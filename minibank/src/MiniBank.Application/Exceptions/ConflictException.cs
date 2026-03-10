namespace MiniBank.Application.Exceptions;

public sealed class ConflictException : MiniBankApplicationException
{
    public ConflictException(string message, string errorCode = "CONFLICT")
        : base(message, 409, errorCode)
    {
    }
}