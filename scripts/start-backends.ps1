$ErrorActionPreference = 'Stop'
$revenuePort = if ($env:REVENUE_PORT) { [int]$env:REVENUE_PORT } else { 24808 }

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
  throw 'Docker CLI was not found. Install Docker Desktop and start its Linux engine.'
}
docker info --format '{{.ServerVersion}}' *> $null
if ($LASTEXITCODE -ne 0) {
  throw 'Docker Desktop is installed but its Linux engine is not running.'
}

function Wait-HttpEndpoint([string]$Name, [string]$Url, [int]$TimeoutSeconds = 180) {
  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  do {
    try {
      $response = Invoke-WebRequest -Uri $Url -TimeoutSec 4 -UseBasicParsing
      if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) { return }
    } catch {
      Start-Sleep -Seconds 3
    }
  } while ((Get-Date) -lt $deadline)
  throw "$Name did not become healthy at $Url within $TimeoutSeconds seconds. Check: docker compose logs"
}

function Wait-TcpEndpoint([string]$Name, [string]$RemoteHost, [int]$Port, [int]$TimeoutSeconds = 120) {
  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  do {
    if (Test-NetConnection -ComputerName $RemoteHost -Port $Port -InformationLevel Quiet -WarningAction SilentlyContinue) { return }
    Start-Sleep -Seconds 3
  } while ((Get-Date) -lt $deadline)
  throw "$Name did not open $RemoteHost`:$Port within $TimeoutSeconds seconds. Check: docker compose logs"
}

# The unified compose file owns Billd, Revenue, SRS, LiveKit, and their
# dependencies. Keeping Revenue in the same project makes the health-checked
# dependency chain deterministic and avoids two APIs sharing the same ledger.
docker compose -p live-platform -f (Join-Path $PSScriptRoot '..\backend-compose.yml') up --build -d
if ($LASTEXITCODE -ne 0) { throw 'Billd/SRS stack did not become ready.' }
# Older revisions launched a second Revenue compose project. Stop its
# containers if they are still present; volumes are intentionally preserved.
$legacyRevenueCompose = Join-Path $PSScriptRoot '..\third_party\live-revenue-integrity-lab\docker-compose.yml'
if (Test-Path $legacyRevenueCompose) {
  docker compose -p live-revenue -f $legacyRevenueCompose down --remove-orphans *> $null
}
$openImRoot = Join-Path $PSScriptRoot '..\third_party\openim-server'
docker network inspect live_backend *> $null
if ($LASTEXITCODE -ne 0) {
  docker network create live_backend *> $null
  if ($LASTEXITCODE -ne 0) { throw 'The live_backend Docker network could not be created.' }
}
docker compose --env-file (Join-Path $openImRoot '.env') -f (Join-Path $openImRoot 'docker-compose.yml') up --build -d
if ($LASTEXITCODE -ne 0) { throw 'OpenIM stack did not start.' }

Wait-HttpEndpoint 'Billd' 'http://localhost:4300/'
Wait-HttpEndpoint 'Revenue API' "http://localhost:$revenuePort/healthz" 300
Wait-HttpEndpoint 'SRS' 'http://localhost:1985/api/v1/versions'
Wait-TcpEndpoint 'LiveKit' 'localhost' 7880
Wait-TcpEndpoint 'OpenIM API' 'localhost' 10002 600
Wait-TcpEndpoint 'OpenIM message WebSocket' 'localhost' 10001 600
Write-Host 'Billd API: http://localhost:4300'
Write-Host "Revenue API: http://localhost:$revenuePort"
Write-Host 'SRS API: http://localhost:1985'
Write-Host 'LiveKit: ws://localhost:7880 (dev key: devkey, secret: secret)'
Write-Host 'OpenIM API: http://localhost:10002'
Write-Host 'OpenIM message WebSocket: ws://localhost:10001'
