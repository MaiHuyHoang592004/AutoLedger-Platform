using Microsoft.AspNetCore.Mvc;
using MiniBank.Application.Abstractions;
using MiniBank.Application.Exceptions;
using MiniBank.Api.Models;
using MiniBank.Contracts.Payments;

namespace MiniBank.Api.Controllers;

[ApiController]
[Route("api/holds")]
public sealed class HoldsController : ControllerBase
{
    private readonly IPaymentService _paymentService;

    public HoldsController(IPaymentService paymentService)
    {
        _paymentService = paymentService;
    }

    [HttpPost("{holdId:guid}/void")]
    [ProducesResponseType(typeof(VoidHoldResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status409Conflict)]
    [ProducesResponseType(StatusCodes.Status500InternalServerError)]
    public async Task<IActionResult> VoidHold(
        [FromRoute] Guid holdId,
        [FromHeader(Name = "Idempotency-Key")] string? idempotencyKey,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(idempotencyKey))
        {
            return StatusCode(StatusCodes.Status400BadRequest, new ApiErrorResponse
            {
                Title = "MiniBank void hold failed",
                Status = StatusCodes.Status400BadRequest,
                Detail = "Idempotency-Key header is required.",
                Code = "IDEMPOTENCY_KEY_REQUIRED",
            });
        }

        try
        {
            var result = await _paymentService.VoidHoldAsync(holdId, idempotencyKey, cancellationToken);
            return Ok(result.Response);
        }
        catch (MiniBankApplicationException ex)
        {
            return StatusCode(ex.StatusCode, new ApiErrorResponse
            {
                Title = "MiniBank void hold failed",
                Status = ex.StatusCode,
                Detail = ex.Message,
                Code = ex.ErrorCode,
            });
        }
    }
}