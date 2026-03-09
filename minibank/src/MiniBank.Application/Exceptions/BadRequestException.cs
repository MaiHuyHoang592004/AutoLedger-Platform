namespace MiniBank.Application.Exceptions;

public sealed class BadRequestException : MiniBankApplicationException
{
    public BadRequestException(string message)
        : base(message, 400)
    {
    }
}