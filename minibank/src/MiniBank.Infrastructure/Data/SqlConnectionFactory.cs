using Microsoft.Data.SqlClient;

namespace MiniBank.Infrastructure.Data;

public sealed class SqlConnectionFactory
{
    private readonly string _connectionString;

    public SqlConnectionFactory(string connectionString)
    {
        if (string.IsNullOrWhiteSpace(connectionString))
        {
            throw new ArgumentException("MiniBank connection string is required.", nameof(connectionString));
        }

        _connectionString = connectionString;
    }

    public SqlConnection CreateConnection() => new(_connectionString);
}