using MiniBank.Contracts.Payments;

namespace MiniBank.Application.Payments.Models;

public sealed record VoidHoldOperationResult(VoidHoldResponse Response, bool IsReplay);