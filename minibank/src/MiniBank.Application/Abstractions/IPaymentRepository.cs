using MiniBank.Application.Payments.Models;

namespace MiniBank.Application.Abstractions;

public interface IPaymentRepository
{
    Task<InitializePaymentStoredProcedureResult> InitializePaymentAsync(
        InitializePaymentRepositoryRequest request,
        CancellationToken cancellationToken = default);
}