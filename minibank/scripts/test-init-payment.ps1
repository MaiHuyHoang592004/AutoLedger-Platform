param(
    [string]$BaseUrl = "http://localhost:5099",
    [string]$SqlServer = "localhost,1433",
    [string]$Database = "MiniBank",
    [string]$SqlUser = "sa",
    [string]$SqlPassword = "123",
    [string]$BookingIdPrefix = "BK-999",
    [string]$IdempotencyKeyPrefix = "test-key-001",
    [long]$TotalPrice = 100000,
    [switch]$UseFixedIds,
    [switch]$SkipConcurrency
)

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Net.Http

$script:Failures = New-Object System.Collections.Generic.List[string]
$script:HttpClient = New-Object System.Net.Http.HttpClient
$script:HttpClient.Timeout = [TimeSpan]::FromSeconds(30)

function Write-Pass {
    param([string]$Message)
    Write-Host "[PASS] $Message" -ForegroundColor Green
}

function Write-Fail {
    param([string]$Message)
    Write-Host "[FAIL] $Message" -ForegroundColor Red
    $script:Failures.Add($Message)
}

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if ($Condition) {
        Write-Pass $Message
    }
    else {
        Write-Fail $Message
    }
}

function Assert-Equal {
    param(
        $Expected,
        $Actual,
        [string]$Message
    )

    if ($Expected -eq $Actual) {
        Write-Pass "$Message (expected=$Expected, actual=$Actual)"
    }
    else {
        Write-Fail "$Message (expected=$Expected, actual=$Actual)"
    }
}

function Escape-SqlLiteral {
    param([string]$Value)
    return $Value.Replace("'", "''")
}

function Invoke-SqlJson {
    param([string]$Query)

    $output = & sqlcmd `
        -S $SqlServer `
        -U $SqlUser `
        -P $SqlPassword `
        -d $Database `
        -C `
        -h -1 `
        -w 65535 `
        -Q ("SET NOCOUNT ON; " + $Query) 2>&1

    if ($LASTEXITCODE -ne 0) {
        throw "sqlcmd failed:`n$($output -join [Environment]::NewLine)"
    }

    $json = ($output |
        Where-Object { $_ -is [string] } |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ -ne '' }) -join ''

    if ([string]::IsNullOrWhiteSpace($json)) {
        return $null
    }

    return $json | ConvertFrom-Json
}

function New-JsonRequestBody {
    param(
        [string]$BookingId,
        [long]$AmountMinor
    )

    return (@{
            bookingId  = $BookingId
            totalPrice = $AmountMinor
        } | ConvertTo-Json -Compress)
}

function Invoke-CreatePayment {
    param(
        [string]$IdempotencyKey,
        [string]$BookingId,
        [long]$AmountMinor
    )

    $uri = "$BaseUrl/api/payments"
    $payload = New-JsonRequestBody -BookingId $BookingId -AmountMinor $AmountMinor
    $request = New-Object System.Net.Http.HttpRequestMessage -ArgumentList ([System.Net.Http.HttpMethod]::Post), $uri
    $request.Headers.TryAddWithoutValidation('Idempotency-Key', $IdempotencyKey) | Out-Null
    $request.Content = New-Object System.Net.Http.StringContent -ArgumentList $payload, ([System.Text.Encoding]::UTF8), 'application/json'

    try {
        $response = $script:HttpClient.SendAsync($request).GetAwaiter().GetResult()
    }
    catch {
        throw "HTTP request to $uri failed: $($_.Exception.Message)"
    }

    $rawBody = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $jsonBody = $null
    if (-not [string]::IsNullOrWhiteSpace($rawBody)) {
        try {
            $jsonBody = $rawBody | ConvertFrom-Json -ErrorAction Stop
        }
        catch {
            $jsonBody = $null
        }
    }

    return [pscustomobject]@{
        StatusCode = [int]$response.StatusCode
        RawBody    = $rawBody
        JsonBody   = $jsonBody
    }
}

function Test-HappyPath {
    param(
        [string]$BookingId,
        [string]$IdempotencyKey,
        [long]$AmountMinor
    )

    Write-Host "`n=== Test 1: Happy Path ===" -ForegroundColor Cyan
    $response = Invoke-CreatePayment -IdempotencyKey $IdempotencyKey -BookingId $BookingId -AmountMinor $AmountMinor

    Assert-Equal 201 $response.StatusCode 'POST /api/payments should return 201 Created for a new payment'
    Assert-True ($null -ne $response.JsonBody) 'Response body should be valid JSON'
    Assert-True ($null -ne $response.JsonBody.paymentId) 'Response body should contain paymentId'
    Assert-Equal 'created' $response.JsonBody.status 'Payment status should be created'

    return $response
}

function Test-IdempotencyReplay {
    param(
        [string]$BookingId,
        [string]$IdempotencyKey,
        [long]$AmountMinor,
        [string]$ExpectedPaymentId
    )

    Write-Host "`n=== Test 2: Idempotency Replay ===" -ForegroundColor Cyan
    $response = Invoke-CreatePayment -IdempotencyKey $IdempotencyKey -BookingId $BookingId -AmountMinor $AmountMinor

    Assert-Equal 200 $response.StatusCode 'Replay request should return 200 OK'
    Assert-True ($null -ne $response.JsonBody) 'Replay response body should be valid JSON'
    Assert-Equal $ExpectedPaymentId ([string]$response.JsonBody.paymentId) 'Replay should return the same paymentId as the first request'

    return $response
}

function Test-DatabaseAudit {
    param(
        [string]$BookingId,
        [string]$IdempotencyKey,
        [string]$PaymentId
    )

    Write-Host "`n=== Test 3: Database Audit ===" -ForegroundColor Cyan

    $escapedBookingId = Escape-SqlLiteral $BookingId
    $escapedIdempotencyKey = Escape-SqlLiteral $IdempotencyKey
    $escapedPaymentId = Escape-SqlLiteral $PaymentId

    $paymentCount = Invoke-SqlJson "SELECT COUNT(1) AS [count] FROM dbo.payments WHERE order_ref = N'$escapedBookingId' FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;"
    Assert-Equal 1 ([int]$paymentCount.count) 'dbo.payments should contain exactly one row for the booking reference'

    $idempotencyRows = @(Invoke-SqlJson "SELECT idem_key, request_route, status, response_code, response_body, completed_at FROM dbo.idempotency_keys WHERE idem_key = N'$escapedIdempotencyKey' FOR JSON PATH;")
    Assert-Equal 1 $idempotencyRows.Count 'dbo.idempotency_keys should contain exactly one row for the test key'
    if ($idempotencyRows.Count -ge 1) {
        $row = $idempotencyRows[0]
        Assert-Equal '/api/payments' ([string]$row.request_route) 'Idempotency row should point to /api/payments'
        Assert-Equal 2 ([int]$row.status) 'Idempotency row should be completed (status=2)'
        Assert-Equal 201 ([int]$row.response_code) 'Completed idempotency row should store response_code = 201'
    }

    $auditCount = Invoke-SqlJson "SELECT COUNT(1) AS [count] FROM dbo.audit_events WHERE resource_type = N'Payment' AND resource_id = '$escapedPaymentId' FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;"
    Assert-True (([int]$auditCount.count) -ge 1) 'dbo.audit_events should contain at least one entry for the payment'

    $outboxCount = Invoke-SqlJson "SELECT COUNT(1) AS [count] FROM dbo.outbox_messages WHERE aggregate_type = N'Payment' AND aggregate_id = '$escapedPaymentId' AND event_type = N'PaymentCreated' FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;"
    Assert-True (([int]$outboxCount.count) -ge 1) 'dbo.outbox_messages should contain a PaymentCreated event for the payment'
}

function Test-PayloadMismatch {
    param(
        [string]$BookingId,
        [string]$IdempotencyKey
    )

    Write-Host "`n=== Test 4: Payload Mismatch ===" -ForegroundColor Cyan
    $response = Invoke-CreatePayment -IdempotencyKey $IdempotencyKey -BookingId $BookingId -AmountMinor 999999

    Assert-True (@(400, 409) -contains $response.StatusCode) 'Reusing the same key with a different payload should return 400 or 409'
}

function Test-Concurrency {
    param(
        [string]$BookingId,
        [string]$IdempotencyKey,
        [long]$AmountMinor
    )

    Write-Host "`n=== Test 5: In-Progress / Concurrency ===" -ForegroundColor Cyan

    $jobScript = {
        param($BaseUrl, $Key, $BookingId, $AmountMinor)

        Add-Type -AssemblyName System.Net.Http

        $client = New-Object System.Net.Http.HttpClient
        $client.Timeout = [TimeSpan]::FromSeconds(30)
        $payload = (@{
                bookingId  = $BookingId
                totalPrice = $AmountMinor
            } | ConvertTo-Json -Compress)

        $request = New-Object System.Net.Http.HttpRequestMessage -ArgumentList ([System.Net.Http.HttpMethod]::Post), "$BaseUrl/api/payments"
        $request.Headers.TryAddWithoutValidation('Idempotency-Key', $Key) | Out-Null
        $request.Content = New-Object System.Net.Http.StringContent -ArgumentList $payload, ([System.Text.Encoding]::UTF8), 'application/json'

        try {
            $response = $client.SendAsync($request).GetAwaiter().GetResult()
            $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            [pscustomobject]@{
                StatusCode = [int]$response.StatusCode
                Body       = $body
            }
        }
        catch {
            [pscustomobject]@{
                StatusCode = 0
                Body       = $_.Exception.Message
            }
        }
    }

    $job1 = Start-Job -ScriptBlock $jobScript -ArgumentList $BaseUrl, $IdempotencyKey, $BookingId, $AmountMinor
    $job2 = Start-Job -ScriptBlock $jobScript -ArgumentList $BaseUrl, $IdempotencyKey, $BookingId, $AmountMinor

    try {
        $results = Receive-Job -Job $job1, $job2 -Wait -AutoRemoveJob
    }
    finally {
        Get-Job | Remove-Job -Force -ErrorAction SilentlyContinue | Out-Null
    }

    $statusCodes = @($results | ForEach-Object { [int]$_.StatusCode })
    Write-Host ("Concurrency statuses: " + ($statusCodes -join ', '))

    Assert-True ($statusCodes -contains 201) 'Concurrency test should produce at least one successful create (201)'
    Assert-True (($statusCodes | Where-Object { $_ -notin 200, 201, 409 }).Count -eq 0) 'Concurrency test responses should stay within the accepted set: 200, 201, 409'

    $escapedBookingId = Escape-SqlLiteral $BookingId
    $paymentCount = Invoke-SqlJson "SELECT COUNT(1) AS [count] FROM dbo.payments WHERE order_ref = N'$escapedBookingId' FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;"
    Assert-Equal 1 ([int]$paymentCount.count) 'Concurrency test should still create only one payment row in dbo.payments'
}

$runId = Get-Date -Format 'yyyyMMddHHmmss'
if ($UseFixedIds) {
    $happyBookingId = $BookingIdPrefix
    $happyIdempotencyKey = $IdempotencyKeyPrefix
    $concurrentBookingId = 'BK-998'
    $concurrentIdempotencyKey = 'test-key-002'
}
else {
    $happyBookingId = "$BookingIdPrefix-$runId"
    $happyIdempotencyKey = "$IdempotencyKeyPrefix-$runId"
    $concurrentBookingId = "BK-998-$runId"
    $concurrentIdempotencyKey = "test-key-002-$runId"
}

Write-Host 'MiniBank Init Payment test run' -ForegroundColor Yellow
Write-Host "BaseUrl: $BaseUrl"
Write-Host "SQL: $SqlServer / $Database"
Write-Host "Happy Path BookingId: $happyBookingId"
Write-Host "Happy Path IdempotencyKey: $happyIdempotencyKey"

$happyResponse = Test-HappyPath -BookingId $happyBookingId -IdempotencyKey $happyIdempotencyKey -AmountMinor $TotalPrice
$paymentId = [string]$happyResponse.JsonBody.paymentId

Test-IdempotencyReplay -BookingId $happyBookingId -IdempotencyKey $happyIdempotencyKey -AmountMinor $TotalPrice -ExpectedPaymentId $paymentId | Out-Null
Test-DatabaseAudit -BookingId $happyBookingId -IdempotencyKey $happyIdempotencyKey -PaymentId $paymentId
Test-PayloadMismatch -BookingId $happyBookingId -IdempotencyKey $happyIdempotencyKey

if (-not $SkipConcurrency) {
    Test-Concurrency -BookingId $concurrentBookingId -IdempotencyKey $concurrentIdempotencyKey -AmountMinor $TotalPrice
}

Write-Host "`n=== Summary ===" -ForegroundColor Cyan
if ($script:Failures.Count -eq 0) {
    Write-Host 'All Init Payment checks PASSED.' -ForegroundColor Green
    exit 0
}

Write-Host ("FAILED checks: " + $script:Failures.Count) -ForegroundColor Red
$script:Failures | ForEach-Object { Write-Host " - $_" -ForegroundColor Red }
exit 1