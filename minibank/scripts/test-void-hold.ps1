param(
    [string]$BaseUrl = "http://localhost:5099",
    [string]$SqlServer = "localhost,1433",
    [string]$Database = "MiniBank",
    [string]$SqlUser = "sa",
    [string]$SqlPassword = "123",
    [string]$BookingIdPrefix = "BK-VOID",
    [string]$InitIdempotencyKeyPrefix = "void-init-key",
    [string]$VoidIdempotencyKeyPrefix = "void-hold-key",
    [long]$TotalPrice = 130000,
    [long]$FundingBuffer = 100000
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

$script:Failures = New-Object System.Collections.Generic.List[string]
$script:HttpClient = New-Object System.Net.Http.HttpClient
$script:HttpClient.Timeout = [TimeSpan]::FromSeconds(30)

function Write-Pass { param([string]$Message) Write-Host "[PASS] $Message" -ForegroundColor Green }
function Write-Fail { param([string]$Message) Write-Host "[FAIL] $Message" -ForegroundColor Red; $script:Failures.Add($Message) }
function Assert-True { param([bool]$Condition,[string]$Message) if ($Condition) { Write-Pass $Message } else { Write-Fail $Message } }
function Assert-Equal { param($Expected,$Actual,[string]$Message) if ($Expected -eq $Actual) { Write-Pass "$Message (expected=$Expected, actual=$Actual)" } else { Write-Fail "$Message (expected=$Expected, actual=$Actual)" } }
function Escape-SqlLiteral { param([string]$Value) return $Value.Replace("'", "''") }

function Invoke-SqlJson {
    param([string]$Query)

    $output = & sqlcmd -S $SqlServer -U $SqlUser -P $SqlPassword -d $Database -C -h -1 -w 65535 -Q ("SET NOCOUNT ON; " + $Query) 2>&1
    if ($LASTEXITCODE -ne 0) { throw "sqlcmd failed:`n$($output -join [Environment]::NewLine)" }

    $json = ($output |
        Where-Object { $_ -is [string] } |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ -ne '' -and $_ -notmatch '^(Msg\s+\d+,\s+Level\s+\d+|Changed database context to|\(\d+ rows? affected\))' }) -join ''

    if ([string]::IsNullOrWhiteSpace($json)) { return $null }
    return $json | ConvertFrom-Json
}

function Invoke-ApiJsonPost {
    param([string]$Uri,[string]$Body,[hashtable]$Headers)

    $request = New-Object System.Net.Http.HttpRequestMessage -ArgumentList ([System.Net.Http.HttpMethod]::Post), $Uri
    foreach ($entry in $Headers.GetEnumerator()) { $request.Headers.TryAddWithoutValidation($entry.Key, [string]$entry.Value) | Out-Null }
    $request.Content = New-Object System.Net.Http.StringContent -ArgumentList $Body, ([System.Text.Encoding]::UTF8), 'application/json'

    $response = $script:HttpClient.SendAsync($request).GetAwaiter().GetResult()
    $rawBody = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $jsonBody = $null
    if (-not [string]::IsNullOrWhiteSpace($rawBody)) { try { $jsonBody = $rawBody | ConvertFrom-Json -ErrorAction Stop } catch { } }

    return [pscustomobject]@{ StatusCode = [int]$response.StatusCode; RawBody = $rawBody; JsonBody = $jsonBody }
}

function Invoke-ApiGet {
    param([string]$Uri)
    $response = $script:HttpClient.GetAsync($Uri).GetAwaiter().GetResult()
    $rawBody = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $jsonBody = $null
    if (-not [string]::IsNullOrWhiteSpace($rawBody)) { try { $jsonBody = $rawBody | ConvertFrom-Json -ErrorAction Stop } catch { } }
    return [pscustomobject]@{ StatusCode = [int]$response.StatusCode; RawBody = $rawBody; JsonBody = $jsonBody }
}

function Get-CustomerLiabilityAccount {
    $result = Invoke-SqlJson "SELECT TOP (1) a.account_id AS accountId, b.available_balance_minor AS availableBalanceMinor FROM dbo.accounts a JOIN dbo.account_balances_current b ON a.account_id = b.account_id WHERE a.account_code = N'CUSTOMER_LIAB' FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;"
    if ($null -eq $result) { throw 'CUSTOMER_LIAB account not found.' }
    return $result
}

function Ensure-AvailableBalance {
    param([int]$AccountId,[long]$CurrentBalance,[long]$RequiredBalance)
    if ($CurrentBalance -ge $RequiredBalance) { return }
    $delta = $RequiredBalance - $CurrentBalance
    Invoke-SqlJson "EXEC dbo.sp_apply_available_delta @account_id = $AccountId, @delta_minor = $delta, @enforce_non_negative = 0; SELECT CAST(1 AS INT) AS ok FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;" | Out-Null
}

$runId = Get-Date -Format 'yyyyMMddHHmmss'
$bookingId = "$BookingIdPrefix-$runId"
$initKey = "$InitIdempotencyKeyPrefix-$runId"
$voidKey = "$VoidIdempotencyKeyPrefix-$runId"

Write-Host 'MiniBank Void Hold test run' -ForegroundColor Yellow
Write-Host "BaseUrl: $BaseUrl"

$account = Get-CustomerLiabilityAccount
$requiredBalance = $TotalPrice + $FundingBuffer
Ensure-AvailableBalance -AccountId ([int]$account.accountId) -CurrentBalance ([long]$account.availableBalanceMinor) -RequiredBalance $requiredBalance
$startingBalance = [long](Get-CustomerLiabilityAccount).availableBalanceMinor

Write-Host "`n=== Step 1: Init Payment ===" -ForegroundColor Cyan
$initResponse = Invoke-ApiJsonPost -Uri "$BaseUrl/api/payments" -Body (@{ bookingId = $bookingId; totalPrice = $TotalPrice } | ConvertTo-Json -Compress) -Headers @{ 'Idempotency-Key' = $initKey }
Assert-Equal 201 $initResponse.StatusCode 'Init Payment should return 201 Created'
$paymentId = [string]$initResponse.JsonBody.paymentId
Assert-True (-not [string]::IsNullOrWhiteSpace($paymentId)) 'Init Payment should return paymentId'

Write-Host "`n=== Step 2: Authorize Hold ===" -ForegroundColor Cyan
$holdResponse = Invoke-ApiJsonPost -Uri "$BaseUrl/api/payments/$paymentId/authorize-hold" -Body '{}' -Headers @{}
Assert-Equal 201 $holdResponse.StatusCode 'Authorize Hold should return 201 Created'
$holdId = [string]$holdResponse.JsonBody.holdId
Assert-True (-not [string]::IsNullOrWhiteSpace($holdId)) 'Authorize Hold should return holdId'

$balanceAfterHold = [long](Get-CustomerLiabilityAccount).availableBalanceMinor
Assert-Equal ($startingBalance - $TotalPrice) $balanceAfterHold 'Available balance should decrease after hold'

Write-Host "`n=== Step 3: Get Payment ===" -ForegroundColor Cyan
$getResponse = Invoke-ApiGet -Uri "$BaseUrl/api/payments/$paymentId"
Assert-Equal 200 $getResponse.StatusCode 'Get Payment should return 200 OK'
Assert-Equal $paymentId ([string]$getResponse.JsonBody.paymentId) 'Get Payment should return same paymentId'
Assert-Equal $holdId ([string]$getResponse.JsonBody.holdId) 'Get Payment should include latest holdId'
Assert-Equal 'AUTHORIZED' ([string]$getResponse.JsonBody.holdStatus) 'Get Payment should show AUTHORIZED hold status'

Write-Host "`n=== Step 4: Void Hold ===" -ForegroundColor Cyan
$voidResponse = Invoke-ApiJsonPost -Uri "$BaseUrl/api/holds/$holdId/void" -Body '{}' -Headers @{ 'Idempotency-Key' = $voidKey }
Assert-Equal 200 $voidResponse.StatusCode 'Void Hold should return 200 OK'
Assert-Equal $holdId ([string]$voidResponse.JsonBody.holdId) 'Void response should return same holdId'
Assert-Equal 3 ([int]$voidResponse.JsonBody.voidStatus) 'Void response should return void_status=3'

Write-Host "`n=== Step 5: Database Verification ===" -ForegroundColor Cyan
$escapedHoldId = Escape-SqlLiteral $holdId
$holdRow = Invoke-SqlJson "SELECT TOP (1) status AS status, remaining_amount_minor AS remainingAmountMinor FROM dbo.holds WHERE hold_id = '$escapedHoldId' FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;"
Assert-Equal 3 ([int]$holdRow.status) 'dbo.holds.status should be VOIDED (3)'
Assert-Equal $TotalPrice ([long]$holdRow.remainingAmountMinor) 'remaining_amount_minor should remain the original amount after void'

$auditCount = Invoke-SqlJson "SELECT COUNT(1) AS [count] FROM dbo.audit_events WHERE resource_type = N'Hold' AND resource_id = '$escapedHoldId' AND action = N'HOLD_VOID' FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;"
Assert-True (([int]$auditCount.count) -ge 1) 'dbo.audit_events should contain a HOLD_VOID event for the hold'

$balanceAfterVoid = [long](Get-CustomerLiabilityAccount).availableBalanceMinor
Assert-Equal $startingBalance $balanceAfterVoid 'Available balance should be fully restored after void'

Write-Host "`n=== Summary ===" -ForegroundColor Cyan
if ($script:Failures.Count -eq 0) {
    Write-Host 'All Void Hold checks PASSED.' -ForegroundColor Green
    exit 0
}

Write-Host ("FAILED checks: " + $script:Failures.Count) -ForegroundColor Red
$script:Failures | ForEach-Object { Write-Host " - $_" -ForegroundColor Red }
exit 1