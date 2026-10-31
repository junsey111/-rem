param(
  [string]$BilldUrl = 'http://localhost:4300',
  [string]$RevenueUrl = 'http://localhost:18080',
  [switch]$SkipOpenIm
)

$ErrorActionPreference = 'Stop'

function Invoke-Json([string]$Method, [string]$Url, [object]$Body = $null, [hashtable]$Headers = @{}) {
  $params = @{
    Method = $Method
    Uri = $Url
    Headers = $Headers
    ContentType = 'application/json'
    UseBasicParsing = $true
  }
  if ($null -ne $Body) { $params.Body = ($Body | ConvertTo-Json -Compress) }
  Invoke-RestMethod @params
}

Invoke-WebRequest -Uri "$BilldUrl/" -UseBasicParsing | Out-Null
$suffix = [Guid]::NewGuid().ToString('N').Substring(0, 10)
$password = "Smoke_$suffix"
$created = Invoke-Json 'POST' "$BilldUrl/user/create" @{
  username = "smoke_$suffix"
  password = $password
}
$userId = [int]$created.data.id
if ($userId -le 0) { throw 'Billd registration did not return a user id.' }

$loggedIn = Invoke-Json 'POST' "$BilldUrl/user/login" @{ id = $userId; password = $password }
$jwt = [string]$loggedIn.data
if ([string]::IsNullOrWhiteSpace($jwt)) { throw 'Billd login did not return a JWT.' }
$auth = @{ Authorization = "Bearer $jwt" }

$gifts = Invoke-Json 'GET' "$BilldUrl/revenue/gifts"
if ($null -eq $gifts.data) { throw 'Gift catalog response did not contain data.' }
$wallet = Invoke-Json 'GET' "$BilldUrl/revenue/wallet" $null $auth
if ($null -eq $wallet.data) { throw 'Wallet response did not contain data.' }

if (-not $SkipOpenIm) {
  $openIm = Invoke-Json 'POST' "$BilldUrl/openim/session" @{} $auth
  if ([string]::IsNullOrWhiteSpace([string]$openIm.data.token)) {
    throw 'OpenIM session response did not contain a user token.'
  }
}

Invoke-WebRequest -Uri "$RevenueUrl/healthz" -UseBasicParsing | Out-Null
Write-Host "Backend smoke test passed for temporary user $userId."
