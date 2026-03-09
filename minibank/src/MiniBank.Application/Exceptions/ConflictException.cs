namespace MiniBank.Application.Exceptions;

public sealed class ConflictException : MiniBankApplicationException
{
    public ConflictException(string message)
        : base(message, 409)
    {
    }
}