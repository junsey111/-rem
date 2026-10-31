param(
  [int]$BilldPort = 4300,
  [int]$MySqlPort = 13306,
  [string]$MySqlDatabase = 'billd_live',
  [string]$MySqlUser = 'billd',
  [string]$MySqlPassword = 'billd',
  [int]$RedisPort = 8888,
  [int]$RevenuePort = 18080,
  [switch]$StartMySqlAsAdmin,
  [switch]$SkipBilld
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$billdRoot = Join-Path $root 'third_party\billd-live-server'

function Test-Tcp([int]$Port) {
  return Test-NetConnection 127.0.0.1 -Port $Port -InformationLevel Quiet -WarningAction SilentlyContinue
}

if (-not (Test-Tcp $RedisPort)) {
  $redis = 'D:\redis\redis-server.exe'
  $redisConfig = 'D:\redis\redis.windows-service.conf'
  if (-not (Test-Path $redis)) { throw "Redis is not listening on $RedisPort and $redis was not found." }
  Start-Process -FilePath $redis -ArgumentList $redisConfig -WorkingDirectory 'D:\redis' -WindowStyle Hidden | Out-Null
  $deadline = (Get-Date).AddSeconds(15)
  while (-not (Test-Tcp $RedisPort) -and (Get-Date) -lt $deadline) { Start-Sleep -Milliseconds 500 }
}
if (-not (Test-Tcp $RedisPort)) { throw "Redis did not become ready on 127.0.0.1:$RedisPort." }

if (-not (Test-Tcp $MySqlPort)) {
  $service = Get-Service MySQL80 -ErrorAction SilentlyContinue
  if ($StartMySqlAsAdmin -and $service -and $service.Status -ne 'Running') {
    Start-Process powershell.exe -Verb RunAs -Wait -ArgumentList '-NoProfile', '-Command', 'Start-Service MySQL80'
  }
}
if (-not (Test-Tcp $MySqlPort)) {
  throw "MySQL is not listening on 127.0.0.1:$MySqlPort. Run PowerShell as Administrator and start MySQL80, then rerun this script."
}

if (-not $SkipBilld) {
  if (-not (Test-Path (Join-Path $billdRoot 'node_modules'))) { throw "Billd dependencies are missing. Run pnpm install in $billdRoot." }
  $env:NODE_ENV = 'development'
  $env:NODE_APP_RELEASE_PROJECT_NAME = 'billd-live-server'
  $env:NODE_APP_RELEASE_PROJECT_ENV = 'development'
  $env:NODE_APP_RELEASE_PROJECT_PORT = [string]$BilldPort
  $env:MYSQL_HOST = '127.0.0.1'
  $env:MYSQL_PORT = [string]$MySqlPort
  $env:MYSQL_DATABASE = $MySqlDatabase
  $env:MYSQL_USER = $MySqlUser
  $env:MYSQL_PASSWORD = $MySqlPassword
  $env:REDIS_HOST = '127.0.0.1'
  $env:REDIS_PORT = [string]$RedisPort
  $env:DB_SYNC_MODE = '2'
  $env:REVENUE_API_URL = "http://127.0.0.1:$RevenuePort"
  pnpm --dir $billdRoot dev
}
