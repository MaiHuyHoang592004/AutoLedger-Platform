using System.Text.Json;
using System.Text.Json.Serialization;
using MiniBank.Application.Abstractions;
using MiniBank.Application.Exceptions;
using MiniBank.Application.Payments.Models;
using MiniBank.Application.Security;
using MiniBank.Contracts.Payments;

namespace MiniBank.Application.Payments;

public sealed class PaymentService : IPaymentService
{
    private static readonly Guid DemoMerchantId = Guid.Parse("7E9B2F5A-3D1C-4E6B-8F9A-0B1C2D3E4F5A");
    private const string Currency = "VND";
    private const string RequestRoute = "/api/payments";
    private const string AuthorizeHoldRouteTemplate = "/api/payments/{0}/authorize-hold";
    private const string CaptureHoldRouteTemplate = "/api/holds/{0}/capture";
    private const string VoidHoldRouteTemplate = "/api/holds/{0}/void";
    private const string Actor = "car-rental-service";
    private const string CustomerLiabilityAccountCode = "CUSTOMER_LIAB";
    private const string MerchantLiabilityAccountCode = "MERCHANT_LIAB";
    private const int HoldDurationMinutes = 15;
    private const byte VoidedHoldStatus = 3;
    private const string CaptureJournalType = "HOLD_CAPTURE";

    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);

    private readonly IPaymentRepository _paymentRepository;

    public PaymentService(IPaymentRepository paymentRepository)
    {
        _paymentRepository = paymentRepository;
    }

    public async Task<CreatePaymentOperationResult> CreatePaymentAsync(
        CreatePaymentRequest request,
        string? idempotencyKey,
        CancellationToken cancellationToken = default)
    {
        var sanitizedBookingId = request.BookingId?.Trim();
        var sanitizedIdempotencyKey = idempotencyKey?.Trim();

        if (string.IsNullOrWhiteSpace(sanitizedIdempotencyKey))
        {
            throw new BadRequestException("Idempotency-Key header is required.");
        }

        if (sanitizedIdempotencyKey.Length > 80)
        {
            throw new BadRequestException("Idempotency-Key must not exceed 80 characters.");
        }

        if (string.IsNullOrWhiteSpace(sanitizedBookingId))
        {
            throw new BadRequestException("bookingId is required.");
        }

        if (sanitizedBookingId.Length > 100)
        {
            throw new BadRequestException("bookingId must not exceed 100 characters.");
        }

        if (request.TotalPrice <= 0)
        {
            throw new BadRequestException("totalPrice must be greater than 0.");
        }

        var repositoryRequest = new InitializePaymentRepositoryRequest(
            IdempotencyKey: sanitizedIdempotencyKey,
            MerchantId: DemoMerchantId,
            RequestRoute: RequestRoute,
            RequestHash: RequestHashCalculator.CalculateCreatePaymentHash(
                sanitizedBookingId,
                request.TotalPrice,
                DemoMerchantId,
                Currency,
                RequestRoute),
            PaymentId: Guid.NewGuid(),
            AmountMinor: request.TotalPrice,
            Currency: Currency,
            OrderRef: sanitizedBookingId,
            Actor: Actor,
            CorrelationId: null,
            RequestId: null,
            SessionId: null,
            TraceId: null);

        var storedProcedureResult = await _paymentRepository.InitializePaymentAsync(repositoryRequest, cancellationToken);

        if (!string.Equals(storedProcedureResult.Result, "SUCCESS", StringComparison.OrdinalIgnoreCase)
            && !string.Equals(storedProcedureResult.Result, "ALREADY_COMPLETED", StringComparison.OrdinalIgnoreCase))
        {
            throw new InternalServerException($"Unexpected result from sp_init_payment_with_idem: {storedProcedureResult.Result}");
        }

        if (string.IsNullOrWhiteSpace(storedProcedureResult.ResponseBody))
        {
            throw new InternalServerException("Stored procedure returned an empty response body.");
        }

        var storedBody = JsonSerializer.Deserialize<StoredProcedurePaymentBody>(storedProcedureResult.ResponseBody, JsonOptions);
        if (storedBody is null || storedBody.PaymentId == Guid.Empty || string.IsNullOrWhiteSpace(storedBody.Status))
        {
            throw new InternalServerException("Unable to parse payment response returned by MiniBank SQL procedure.");
        }

        var response = new CreatePaymentResponse
        {
            PaymentId = storedBody.PaymentId,
            Status = storedBody.Status,
            OrderRef = repositoryRequest.OrderRef,
            AmountMinor = repositoryRequest.AmountMinor,
            Currency = repositoryRequest.Currency,
        };

        return new CreatePaymentOperationResult(
            response,
            string.Equals(storedProcedureResult.Result, "ALREADY_COMPLETED", StringComparison.OrdinalIgnoreCase));
    }

    public async Task<AuthorizeHoldOperationResult> AuthorizeHoldAsync(
        Guid paymentId,
        string? idempotencyKey,
        CancellationToken cancellationToken = default)
    {
        var sanitizedIdempotencyKey = idempotencyKey?.Trim();
        if (string.IsNullOrWhiteSpace(sanitizedIdempotencyKey))
        {
            throw new BadRequestException("Idempotency-Key header is required.", "IDEMPOTENCY_KEY_REQUIRED");
        }

        if (sanitizedIdempotencyKey.Length > 80)
        {
            throw new BadRequestException("Idempotency-Key must not exceed 80 characters.", "IDEMPOTENCY_KEY_TOO_LONG");
        }

        var payment = await _paymentRepository.GetPaymentSummaryAsync(paymentId, cancellationToken);
        if (payment is null)
        {
            throw new BadRequestException("Payment not found.", "PAYMENT_NOT_FOUND");
        }

        if (payment.Status != 1)
        {
            throw new ConflictException("Only payments in created status can be authorized for hold.", "PAYMENT_NOT_AUTHORIZABLE");
        }

        if (!string.Equals(payment.Currency, Currency, StringComparison.OrdinalIgnoreCase))
        {
            throw new BadRequestException("Only VND payments are supported for the MVP Authorize Hold flow.", "UNSUPPORTED_CURRENCY");
        }

        var accountId = await _paymentRepository.GetAccountIdByCodeAsync(CustomerLiabilityAccountCode, cancellationToken);
        if (accountId is null)
        {
            throw new InternalServerException("CUSTOMER_LIAB account was not found in MiniBank DB.", "ACCOUNT_NOT_FOUND");
        }

        var requestRoute = string.Format(AuthorizeHoldRouteTemplate, paymentId);
        var requestHash = RequestHashCalculator.CalculateAuthorizeHoldHash(
            paymentId,
            payment.AmountMinor,
            DemoMerchantId,
            accountId.Value,
            Currency,
            requestRoute);

        var idempotencyResult = await _paymentRepository.BeginIdempotencyAsync(
            new BeginIdempotencyRepositoryRequest(
                sanitizedIdempotencyKey,
                DemoMerchantId,
                requestRoute,
                requestHash),
            cancellationToken);

        if (string.Equals(idempotencyResult.Result, "ALREADY_COMPLETED", StringComparison.OrdinalIgnoreCase))
        {
            if (string.IsNullOrWhiteSpace(idempotencyResult.ResponseBody))
            {
                throw new InternalServerException("Idempotency replay returned an empty response body.", "IDEMPOTENCY_REPLAY_EMPTY");
            }

            var replayResponse = JsonSerializer.Deserialize<AuthorizeHoldResponse>(idempotencyResult.ResponseBody, JsonOptions);
            if (replayResponse is null || replayResponse.HoldId == Guid.Empty)
            {
                throw new InternalServerException("Unable to parse authorize hold replay response.", "IDEMPOTENCY_REPLAY_INVALID");
            }

            return new AuthorizeHoldOperationResult(replayResponse, true);
        }

        var repositoryRequest = new AuthorizeHoldRepositoryRequest(
            HoldId: Guid.NewGuid(),
            PaymentId: paymentId,
            MerchantId: DemoMerchantId,
            AccountId: accountId.Value,
            AmountMinor: payment.AmountMinor,
            Currency: Currency,
            ExpiresAtUtc: DateTime.UtcNow.AddMinutes(HoldDurationMinutes),
            Actor: Actor,
            CorrelationId: null,
            RequestHash: requestHash);

        var hold = await _paymentRepository.AuthorizeHoldAsync(repositoryRequest, cancellationToken);

        var response = new AuthorizeHoldResponse
        {
            HoldId = hold.HoldId,
            PaymentId = hold.PaymentId,
            AccountId = hold.AccountId,
            Status = MapHoldStatus(hold.Status),
            OriginalAmountMinor = hold.OriginalAmountMinor,
            RemainingAmountMinor = hold.RemainingAmountMinor,
            Currency = hold.Currency,
            ExpiresAtUtc = DateTime.SpecifyKind(hold.ExpiresAtUtc, DateTimeKind.Utc),
        };

        await _paymentRepository.CompleteIdempotencySuccessAsync(
            new CompleteIdempotencyRepositoryRequest(
                sanitizedIdempotencyKey,
                DemoMerchantId,
                requestRoute,
                201,
                JsonSerializer.Serialize(response, JsonOptions)),
            cancellationToken);

        return new AuthorizeHoldOperationResult(response, false);
    }

    public async Task<GetPaymentOperationResult> GetPaymentAsync(
        Guid paymentId,
        CancellationToken cancellationToken = default)
    {
        var payment = await _paymentRepository.GetPaymentWithLatestHoldAsync(paymentId, cancellationToken);
        if (payment is null)
        {
            throw new BadRequestException("Payment not found.");
        }

        return new GetPaymentOperationResult(new GetPaymentResponse
        {
            PaymentId = payment.PaymentId,
            PaymentStatus = MapPaymentStatus(payment.PaymentStatus),
            OrderRef = payment.OrderRef,
            AmountMinor = payment.AmountMinor,
            Currency = payment.Currency,
            HoldId = payment.HoldId,
            HoldStatus = payment.HoldStatus.HasValue ? MapHoldStatus(payment.HoldStatus.Value) : null,
            RemainingAmountMinor = payment.RemainingAmountMinor,
            ExpiresAtUtc = payment.ExpiresAtUtc.HasValue
                ? DateTime.SpecifyKind(payment.ExpiresAtUtc.Value, DateTimeKind.Utc)
                : null,
        });
    }

    public async Task<CaptureHoldOperationResult> CaptureHoldAsync(
        Guid holdId,
        string? idempotencyKey,
        CancellationToken cancellationToken = default)
    {
        var sanitizedIdempotencyKey = idempotencyKey?.Trim();
        if (string.IsNullOrWhiteSpace(sanitizedIdempotencyKey))
        {
            throw new BadRequestException("Idempotency-Key header is required.", "IDEMPOTENCY_KEY_REQUIRED");
        }

        if (sanitizedIdempotencyKey.Length > 80)
        {
            throw new BadRequestException("Idempotency-Key must not exceed 80 characters.", "IDEMPOTENCY_KEY_TOO_LONG");
        }

        var hold = await _paymentRepository.GetHoldSummaryAsync(holdId, cancellationToken);
        if (hold is null)
        {
            throw new BadRequestException("Hold not found.", "HOLD_NOT_FOUND");
        }

        if (!string.Equals(hold.Currency, Currency, StringComparison.OrdinalIgnoreCase))
        {
            throw new BadRequestException("Only VND holds are supported for the MVP capture flow.", "UNSUPPORTED_CURRENCY");
        }

        if (hold.Status == 2 && hold.RemainingAmountMinor == 0)
        {
            return new CaptureHoldOperationResult(
                new CaptureHoldResponse
                {
                    HoldId = hold.HoldId,
                    PaymentId = hold.PaymentId,
                    CapturedAmountMinor = hold.OriginalAmountMinor,
                    RemainingAmountMinor = hold.RemainingAmountMinor,
                    Currency = hold.Currency,
                    HoldStatus = MapHoldStatus(hold.Status),
                    ProviderRef = $"{hold.PaymentId}:{hold.HoldId}",
                },
                true);
        }

        if (hold.Status != 1)
        {
            throw new ConflictException("Only holds in authorized status can be captured.", "HOLD_NOT_CAPTURABLE");
        }

        if (hold.RemainingAmountMinor <= 0)
        {
            throw new ConflictException("Hold has no remaining amount to capture.", "HOLD_NOT_CAPTURABLE");
        }

        var customerLiabilityAccountId = await _paymentRepository.GetAccountIdByCodeAsync(CustomerLiabilityAccountCode, cancellationToken);
        if (customerLiabilityAccountId is null)
        {
            throw new InternalServerException("CUSTOMER_LIAB account was not found in MiniBank DB.", "ACCOUNT_NOT_FOUND");
        }

        var merchantLiabilityAccountId = await _paymentRepository.GetAccountIdByCodeAsync(MerchantLiabilityAccountCode, cancellationToken);
        if (merchantLiabilityAccountId is null)
        {
            throw new InternalServerException("MERCHANT_LIAB account was not found in MiniBank DB.", "ACCOUNT_NOT_FOUND");
        }

        var requestRoute = string.Format(CaptureHoldRouteTemplate, holdId);
        var referenceId = hold.PaymentId;
        var journalId = Guid.NewGuid();
        var requestHash = RequestHashCalculator.CalculateCaptureHoldHash(
            holdId,
            hold.PaymentId,
            hold.RemainingAmountMinor,
            DemoMerchantId,
            customerLiabilityAccountId.Value,
            merchantLiabilityAccountId.Value,
            Currency,
            CaptureJournalType,
            referenceId,
            requestRoute);

        var result = await _paymentRepository.CaptureHoldAsync(
            new CaptureHoldRepositoryRequest(
                sanitizedIdempotencyKey,
                DemoMerchantId,
                requestRoute,
                requestHash,
                holdId,
                hold.RemainingAmountMinor,
                journalId,
                CaptureJournalType,
                referenceId,
                Currency,
                Actor,
                customerLiabilityAccountId.Value,
                merchantLiabilityAccountId.Value),
            cancellationToken);

        if (!string.Equals(result.Result, "SUCCESS", StringComparison.OrdinalIgnoreCase)
            && !string.Equals(result.Result, "ALREADY_COMPLETED", StringComparison.OrdinalIgnoreCase))
        {
            throw new InternalServerException($"Unexpected result from sp_capture_hold_partial_with_idem: {result.Result}");
        }

        if (string.IsNullOrWhiteSpace(result.ResponseBody))
        {
            throw new InternalServerException("Stored procedure returned an empty response body.");
        }

        var storedBody = JsonSerializer.Deserialize<StoredProcedureCaptureHoldBody>(result.ResponseBody, JsonOptions);
        if (storedBody is null || storedBody.HoldId == Guid.Empty)
        {
            throw new InternalServerException("Unable to parse capture hold response returned by MiniBank SQL procedure.");
        }

        var updatedHold = await _paymentRepository.GetHoldSummaryAsync(holdId, cancellationToken);
        if (updatedHold is null)
        {
            throw new InternalServerException("Hold was not found after capture completed.", "HOLD_NOT_FOUND_AFTER_CAPTURE");
        }

        var isReplay = string.Equals(result.Result, "ALREADY_COMPLETED", StringComparison.OrdinalIgnoreCase);

        return new CaptureHoldOperationResult(
            new CaptureHoldResponse
            {
                HoldId = updatedHold.HoldId,
                PaymentId = updatedHold.PaymentId,
                CapturedAmountMinor = storedBody.CaptureAmountMinor,
                RemainingAmountMinor = updatedHold.RemainingAmountMinor,
                Currency = updatedHold.Currency,
                HoldStatus = MapHoldStatus(updatedHold.Status),
                ProviderRef = isReplay ? $"{updatedHold.PaymentId}:{updatedHold.HoldId}" : journalId.ToString(),
            },
            isReplay);
    }

    public async Task<VoidHoldOperationResult> VoidHoldAsync(
        Guid holdId,
        string? idempotencyKey,
        CancellationToken cancellationToken = default)
    {
        var sanitizedIdempotencyKey = idempotencyKey?.Trim();
        if (string.IsNullOrWhiteSpace(sanitizedIdempotencyKey))
        {
            throw new BadRequestException("Idempotency-Key header is required.");
        }

        if (sanitizedIdempotencyKey.Length > 80)
        {
            throw new BadRequestException("Idempotency-Key must not exceed 80 characters.");
        }

        var requestRoute = string.Format(VoidHoldRouteTemplate, holdId);
        var request = new VoidHoldRepositoryRequest(
            sanitizedIdempotencyKey,
            DemoMerchantId,
            requestRoute,
            RequestHashCalculator.CalculateVoidHoldHash(holdId, DemoMerchantId, VoidedHoldStatus, requestRoute),
            holdId,
            VoidedHoldStatus,
            Actor);

        var result = await _paymentRepository.VoidHoldAsync(request, cancellationToken);
        if (!string.Equals(result.Result, "SUCCESS", StringComparison.OrdinalIgnoreCase)
            && !string.Equals(result.Result, "ALREADY_COMPLETED", StringComparison.OrdinalIgnoreCase))
        {
            throw new InternalServerException($"Unexpected result from sp_void_hold_with_idem: {result.Result}");
        }

        if (string.IsNullOrWhiteSpace(result.ResponseBody))
        {
            throw new InternalServerException("Stored procedure returned an empty response body.");
        }

        var storedBody = JsonSerializer.Deserialize<StoredProcedureVoidHoldBody>(result.ResponseBody, JsonOptions);
        if (storedBody is null || storedBody.HoldId == Guid.Empty)
        {
            throw new InternalServerException("Unable to parse void hold response returned by MiniBank SQL procedure.");
        }

        return new VoidHoldOperationResult(
            new VoidHoldResponse
            {
                HoldId = storedBody.HoldId,
                VoidStatus = storedBody.VoidStatus,
                HoldStatus = MapHoldStatus((byte)storedBody.VoidStatus),
            },
            string.Equals(result.Result, "ALREADY_COMPLETED", StringComparison.OrdinalIgnoreCase));
    }

    private static string MapHoldStatus(byte status) => status switch
    {
        1 => "AUTHORIZED",
        2 => "CAPTURED",
        3 => "VOIDED",
        4 => "EXPIRED",
        _ => "UNKNOWN",
    };

    private static string MapPaymentStatus(byte status) => status switch
    {
        1 => "CREATED",
        2 => "AUTHORIZED",
        3 => "FAILED",
        4 => "VOIDED",
        5 => "REFUNDED",
        _ => "UNKNOWN",
    };

    private sealed class StoredProcedurePaymentBody
    {
        [JsonPropertyName("payment_id")]
        public Guid PaymentId { get; init; }

        [JsonPropertyName("status")]
        public string Status { get; init; } = string.Empty;
    }

    private sealed class StoredProcedureVoidHoldBody
    {
        [JsonPropertyName("hold_id")]
        public Guid HoldId { get; init; }

        [JsonPropertyName("void_status")]
        public int VoidStatus { get; init; }
    }

    private sealed class StoredProcedureCaptureHoldBody
    {
        [JsonPropertyName("hold_id")]
        public Guid HoldId { get; init; }

        [JsonPropertyName("capture_amount_minor")]
        public long CaptureAmountMinor { get; init; }
    }
}