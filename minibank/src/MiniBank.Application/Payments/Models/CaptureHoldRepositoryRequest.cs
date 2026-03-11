namespace MiniBank.Application.Payments.Models;

public sealed record CaptureHoldRepositoryRequest(
    string IdempotencyKey,
    Guid MerchantId,
    string RequestRoute,
    byte[] RequestHash,
    Guid HoldId,
    long CaptureAmountMinor,
    Guid JournalId,
    string JournalType,
    Guid ReferenceId,
    string Currency,
    string Actor,
    int CustomerLiabilityAccountId,
    int MerchantLiabilityAccountId,
    Guid? CorrelationId = null,
    Guid? RequestId = null,
    Guid? SessionId = null,
    string? TraceId = null,
    int IdempotencyTtlHours = 24,
    int InProgressTimeoutSeconds = 60);