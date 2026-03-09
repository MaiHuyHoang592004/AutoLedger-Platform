using MiniBank.Application.Abstractions;
using MiniBank.Application.Payments;
using MiniBank.Infrastructure.Data;
using MiniBank.Infrastructure.Payments;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();
builder.Services.AddOpenApi();

var miniBankConnectionString = builder.Configuration.GetConnectionString("MiniBank")
    ?? throw new InvalidOperationException("Connection string 'MiniBank' is missing.");

builder.Services.AddSingleton(new SqlConnectionFactory(miniBankConnectionString));
builder.Services.AddScoped<IPaymentRepository, PaymentRepository>();
builder.Services.AddScoped<IPaymentService, PaymentService>();

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
}

app.UseAuthorization();
app.MapControllers();

app.Run();
