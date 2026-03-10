namespace MiniBank.Contracts.Payments;

public sealed class AuthorizeHoldResponse
{
    public Guid HoldId { get; init; }

    public Guid PaymentId { get; init; }

    public int AccountId { get; init; }

    public string Status { get; init; } = string.Empty;

    public long OriginalAmountMinor { get; init; }

    public long RemainingAmountMinor { get; init; }

    public string Currency { get; init; } = string.Empty;

    public DateTime ExpiresAtUtc { get; init; }
}