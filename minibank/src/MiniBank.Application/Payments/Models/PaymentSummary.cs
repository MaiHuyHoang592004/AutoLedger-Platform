namespace MiniBank.Application.Payments.Models;

public sealed class PaymentSummary
{
    public Guid PaymentId { get; init; }

    public Guid MerchantId { get; init; }

    public string OrderRef { get; init; } = string.Empty;

    public long AmountMinor { get; init; }

    public string Currency { get; init; } = string.Empty;

    public byte Status { get; init; }
}