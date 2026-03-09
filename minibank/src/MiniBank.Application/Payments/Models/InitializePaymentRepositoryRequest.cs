namespace MiniBank.Application.Payments.Models;

public sealed record InitializePaymentRepositoryRequest(
    string IdempotencyKey,
    Guid MerchantId,
    string RequestRoute,
    byte[] RequestHash,
    Guid PaymentId,
    long AmountMinor,
    string Currency,
    string OrderRef,
    string Actor,
    Guid? CorrelationId,
    Guid? RequestId,
    Guid? SessionId,
    string? TraceId);