namespace MiniBank.Application.Payments.Models;

public sealed record IdempotencyExecutionResult(
    string Result,
    int? ResponseCode,
    string? ResponseBody);