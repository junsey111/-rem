$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$database = Join-Path $root '.local/mariadb-data'
$server = 'C:\Program Files\MariaDB 12.3\bin\mariadbd.exe'

if (-not (Test-Path -LiteralPath (Join-Path $database 'my.ini'))) {
  throw 'Local database is not initialized.'
}
if (-not (Get-NetTCPConnection -State Listen -LocalPort 23306 -ErrorAction SilentlyContinue)) {
  Start-Process -FilePath $server -ArgumentList "--defaults-file=$database\my.ini", '--bind-address=127.0.0.1' -WorkingDirectory $database -WindowStyle Hidden | Out-Null
  $deadline = (Get-Date).AddSeconds(15)
  while (-not (Get-NetTCPConnection -State Listen -LocalPort 23306 -ErrorAction SilentlyContinue)) {
    if ((Get-Date) -ge $deadline) { throw 'Local database did not start.' }
    Start-Sleep -Milliseconds 500
  }
}

if (-not (Get-NetTCPConnection -State Listen -LocalPort 4300 -ErrorAction SilentlyContinue)) {
  $local = Join-Path $root '.local'
  Start-Process -FilePath (Get-Command node).Source -ArgumentList 'scripts/start-auth-local.cjs' `
    -WorkingDirectory $root -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $local 'auth-local.log') `
    -RedirectStandardError (Join-Path $local 'auth-local-error.log') | Out-Null
  $deadline = (Get-Date).AddSeconds(30)
  while (-not (Get-NetTCPConnection -State Listen -LocalPort 4300 -ErrorAction SilentlyContinue)) {
    if ((Get-Date) -ge $deadline) { throw 'Local account API did not start. Check .local/auth-local-error.log.' }
    Start-Sleep -Milliseconds 500
  }
}

Write-Host 'Local account API is ready at http://127.0.0.1:4300'
