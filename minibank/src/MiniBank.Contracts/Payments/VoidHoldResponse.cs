namespace MiniBank.Contracts.Payments;

public sealed class VoidHoldResponse
{
    public Guid HoldId { get; init; }

    public int VoidStatus { get; init; }

    public string HoldStatus { get; init; } = string.Empty;
}