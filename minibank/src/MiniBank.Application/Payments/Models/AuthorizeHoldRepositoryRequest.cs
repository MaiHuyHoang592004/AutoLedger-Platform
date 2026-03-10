namespace MiniBank.Application.Payments.Models;

public sealed record AuthorizeHoldRepositoryRequest(
    Guid HoldId,
    Guid PaymentId,
    Guid MerchantId,
    int AccountId,
    long AmountMinor,
    string Currency,
    DateTime ExpiresAtUtc,
    string Actor,
    Guid? CorrelationId,
    byte[] RequestHash);