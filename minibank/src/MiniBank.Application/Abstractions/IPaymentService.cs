using MiniBank.Application.Payments.Models;
using MiniBank.Contracts.Payments;

namespace MiniBank.Application.Abstractions;

public interface IPaymentService
{
    Task<CreatePaymentOperationResult> CreatePaymentAsync(
        CreatePaymentRequest request,
        string? idempotencyKey,
        CancellationToken cancellationToken = default);
}