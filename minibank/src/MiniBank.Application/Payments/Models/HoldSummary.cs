namespace MiniBank.Application.Payments.Models;

public sealed class HoldSummary
{
    public Guid HoldId { get; init; }

    public Guid PaymentId { get; init; }

    public int AccountId { get; init; }

    public long OriginalAmountMinor { get; init; }

    public long RemainingAmountMinor { get; init; }

    public string Currency { get; init; } = string.Empty;

    public byte Status { get; init; }

    public DateTime ExpiresAtUtc { get; init; }
}