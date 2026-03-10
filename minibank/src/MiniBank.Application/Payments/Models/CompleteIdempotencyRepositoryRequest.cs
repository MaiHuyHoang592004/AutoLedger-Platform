namespace MiniBank.Application.Payments.Models;

public sealed record CompleteIdempotencyRepositoryRequest(
    string IdempotencyKey,
    Guid MerchantId,
    string RequestRoute,
    int ResponseCode,
    string ResponseBody);