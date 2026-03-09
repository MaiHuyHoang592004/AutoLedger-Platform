namespace MiniBank.Contracts.Payments;

public sealed class CreatePaymentResponse
{
    public Guid PaymentId { get; init; }

    public string Status { get; init; } = string.Empty;

    public string OrderRef { get; init; } = string.Empty;

    public long AmountMinor { get; init; }

    public string Currency { get; init; } = string.Empty;
}