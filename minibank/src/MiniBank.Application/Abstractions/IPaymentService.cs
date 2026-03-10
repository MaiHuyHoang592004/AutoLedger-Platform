using MiniBank.Application.Payments.Models;
using MiniBank.Contracts.Payments;

namespace MiniBank.Application.Abstractions;

public interface IPaymentService
{
    Task<CreatePaymentOperationResult> CreatePaymentAsync(
        CreatePaymentRequest request,
        string? idempotencyKey,
        CancellationToken cancellationToken = default);

    Task<AuthorizeHoldOperationResult> AuthorizeHoldAsync(
        Guid paymentId,
        string? idempotencyKey,
        CancellationToken cancellationToken = default);

    Task<GetPaymentOperationResult> GetPaymentAsync(
        Guid paymentId,
        CancellationToken cancellationToken = default);

    Task<VoidHoldOperationResult> VoidHoldAsync(
        Guid holdId,
        string? idempotencyKey,
        CancellationToken cancellationToken = default);
}