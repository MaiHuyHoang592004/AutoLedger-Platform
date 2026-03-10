using MiniBank.Contracts.Payments;

namespace MiniBank.Application.Payments.Models;

public sealed record AuthorizeHoldOperationResult(AuthorizeHoldResponse Response, bool IsReplay);