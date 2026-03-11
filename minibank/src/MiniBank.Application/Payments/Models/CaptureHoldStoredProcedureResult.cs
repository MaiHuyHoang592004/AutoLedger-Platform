namespace MiniBank.Application.Payments.Models;

public sealed class CaptureHoldStoredProcedureResult
{
    public string Result { get; init; } = string.Empty;

    public int ResponseCode { get; init; }

    public string ResponseBody { get; init; } = string.Empty;
}