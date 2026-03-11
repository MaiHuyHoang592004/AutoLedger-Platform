namespace MiniBank.Contracts.Payments;

public sealed class CaptureHoldResponse
{
    public Guid HoldId { get; init; }

    public Guid PaymentId { get; init; }

    public long CapturedAmountMinor { get; init; }

    public long RemainingAmountMinor { get; init; }

    public string Currency { get; init; } = string.Empty;

    public string HoldStatus { get; init; } = string.Empty;

    public string ProviderRef { get; init; } = string.Empty;
}