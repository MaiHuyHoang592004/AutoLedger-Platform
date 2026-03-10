namespace MiniBank.Api.Models;

public sealed class ApiErrorResponse
{
    public string Title { get; init; } = string.Empty;

    public int Status { get; init; }

    public string Detail { get; init; } = string.Empty;

    public string Code { get; init; } = string.Empty;
}