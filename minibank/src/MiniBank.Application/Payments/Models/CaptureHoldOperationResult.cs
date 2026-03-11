using MiniBank.Contracts.Payments;

namespace MiniBank.Application.Payments.Models;

public sealed record CaptureHoldOperationResult(CaptureHoldResponse Response, bool IsReplay);