namespace MiniBank.Application.Exceptions;

public abstract class MiniBankApplicationException : Exception
{
    protected MiniBankApplicationException(string message, int statusCode)
        : base(message)
    {
        StatusCode = statusCode;
    }

    public int StatusCode { get; }
}