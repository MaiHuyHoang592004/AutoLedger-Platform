namespace MiniBank.Application.Exceptions;

public sealed class InternalServerException : MiniBankApplicationException
{
    public InternalServerException(string message, string errorCode = "INTERNAL_SERVER_ERROR")
        : base(message, 500, errorCode)
    {
    }
}