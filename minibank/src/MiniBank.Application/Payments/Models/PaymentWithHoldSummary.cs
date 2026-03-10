namespace MiniBank.Application.Payments.Models;

public sealed class PaymentWithHoldSummary
{
    public Guid PaymentId { get; init; }

    public string OrderRef { get; init; } = string.Empty;

    public long AmountMinor { get; init; }

    public string Currency { get; init; } = string.Empty;

    public byte PaymentStatus { get; init; }

    public Guid? HoldId { get; init; }

    public byte? HoldStatus { get; init; }

    public long? RemainingAmountMinor { get; init; }

    public DateTime? ExpiresAtUtc { get; init; }
}