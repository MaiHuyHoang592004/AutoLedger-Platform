param(
    [string]$BaseUrl = "http://localhost:5099",
    [string]$SqlServer = "localhost,1433",
    [string]$Database = "MiniBank",
    [string]$SqlUser = "sa",
    [string]$SqlPassword = "123",
    [string]$BookingIdPrefix = "BK-HOLD",
    [string]$InitIdempotencyKeyPrefix = "hold-init-key",
    [string]$AuthorizeIdempotencyKeyPrefix = "hold-auth-key",
    [long]$TotalPrice = 120000,
    [long]$FundingBuffer = 100000
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
    param([bool]$Condition, [string]$Message)
    if ($Condition) { Write-Pass $Message } else { Write-Fail $Message }
}

function Assert-Equal {
    param($Expected, $Actual, [string]$Message)
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
        Where-Object {
            $_ -ne '' -and
            $_ -notmatch '^(Msg\s+\d+,\s+Level\s+\d+|Changed database context to|\(\d+ rows? affected\))'
        }) -join ''

    if ([string]::IsNullOrWhiteSpace($json)) {
        return $null
    }

    return $json | ConvertFrom-Json
}

function New-JsonRequestBody {
    param([string]$BookingId, [long]$AmountMinor)
    return (@{ bookingId = $BookingId; totalPrice = $AmountMinor } | ConvertTo-Json -Compress)
}

function Invoke-ApiJsonPost {
    param(
        [string]$Uri,
        [string]$Body,
        [hashtable]$Headers
    )

    $request = New-Object System.Net.Http.HttpRequestMessage -ArgumentList ([System.Net.Http.HttpMethod]::Post), $Uri
    foreach ($entry in $Headers.GetEnumerator()) {
        $request.Headers.TryAddWithoutValidation($entry.Key, [string]$entry.Value) | Out-Null
    }
    $request.Content = New-Object System.Net.Http.StringContent -ArgumentList $Body, ([System.Text.Encoding]::UTF8), 'application/json'

    $response = $script:HttpClient.SendAsync($request).GetAwaiter().GetResult()
    $rawBody = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $jsonBody = $null
    if (-not [string]::IsNullOrWhiteSpace($rawBody)) {
        try { $jsonBody = $rawBody | ConvertFrom-Json -ErrorAction Stop } catch { }
    }

    return [pscustomobject]@{
        StatusCode = [int]$response.StatusCode
        RawBody    = $rawBody
        JsonBody   = $jsonBody
    }
}

function Get-CustomerLiabilityAccount {
    $result = Invoke-SqlJson "SELECT TOP (1) a.account_id AS accountId, b.available_balance_minor AS availableBalanceMinor FROM dbo.accounts a JOIN dbo.account_balances_current b ON a.account_id = b.account_id WHERE a.account_code = N'CUSTOMER_LIAB' FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;"
    if ($null -eq $result) {
        throw 'CUSTOMER_LIAB account not found.'
    }
    return $result
}

function Ensure-AvailableBalance {
    param(
        [int]$AccountId,
        [long]$CurrentBalance,
        [long]$RequiredBalance
    )

    if ($CurrentBalance -ge $RequiredBalance) {
        return
    }

    $delta = $RequiredBalance - $CurrentBalance
    Invoke-SqlJson "EXEC dbo.sp_apply_available_delta @account_id = $AccountId, @delta_minor = $delta, @enforce_non_negative = 0; SELECT CAST(1 AS INT) AS ok FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;" | Out-Null
}

$runId = Get-Date -Format 'yyyyMMddHHmmss'
$bookingId = "$BookingIdPrefix-$runId"
$initIdempotencyKey = "$InitIdempotencyKeyPrefix-$runId"
$authorizeIdempotencyKey = "$AuthorizeIdempotencyKeyPrefix-$runId"

Write-Host 'MiniBank Authorize Hold test run' -ForegroundColor Yellow
Write-Host "BaseUrl: $BaseUrl"
Write-Host "SQL: $SqlServer / $Database"
Write-Host "BookingId: $bookingId"
Write-Host "Init Idempotency-Key: $initIdempotencyKey"
Write-Host "Authorize Idempotency-Key: $authorizeIdempotencyKey"

$paymentResponse = Invoke-ApiJsonPost -Uri "$BaseUrl/api/payments" -Body (New-JsonRequestBody -BookingId $bookingId -AmountMinor $TotalPrice) -Headers @{ 'Idempotency-Key' = $initIdempotencyKey }

Write-Host "`n=== Step 1: Init Payment ===" -ForegroundColor Cyan
Assert-Equal 201 $paymentResponse.StatusCode 'Init Payment should return 201 Created'
Assert-True ($null -ne $paymentResponse.JsonBody.paymentId) 'Init Payment should return paymentId'

$paymentId = [string]$paymentResponse.JsonBody.paymentId

$account = Get-CustomerLiabilityAccount
$beforeBalance = [long]$account.availableBalanceMinor
$requiredBalance = $TotalPrice + $FundingBuffer
Ensure-AvailableBalance -AccountId ([int]$account.accountId) -CurrentBalance $beforeBalance -RequiredBalance $requiredBalance

$accountAfterFunding = Get-CustomerLiabilityAccount
$availableBeforeHold = [long]$accountAfterFunding.availableBalanceMinor
Write-Host "CUSTOMER_LIAB accountId: $($accountAfterFunding.accountId), available before hold: $availableBeforeHold"
Write-Host "`n=== Step 2: Authorize Hold (first call) ===" -ForegroundColor Cyan
$holdResponse = Invoke-ApiJsonPost -Uri "$BaseUrl/api/payments/$paymentId/authorize-hold" -Body '{}' -Headers @{ 'Idempotency-Key' = $authorizeIdempotencyKey }
Assert-Equal 201 $holdResponse.StatusCode 'Authorize Hold should return 201 Created on first call'
Assert-True ($null -ne $holdResponse.JsonBody.holdId) 'Authorize Hold should return holdId'
Assert-Equal 'AUTHORIZED' ([string]$holdResponse.JsonBody.status) 'Hold status should be AUTHORIZED'
Assert-Equal $paymentId ([string]$holdResponse.JsonBody.paymentId) 'Hold response should point to the same payment'
Assert-Equal $TotalPrice ([long]$holdResponse.JsonBody.originalAmountMinor) 'Hold original amount should match payment amount'
Assert-Equal $TotalPrice ([long]$holdResponse.JsonBody.remainingAmountMinor) 'Remaining amount should equal original amount right after authorization'

$holdId = [string]$holdResponse.JsonBody.holdId
$balanceAfterFirstAuthorize = [long](Get-CustomerLiabilityAccount).availableBalanceMinor
Assert-Equal ($availableBeforeHold - $TotalPrice) $balanceAfterFirstAuthorize 'CUSTOMER_LIAB available_balance should decrease by the hold amount after first authorize'

Write-Host "`n=== Step 3: Authorize Hold Replay ===" -ForegroundColor Cyan
$replayResponse = Invoke-ApiJsonPost -Uri "$BaseUrl/api/payments/$paymentId/authorize-hold" -Body '{}' -Headers @{ 'Idempotency-Key' = $authorizeIdempotencyKey }
Assert-Equal 200 $replayResponse.StatusCode 'Authorize Hold replay should return 200 OK'
Assert-Equal $holdId ([string]$replayResponse.JsonBody.holdId) 'Replay should return the same holdId'
Assert-Equal $paymentId ([string]$replayResponse.JsonBody.paymentId) 'Replay should return the same paymentId'
Assert-Equal 'AUTHORIZED' ([string]$replayResponse.JsonBody.status) 'Replay hold status should stay AUTHORIZED'

$balanceAfterReplay = [long](Get-CustomerLiabilityAccount).availableBalanceMinor
Assert-Equal $balanceAfterFirstAuthorize $balanceAfterReplay 'Replay must not deduct available balance a second time'

Write-Host "`n=== Step 4: Database Verification ===" -ForegroundColor Cyan
$escapedHoldId = Escape-SqlLiteral $holdId
$holdRow = Invoke-SqlJson "SELECT TOP (1) hold_id AS holdId, payment_id AS paymentId, account_id AS accountId, original_amount_minor AS originalAmountMinor, remaining_amount_minor AS remainingAmountMinor, status AS status, currency AS currency FROM dbo.holds WHERE hold_id = '$escapedHoldId' FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;"
Assert-True ($null -ne $holdRow) 'dbo.holds should contain the new hold row'
if ($null -ne $holdRow) {
    Assert-Equal $paymentId ([string]$holdRow.paymentId) 'dbo.holds.payment_id should match paymentId'
    Assert-Equal 1 ([int]$holdRow.status) 'dbo.holds.status should be AUTHORIZED (1)'
    Assert-Equal $TotalPrice ([long]$holdRow.originalAmountMinor) 'dbo.holds.original_amount_minor should match payment amount'
    Assert-Equal $TotalPrice ([long]$holdRow.remainingAmountMinor) 'dbo.holds.remaining_amount_minor should equal original amount after authorization'
}

$holdEventCount = Invoke-SqlJson "SELECT COUNT(1) AS [count] FROM dbo.hold_events WHERE hold_id = '$escapedHoldId' AND event_type = N'AUTHORIZED' FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;"
Assert-Equal 1 ([int]$holdEventCount.count) 'dbo.hold_events should contain exactly one AUTHORIZED event for the hold'

$paymentHoldCount = Invoke-SqlJson "SELECT COUNT(1) AS [count] FROM dbo.holds WHERE payment_id = '$([string](Escape-SqlLiteral $paymentId))' FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;"
Assert-Equal 1 ([int]$paymentHoldCount.count) 'dbo.holds should contain exactly one hold for the payment after replay'

$idemRow = Invoke-SqlJson "SELECT TOP (1) status AS status, response_code AS responseCode FROM dbo.idempotency_keys WHERE request_route = N'/api/payments/$paymentId/authorize-hold' AND idem_key = N'$authorizeIdempotencyKey' ORDER BY idem_id DESC FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;"
Assert-Equal 2 ([int]$idemRow.status) 'Idempotency record for authorize hold should be COMPLETED (2)'
Assert-Equal 201 ([int]$idemRow.responseCode) 'Idempotency record should store 201 response code from the first authorize call'

Write-Host "`n=== Summary ===" -ForegroundColor Cyan
if ($script:Failures.Count -eq 0) {
    Write-Host 'All Authorize Hold checks PASSED.' -ForegroundColor Green
    exit 0
}

Write-Host ("FAILED checks: " + $script:Failures.Count) -ForegroundColor Red
$script:Failures | ForEach-Object { Write-Host " - $_" -ForegroundColor Red }
exit 1