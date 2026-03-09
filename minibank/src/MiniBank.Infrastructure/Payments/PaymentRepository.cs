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
    private const string InitializePaymentProcedureName = "dbo.sp_init_payment_with_idem";
    private const byte CompletedIdempotencyStatus = 2;
    private const byte InProgressIdempotencyStatus = 1;

    private readonly SqlConnectionFactory _sqlConnectionFactory;

    public PaymentRepository(SqlConnectionFactory sqlConnectionFactory)
    {
        _sqlConnectionFactory = sqlConnectionFactory;
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
            throw new BadRequestException("The same Idempotency-Key was reused with a different payload.");
        }
        catch (SqlException ex) when (Contains(ex, "Request is still in progress") || Contains(ex, "stale in-progress"))
        {
            throw new ConflictException("The payment initialization request is already in progress. Please retry shortly.");
        }
        catch (SqlException ex) when (Contains(ex, "Invalid payment amount"))
        {
            throw new BadRequestException("Payment amount must be greater than 0.");
        }
        catch (SqlException ex) when (Contains(ex, "UX_payments_merchant_orderref") || Contains(ex, "Cannot insert duplicate key row"))
        {
            throw new ConflictException("A MiniBank payment already exists for this booking reference.");
        }
        catch (SqlException ex)
        {
            var replayResult = await TryReadCompletedIdempotencyResponseAsync(request, ex, cancellationToken);
            if (replayResult is not null)
            {
                return replayResult;
            }

            throw new InternalServerException($"MiniBank database error while initializing payment: {ex.Message}");
        }
    }

    private static bool Contains(SqlException exception, string value) =>
        exception.Message.Contains(value, StringComparison.OrdinalIgnoreCase);

    private async Task<InitializePaymentStoredProcedureResult?> TryReadCompletedIdempotencyResponseAsync(
        InitializePaymentRepositoryRequest request,
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

        var record = await connection.QuerySingleOrDefaultAsync<IdempotencyReplayRecord>(
            new CommandDefinition(
                sql,
                new
                {
                    request.MerchantId,
                    request.RequestRoute,
                    IdempotencyKey = request.IdempotencyKey,
                },
                cancellationToken: cancellationToken));

        if (record is null)
        {
            return null;
        }

        if (record.Status == InProgressIdempotencyStatus)
        {
            throw new ConflictException("The payment initialization request is already in progress. Please retry shortly.");
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

    private sealed class IdempotencyReplayRecord
    {
        public byte Status { get; init; }

        public int? ResponseCode { get; init; }

        public string? ResponseBody { get; init; }
    }
}