namespace MiniBank.Application.Exceptions;

public sealed class BadRequestException : MiniBankApplicationException
{
    public BadRequestException(string message, string errorCode = "BAD_REQUEST")
        : base(message, 400, errorCode)
    {
    }
}