namespace MiniBank.Application.Payments.Models;

public sealed record VoidHoldRepositoryRequest(
    string IdempotencyKey,
    Guid MerchantId,
    string RequestRoute,
    byte[] RequestHash,
    Guid HoldId,
    byte VoidStatus,
    string Actor);