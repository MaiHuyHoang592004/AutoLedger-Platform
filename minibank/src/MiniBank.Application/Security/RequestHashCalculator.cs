using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace MiniBank.Application.Security;

public static class RequestHashCalculator
{
    public static byte[] CalculateCreatePaymentHash(
        string bookingId,
        long totalPrice,
        Guid merchantId,
        string currency,
        string requestRoute)
    {
        var payload = new
        {
            bookingId,
            totalPrice,
            merchantId,
            currency,
            requestRoute,
        };

        var payloadJson = JsonSerializer.Serialize(payload);
        return SHA256.HashData(Encoding.UTF8.GetBytes(payloadJson));
    }

    public static byte[] CalculateAuthorizeHoldHash(
        Guid paymentId,
        long amountMinor,
        Guid merchantId,
        int accountId,
        string currency,
        string requestRoute)
    {
        var payload = new
        {
            paymentId,
            amountMinor,
            merchantId,
            accountId,
            currency,
            requestRoute,
        };

        var payloadJson = JsonSerializer.Serialize(payload);
        return SHA256.HashData(Encoding.UTF8.GetBytes(payloadJson));
    }

    public static byte[] CalculateVoidHoldHash(
        Guid holdId,
        Guid merchantId,
        byte voidStatus,
        string requestRoute)
    {
        var payload = new
        {
            holdId,
            merchantId,
            voidStatus,
            requestRoute,
        };

        var payloadJson = JsonSerializer.Serialize(payload);
        return SHA256.HashData(Encoding.UTF8.GetBytes(payloadJson));
    }
}