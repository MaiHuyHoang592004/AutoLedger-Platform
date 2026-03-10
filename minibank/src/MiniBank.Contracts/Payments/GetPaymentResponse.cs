namespace MiniBank.Contracts.Payments;

public sealed class GetPaymentResponse
{
    public Guid PaymentId { get; init; }

    public string PaymentStatus { get; init; } = string.Empty;

    public string OrderRef { get; init; } = string.Empty;

    public long AmountMinor { get; init; }

    public string Currency { get; init; } = string.Empty;

    public Guid? HoldId { get; init; }

    public string? HoldStatus { get; init; }

    public long? RemainingAmountMinor { get; init; }

    public DateTime? ExpiresAtUtc { get; init; }
}