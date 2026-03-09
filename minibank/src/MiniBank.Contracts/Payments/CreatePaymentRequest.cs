using System.ComponentModel.DataAnnotations;

namespace MiniBank.Contracts.Payments;

public sealed class CreatePaymentRequest
{
    [Required]
    [StringLength(100, MinimumLength = 1)]
    public string BookingId { get; set; } = string.Empty;

    [Range(1, long.MaxValue)]
    public long TotalPrice { get; set; }
}