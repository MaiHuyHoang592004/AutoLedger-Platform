using MiniBank.Contracts.Payments;

namespace MiniBank.Application.Payments.Models;

public sealed record CreatePaymentOperationResult(
    CreatePaymentResponse Response,
    bool IsReplay);