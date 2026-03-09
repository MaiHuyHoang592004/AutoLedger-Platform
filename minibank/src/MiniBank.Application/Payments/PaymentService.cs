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
    private const string Actor = "car-rental-service";

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

    private sealed class StoredProcedurePaymentBody
    {
        [JsonPropertyName("payment_id")]
        public Guid PaymentId { get; init; }

        [JsonPropertyName("status")]
        public string Status { get; init; } = string.Empty;
    }
}