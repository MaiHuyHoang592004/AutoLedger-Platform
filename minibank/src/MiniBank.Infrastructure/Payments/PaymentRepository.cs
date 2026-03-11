using System.Data;
using Dapper;
using Microsoft.Data.SqlClient;
using MiniBank.Application.Abstractions;
using MiniBank.Application.Exceptions;
using MiniBank.Application.Payments.Models;
using MiniBank.Infrastructure.Data;

namespace MiniBank.Infrastructure.Payments;

public sealed class PaymentRepository : IPaymentRepository
{
    private const string BeginIdempotencyProcedureName = "dbo.sp_idem_begin";
    private const string CompleteIdempotencySuccessProcedureName = "dbo.sp_idem_complete_success";
    private const string InitializePaymentProcedureName = "dbo.sp_init_payment_with_idem";
    private const string AuthorizeHoldProcedureName = "dbo.sp_authorize_hold";
    private const string CaptureHoldProcedureName = "dbo.sp_capture_hold_partial_with_idem";
    private const string VoidHoldProcedureName = "dbo.sp_void_hold_with_idem";
    private const byte CompletedIdempotencyStatus = 2;
    private const byte InProgressIdempotencyStatus = 1;

    private readonly SqlConnectionFactory _sqlConnectionFactory;

    public PaymentRepository(SqlConnectionFactory sqlConnectionFactory)
    {
        _sqlConnectionFactory = sqlConnectionFactory;
    }

    public async Task<IdempotencyExecutionResult> BeginIdempotencyAsync(
        BeginIdempotencyRepositoryRequest request,
        CancellationToken cancellationToken = default)
    {
        try
        {
            await using var connection = _sqlConnectionFactory.CreateConnection();
            await connection.OpenAsync(cancellationToken);

            var parameters = new DynamicParameters();
            parameters.Add("@idem_key", request.IdempotencyKey, DbType.String, size: 80);
            parameters.Add("@merchant_id", request.MerchantId, DbType.Guid);
            parameters.Add("@request_route", request.RequestRoute, DbType.String, size: 120);
            parameters.Add("@request_hash", request.RequestHash, DbType.Binary, size: 32);
            parameters.Add("@idempotency_ttl_hours", request.IdempotencyTtlHours, DbType.Int32);
            parameters.Add("@in_progress_timeout_seconds", request.InProgressTimeoutSeconds, DbType.Int32);

            return await connection.QuerySingleAsync<IdempotencyExecutionResult>(
                new CommandDefinition(
                    BeginIdempotencyProcedureName,
                    parameters,
                    commandType: CommandType.StoredProcedure,
                    cancellationToken: cancellationToken));
        }
        catch (SqlException ex) when (Contains(ex, "Idempotency key reused with different payload"))
        {
            throw new BadRequestException("The same Idempotency-Key was reused with a different payload.", "IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD");
        }
        catch (SqlException ex) when (Contains(ex, "Request is still in progress") || Contains(ex, "stale in-progress"))
        {
            throw new ConflictException("The authorize hold request is already in progress. Please retry shortly.", "IDEMPOTENCY_REQUEST_IN_PROGRESS");
        }
        catch (SqlException ex)
        {
            throw new InternalServerException($"MiniBank database error while beginning idempotency request: {ex.Message}", "IDEMPOTENCY_BEGIN_FAILED");
        }
    }

    public async Task CompleteIdempotencySuccessAsync(
        CompleteIdempotencyRepositoryRequest request,
        CancellationToken cancellationToken = default)
    {
        try
        {
            await using var connection = _sqlConnectionFactory.CreateConnection();
            await connection.OpenAsync(cancellationToken);

            var parameters = new DynamicParameters();
            parameters.Add("@idem_key", request.IdempotencyKey, DbType.String, size: 80);
            parameters.Add("@merchant_id", request.MerchantId, DbType.Guid);
            parameters.Add("@request_route", request.RequestRoute, DbType.String, size: 120);
            parameters.Add("@response_code", request.ResponseCode, DbType.Int32);
            parameters.Add("@response_body", request.ResponseBody, DbType.String);

            await connection.ExecuteAsync(
                new CommandDefinition(
                    CompleteIdempotencySuccessProcedureName,
                    parameters,
                    commandType: CommandType.StoredProcedure,
                    cancellationToken: cancellationToken));
        }
        catch (SqlException ex)
        {
            throw new InternalServerException($"MiniBank database error while completing idempotency request: {ex.Message}", "IDEMPOTENCY_COMPLETE_FAILED");
        }
    }

    public async Task<InitializePaymentStoredProcedureResult> InitializePaymentAsync(
        InitializePaymentRepositoryRequest request,
        CancellationToken cancellationToken = default)
    {
        try
        {
            await using var connection = _sqlConnectionFactory.CreateConnection();
            await connection.OpenAsync(cancellationToken);

            var parameters = new DynamicParameters();
            parameters.Add("@idem_key", request.IdempotencyKey, DbType.String, size: 80);
            parameters.Add("@merchant_id", request.MerchantId, DbType.Guid);
            parameters.Add("@request_route", request.RequestRoute, DbType.String, size: 120);
            parameters.Add("@request_hash", request.RequestHash, DbType.Binary, size: 32);
            parameters.Add("@payment_id", request.PaymentId, DbType.Guid);
            parameters.Add("@amount_minor", request.AmountMinor, DbType.Int64);
            parameters.Add("@currency", request.Currency, DbType.AnsiStringFixedLength, size: 3);
            parameters.Add("@order_ref", request.OrderRef, DbType.String, size: 100);
            parameters.Add("@actor", request.Actor, DbType.String, size: 80);
            parameters.Add("@correlation_id", request.CorrelationId, DbType.Guid);
            parameters.Add("@request_id", request.RequestId, DbType.Guid);
            parameters.Add("@session_id", request.SessionId, DbType.Guid);
            parameters.Add("@trace_id", request.TraceId, DbType.StringFixedLength, size: 32);

            var command = new CommandDefinition(
                InitializePaymentProcedureName,
                parameters,
                commandType: CommandType.StoredProcedure,
                cancellationToken: cancellationToken);

            var result = await connection.QuerySingleAsync<InitializePaymentStoredProcedureResult>(command);
            return result;
        }
        catch (SqlException ex) when (Contains(ex, "Idempotency key reused with different payload"))
        {
            throw new BadRequestException("The same Idempotency-Key was reused with a different payload.", "IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD");
        }
        catch (SqlException ex) when (Contains(ex, "Request is still in progress") || Contains(ex, "stale in-progress"))
        {
            throw new ConflictException("The payment initialization request is already in progress. Please retry shortly.", "IDEMPOTENCY_REQUEST_IN_PROGRESS");
        }
        catch (SqlException ex) when (Contains(ex, "Invalid payment amount"))
        {
            throw new BadRequestException("Payment amount must be greater than 0.", "INVALID_PAYMENT_AMOUNT");
        }
        catch (SqlException ex) when (Contains(ex, "UX_payments_merchant_orderref") || Contains(ex, "Cannot insert duplicate key row"))
        {
            throw new ConflictException("A MiniBank payment already exists for this booking reference.", "PAYMENT_ALREADY_EXISTS");
        }
        catch (SqlException ex)
        {
            var replayResult = await TryReadCompletedIdempotencyResponseAsync(request, ex, cancellationToken);
            if (replayResult is not null)
            {
                return replayResult;
            }

            throw new InternalServerException($"MiniBank database error while initializing payment: {ex.Message}", "PAYMENT_INITIALIZATION_FAILED");
        }
    }

    public async Task<PaymentSummary?> GetPaymentSummaryAsync(
        Guid paymentId,
        CancellationToken cancellationToken = default)
    {
        const string sql = """
            SELECT
                payment_id AS PaymentId,
                merchant_id AS MerchantId,
                order_ref AS OrderRef,
                amount_minor AS AmountMinor,
                currency AS Currency,
                status AS Status
            FROM dbo.payments
            WHERE payment_id = @PaymentId;
            """;

        await using var connection = _sqlConnectionFactory.CreateConnection();
        await connection.OpenAsync(cancellationToken);

        return await connection.QuerySingleOrDefaultAsync<PaymentSummary>(
            new CommandDefinition(
                sql,
                new { PaymentId = paymentId },
                cancellationToken: cancellationToken));
    }

    public async Task<PaymentWithHoldSummary?> GetPaymentWithLatestHoldAsync(
        Guid paymentId,
        CancellationToken cancellationToken = default)
    {
        const string sql = """
            SELECT
                p.payment_id AS PaymentId,
                p.order_ref AS OrderRef,
                p.amount_minor AS AmountMinor,
                p.currency AS Currency,
                p.status AS PaymentStatus,
                h.hold_id AS HoldId,
                h.status AS HoldStatus,
                h.remaining_amount_minor AS RemainingAmountMinor,
                h.expires_at AS ExpiresAtUtc
            FROM dbo.payments p
            OUTER APPLY (
                SELECT TOP (1)
                    hold_id,
                    status,
                    remaining_amount_minor,
                    expires_at
                FROM dbo.holds
                WHERE payment_id = p.payment_id
                ORDER BY created_at DESC, hold_id DESC
            ) h
            WHERE p.payment_id = @PaymentId;
            """;

        await using var connection = _sqlConnectionFactory.CreateConnection();
        await connection.OpenAsync(cancellationToken);

        return await connection.QuerySingleOrDefaultAsync<PaymentWithHoldSummary>(
            new CommandDefinition(sql, new { PaymentId = paymentId }, cancellationToken: cancellationToken));
    }

    public async Task<int?> GetAccountIdByCodeAsync(
        string accountCode,
        CancellationToken cancellationToken = default)
    {
        const string sql = """
            SELECT TOP (1) account_id
            FROM dbo.accounts
            WHERE account_code = @AccountCode;
            """;

        await using var connection = _sqlConnectionFactory.CreateConnection();
        await connection.OpenAsync(cancellationToken);

        return await connection.QuerySingleOrDefaultAsync<int?>(
            new CommandDefinition(
                sql,
                new { AccountCode = accountCode },
                cancellationToken: cancellationToken));
    }

    public async Task<HoldSummary> AuthorizeHoldAsync(
        AuthorizeHoldRepositoryRequest request,
        CancellationToken cancellationToken = default)
    {
        try
        {
            await using var connection = _sqlConnectionFactory.CreateConnection();
            await connection.OpenAsync(cancellationToken);

            var parameters = new DynamicParameters();
            parameters.Add("@hold_id", request.HoldId, DbType.Guid);
            parameters.Add("@payment_id", request.PaymentId, DbType.Guid);
            parameters.Add("@merchant_id", request.MerchantId, DbType.Guid);
            parameters.Add("@account_id", request.AccountId, DbType.Int32);
            parameters.Add("@amount_minor", request.AmountMinor, DbType.Int64);
            parameters.Add("@currency", request.Currency, DbType.AnsiStringFixedLength, size: 3);
            parameters.Add("@expires_at", request.ExpiresAtUtc, DbType.DateTime2);
            parameters.Add("@actor", request.Actor, DbType.String, size: 80);
            parameters.Add("@correlation_id", request.CorrelationId, DbType.Guid);
            parameters.Add("@enforce_non_negative", true, DbType.Boolean);

            await connection.ExecuteAsync(
                new CommandDefinition(
                    AuthorizeHoldProcedureName,
                    parameters,
                    commandType: CommandType.StoredProcedure,
                    cancellationToken: cancellationToken));

            const string sql = """
                SELECT
                    hold_id AS HoldId,
                    payment_id AS PaymentId,
                    account_id AS AccountId,
                    original_amount_minor AS OriginalAmountMinor,
                    remaining_amount_minor AS RemainingAmountMinor,
                    currency AS Currency,
                    status AS Status,
                    expires_at AS ExpiresAtUtc
                FROM dbo.holds
                WHERE hold_id = @HoldId;
                """;

            var hold = await connection.QuerySingleOrDefaultAsync<HoldSummary>(
                new CommandDefinition(
                    sql,
                    new { request.HoldId },
                    cancellationToken: cancellationToken));

            if (hold is null)
            {
                throw new InternalServerException("Hold was not found after authorization completed.", "HOLD_NOT_FOUND_AFTER_AUTHORIZATION");
            }

            return hold;
        }
        catch (SqlException ex) when (Contains(ex, "Insufficient funds for authorization") || Contains(ex, "Insufficient funds"))
        {
            throw new BadRequestException("Insufficient available balance to authorize hold.", "INSUFFICIENT_FUNDS");
        }
        catch (SqlException ex) when (Contains(ex, "Hold not found"))
        {
            throw new BadRequestException("Hold could not be created because the target payment is invalid.", "PAYMENT_NOT_FOUND");
        }
        catch (SqlException ex) when (Contains(ex, "UX_holds_payment_active") || Contains(ex, "Cannot insert duplicate key row"))
        {
            throw new ConflictException("An active hold already exists for this payment.", "HOLD_ALREADY_EXISTS");
        }
        catch (SqlException ex)
        {
            throw new InternalServerException($"MiniBank database error while authorizing hold: {ex.Message}", "AUTHORIZE_HOLD_FAILED");
        }
    }

    public async Task<HoldSummary?> GetHoldSummaryAsync(
        Guid holdId,
        CancellationToken cancellationToken = default)
    {
        const string sql = """
            SELECT
                hold_id AS HoldId,
                payment_id AS PaymentId,
                account_id AS AccountId,
                original_amount_minor AS OriginalAmountMinor,
                remaining_amount_minor AS RemainingAmountMinor,
                currency AS Currency,
                status AS Status,
                expires_at AS ExpiresAtUtc
            FROM dbo.holds
            WHERE hold_id = @HoldId;
            """;

        await using var connection = _sqlConnectionFactory.CreateConnection();
        await connection.OpenAsync(cancellationToken);

        return await connection.QuerySingleOrDefaultAsync<HoldSummary>(
            new CommandDefinition(sql, new { HoldId = holdId }, cancellationToken: cancellationToken));
    }

    public async Task<CaptureHoldStoredProcedureResult> CaptureHoldAsync(
        CaptureHoldRepositoryRequest request,
        CancellationToken cancellationToken = default)
    {
        try
        {
            await using var connection = _sqlConnectionFactory.CreateConnection();
            await connection.OpenAsync(cancellationToken);

            var postings = new DataTable();
            postings.Columns.Add("account_id", typeof(int));
            postings.Columns.Add("direction", typeof(string));
            postings.Columns.Add("amount_minor", typeof(long));
            postings.Rows.Add(request.CustomerLiabilityAccountId, "D", request.CaptureAmountMinor);
            postings.Rows.Add(request.MerchantLiabilityAccountId, "C", request.CaptureAmountMinor);

            var parameters = new DynamicParameters();
            parameters.Add("@idem_key", request.IdempotencyKey, DbType.String, size: 80);
            parameters.Add("@merchant_id", request.MerchantId, DbType.Guid);
            parameters.Add("@request_route", request.RequestRoute, DbType.String, size: 120);
            parameters.Add("@request_hash", request.RequestHash, DbType.Binary, size: 32);
            parameters.Add("@hold_id", request.HoldId, DbType.Guid);
            parameters.Add("@capture_amount_minor", request.CaptureAmountMinor, DbType.Int64);
            parameters.Add("@journal_id", request.JournalId, DbType.Guid);
            parameters.Add("@journal_type", request.JournalType, DbType.String, size: 40);
            parameters.Add("@reference_id", request.ReferenceId, DbType.Guid);
            parameters.Add("@currency", request.Currency, DbType.AnsiStringFixedLength, size: 3);
            parameters.Add("@postings", postings.AsTableValuedParameter("dbo.PostingTvp"));
            parameters.Add("@actor", request.Actor, DbType.String, size: 80);
            parameters.Add("@correlation_id", request.CorrelationId, DbType.Guid);
            parameters.Add("@request_id", request.RequestId, DbType.Guid);
            parameters.Add("@session_id", request.SessionId, DbType.Guid);
            parameters.Add("@trace_id", request.TraceId, DbType.StringFixedLength, size: 32);
            parameters.Add("@idempotency_ttl_hours", request.IdempotencyTtlHours, DbType.Int32);
            parameters.Add("@in_progress_timeout_seconds", request.InProgressTimeoutSeconds, DbType.Int32);

            return await connection.QuerySingleAsync<CaptureHoldStoredProcedureResult>(
                new CommandDefinition(
                    CaptureHoldProcedureName,
                    parameters,
                    commandType: CommandType.StoredProcedure,
                    cancellationToken: cancellationToken));
        }
        catch (SqlException ex) when (Contains(ex, "Idempotency key reused with different payload"))
        {
            throw new BadRequestException("The same Idempotency-Key was reused with a different payload.", "IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD");
        }
        catch (SqlException ex) when (Contains(ex, "Request is still in progress") || Contains(ex, "stale in-progress"))
        {
            throw new ConflictException("The capture hold request is already in progress. Please retry shortly.", "IDEMPOTENCY_REQUEST_IN_PROGRESS");
        }
        catch (SqlException ex) when (Contains(ex, "Hold not found"))
        {
            throw new BadRequestException("Hold not found.", "HOLD_NOT_FOUND");
        }
        catch (SqlException ex) when (Contains(ex, "Invalid capture amount") || Contains(ex, "exceeds remaining hold"))
        {
            throw new ConflictException("Hold cannot be captured with the requested amount.", "HOLD_NOT_CAPTURABLE");
        }
        catch (SqlException ex)
        {
            var replayResult = await TryReadCompletedIdempotencyResponseAsync(
                request.MerchantId,
                request.RequestRoute,
                request.IdempotencyKey,
                ex,
                cancellationToken);

            if (replayResult is not null)
            {
                return new CaptureHoldStoredProcedureResult
                {
                    Result = "ALREADY_COMPLETED",
                    ResponseCode = replayResult.ResponseCode ?? 200,
                    ResponseBody = replayResult.ResponseBody ?? string.Empty,
                };
            }

            throw new InternalServerException($"MiniBank database error while capturing hold: {ex.Message}", "CAPTURE_HOLD_FAILED");
        }
    }

    public async Task<VoidHoldStoredProcedureResult> VoidHoldAsync(
        VoidHoldRepositoryRequest request,
        CancellationToken cancellationToken = default)
    {
        try
        {
            await using var connection = _sqlConnectionFactory.CreateConnection();
            await connection.OpenAsync(cancellationToken);

            var parameters = new DynamicParameters();
            parameters.Add("@idem_key", request.IdempotencyKey, DbType.String, size: 80);
            parameters.Add("@merchant_id", request.MerchantId, DbType.Guid);
            parameters.Add("@request_route", request.RequestRoute, DbType.String, size: 120);
            parameters.Add("@request_hash", request.RequestHash, DbType.Binary, size: 32);
            parameters.Add("@hold_id", request.HoldId, DbType.Guid);
            parameters.Add("@void_status", request.VoidStatus, DbType.Byte);
            parameters.Add("@actor", request.Actor, DbType.String, size: 80);

            return await connection.QuerySingleAsync<VoidHoldStoredProcedureResult>(
                new CommandDefinition(
                    VoidHoldProcedureName,
                    parameters,
                    commandType: CommandType.StoredProcedure,
                    cancellationToken: cancellationToken));
        }
        catch (SqlException ex) when (Contains(ex, "Idempotency key reused with different payload"))
        {
            throw new BadRequestException("The same Idempotency-Key was reused with a different payload.", "IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD");
        }
        catch (SqlException ex) when (Contains(ex, "Request is still in progress") || Contains(ex, "stale in-progress"))
        {
            throw new ConflictException("The void hold request is already in progress. Please retry shortly.", "IDEMPOTENCY_REQUEST_IN_PROGRESS");
        }
        catch (SqlException ex) when (Contains(ex, "Hold not found"))
        {
            throw new BadRequestException("Hold not found.", "HOLD_NOT_FOUND");
        }
        catch (SqlException ex) when (Contains(ex, "already terminal") || Contains(ex, "not in AUTHORIZED status"))
        {
            throw new ConflictException("Hold cannot be voided in its current state.", "HOLD_NOT_VOIDABLE");
        }
        catch (SqlException ex)
        {
            var replayResult = await TryReadCompletedIdempotencyResponseAsync(
                request.MerchantId,
                request.RequestRoute,
                request.IdempotencyKey,
                ex,
                cancellationToken);

            if (replayResult is not null)
            {
                return new VoidHoldStoredProcedureResult
                {
                    Result = "ALREADY_COMPLETED",
                    ResponseCode = replayResult.ResponseCode ?? 200,
                    ResponseBody = replayResult.ResponseBody ?? string.Empty,
                };
            }

            throw new InternalServerException($"MiniBank database error while voiding hold: {ex.Message}", "VOID_HOLD_FAILED");
        }
    }

    private static bool Contains(SqlException exception, string value) =>
        exception.Message.Contains(value, StringComparison.OrdinalIgnoreCase);

    private async Task<InitializePaymentStoredProcedureResult?> TryReadCompletedIdempotencyResponseAsync(
        InitializePaymentRepositoryRequest request,
        SqlException exception,
        CancellationToken cancellationToken)
    {
        var record = await TryReadCompletedIdempotencyResponseAsync(
            request.MerchantId,
            request.RequestRoute,
            request.IdempotencyKey,
            exception,
            cancellationToken);

        if (record is null)
        {
            return null;
        }

        if (record.Status == InProgressIdempotencyStatus)
        {
            throw new ConflictException("The payment initialization request is already in progress. Please retry shortly.", "IDEMPOTENCY_REQUEST_IN_PROGRESS");
        }

        if (record.Status == CompletedIdempotencyStatus && !string.IsNullOrWhiteSpace(record.ResponseBody))
        {
            return new InitializePaymentStoredProcedureResult
            {
                Result = "ALREADY_COMPLETED",
                ResponseCode = record.ResponseCode,
                ResponseBody = record.ResponseBody,
            };
        }

        return null;
    }

    private async Task<IdempotencyReplayRecord?> TryReadCompletedIdempotencyResponseAsync(
        Guid merchantId,
        string requestRoute,
        string idempotencyKey,
        SqlException exception,
        CancellationToken cancellationToken)
    {
        if (!Contains(exception, "The current transaction cannot be committed and cannot support operations that write to the log file"))
        {
            return null;
        }

        const string sql = """
            SELECT TOP (1)
                status AS Status,
                response_code AS ResponseCode,
                response_body AS ResponseBody
            FROM dbo.idempotency_keys
            WHERE merchant_id = @MerchantId
              AND request_route = @RequestRoute
              AND idem_key = @IdempotencyKey
            ORDER BY idem_id DESC;
            """;

        await using var connection = _sqlConnectionFactory.CreateConnection();
        await connection.OpenAsync(cancellationToken);

        return await connection.QuerySingleOrDefaultAsync<IdempotencyReplayRecord>(
            new CommandDefinition(
                sql,
                new
                {
                    MerchantId = merchantId,
                    RequestRoute = requestRoute,
                    IdempotencyKey = idempotencyKey,
                },
                cancellationToken: cancellationToken));
    }

    private sealed class IdempotencyReplayRecord
    {
        public byte Status { get; init; }

        public int? ResponseCode { get; init; }

        public string? ResponseBody { get; init; }
    }
}