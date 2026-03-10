namespace MiniBank.Application.Payments.Models;

public sealed record BeginIdempotencyRepositoryRequest(
    string IdempotencyKey,
    Guid MerchantId,
    string RequestRoute,
    byte[] RequestHash,
    int IdempotencyTtlHours = 24,
    int InProgressTimeoutSeconds = 60);