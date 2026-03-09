using Microsoft.AspNetCore.Mvc;
using MiniBank.Application.Abstractions;
using MiniBank.Application.Exceptions;
using MiniBank.Contracts.Payments;

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
            return Problem(
                title: "MiniBank payment initialization failed",
                detail: ex.Message,
                statusCode: ex.StatusCode);
        }
    }
}