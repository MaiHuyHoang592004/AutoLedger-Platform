using MiniBank.Application.Payments.Models;

namespace MiniBank.Application.Abstractions;

public interface IPaymentRepository
{
    Task<IdempotencyExecutionResult> BeginIdempotencyAsync(
        BeginIdempotencyRepositoryRequest request,
        CancellationToken cancellationToken = default);

    Task CompleteIdempotencySuccessAsync(
        CompleteIdempotencyRepositoryRequest request,
        CancellationToken cancellationToken = default);

    Task<InitializePaymentStoredProcedureResult> InitializePaymentAsync(
        InitializePaymentRepositoryRequest request,
        CancellationToken cancellationToken = default);

    Task<PaymentSummary?> GetPaymentSummaryAsync(
        Guid paymentId,
        CancellationToken cancellationToken = default);

    Task<PaymentWithHoldSummary?> GetPaymentWithLatestHoldAsync(
        Guid paymentId,
        CancellationToken cancellationToken = default);

    Task<int?> GetAccountIdByCodeAsync(
        string accountCode,
        CancellationToken cancellationToken = default);

    Task<HoldSummary> AuthorizeHoldAsync(
        AuthorizeHoldRepositoryRequest request,
        CancellationToken cancellationToken = default);

    Task<VoidHoldStoredProcedureResult> VoidHoldAsync(
        VoidHoldRepositoryRequest request,
        CancellationToken cancellationToken = default);
}