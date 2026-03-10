using Microsoft.AspNetCore.Mvc;
using MiniBank.Application.Abstractions;
using MiniBank.Application.Exceptions;
using MiniBank.Contracts.Payments;
using MiniBank.Api.Models;

namespace MiniBank.Api.Controllers;

[ApiController]
[Route("api/payments")]
public sealed class PaymentsController : ControllerBase
{
    private readonly IPaymentService _paymentService;

    public PaymentsController(IPaymentService paymentService)
    {
        _paymentService = paymentService;
    }

    [HttpPost]
    [ProducesResponseType(typeof(CreatePaymentResponse), StatusCodes.Status201Created)]
    [ProducesResponseType(typeof(CreatePaymentResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status409Conflict)]
    [ProducesResponseType(StatusCodes.Status500InternalServerError)]
    public async Task<IActionResult> CreatePayment(
        [FromBody] CreatePaymentRequest request,
        [FromHeader(Name = "Idempotency-Key")] string? idempotencyKey,
        CancellationToken cancellationToken)
    {
        try
        {
            var result = await _paymentService.CreatePaymentAsync(request, idempotencyKey, cancellationToken);

            return result.IsReplay
                ? Ok(result.Response)
                : StatusCode(StatusCodes.Status201Created, result.Response);
        }
        catch (MiniBankApplicationException ex)
        {
            return ToErrorResult("MiniBank payment initialization failed", ex);
        }
    }

    [HttpPost("{paymentId:guid}/authorize-hold")]
    [ProducesResponseType(typeof(AuthorizeHoldResponse), StatusCodes.Status201Created)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status409Conflict)]
    [ProducesResponseType(StatusCodes.Status500InternalServerError)]
    public async Task<IActionResult> AuthorizeHold(
        [FromRoute] Guid paymentId,
        [FromHeader(Name = "Idempotency-Key")] string? idempotencyKey,
        CancellationToken cancellationToken)
    {
        try
        {
            var result = await _paymentService.AuthorizeHoldAsync(paymentId, idempotencyKey, cancellationToken);
            return result.IsReplay
                ? Ok(result.Response)
                : StatusCode(StatusCodes.Status201Created, result.Response);
        }
        catch (MiniBankApplicationException ex)
        {
            return ToErrorResult("MiniBank authorize hold failed", ex);
        }
    }

    [HttpGet("{paymentId:guid}")]
    [ProducesResponseType(typeof(GetPaymentResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status500InternalServerError)]
    public async Task<IActionResult> GetPayment(
        [FromRoute] Guid paymentId,
        CancellationToken cancellationToken)
    {
        try
        {
            var result = await _paymentService.GetPaymentAsync(paymentId, cancellationToken);
            return Ok(result.Response);
        }
        catch (MiniBankApplicationException ex)
        {
            return ToErrorResult("MiniBank payment lookup failed", ex);
        }
    }

    private ObjectResult ToErrorResult(string title, MiniBankApplicationException ex)
    {
        return StatusCode(ex.StatusCode, new ApiErrorResponse
        {
            Title = title,
            Status = ex.StatusCode,
            Detail = ex.Message,
            Code = ex.ErrorCode,
        });
    }
}