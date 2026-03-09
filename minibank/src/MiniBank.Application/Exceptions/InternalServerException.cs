namespace MiniBank.Application.Exceptions;

public sealed class InternalServerException : MiniBankApplicationException
{
    public InternalServerException(string message)
        : base(message, 500)
    {
    }
}