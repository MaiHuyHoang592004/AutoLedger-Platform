namespace MiniBank.Application.Exceptions;

public abstract class MiniBankApplicationException : Exception
{
    protected MiniBankApplicationException(string message, int statusCode, string errorCode)
        : base(message)
    {
        StatusCode = statusCode;
        ErrorCode = errorCode;
    }

    public int StatusCode { get; }

    public string ErrorCode { get; }
}