param([switch]$IncludeSmoke)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$source = Join-Path $root 'third_party\openim-server'
$output = Join-Path $root '.tools\openim-linux'
New-Item -ItemType Directory -Force $output | Out-Null
$previousGoOs = $env:GOOS
$previousGoArch = $env:GOARCH
$previousCgo = $env:CGO_ENABLED
Push-Location $source
try {
  $env:GOOS = 'linux'
  $env:GOARCH = 'amd64'
  $env:CGO_ENABLED = '0'
  $targets = @(Get-ChildItem cmd/openim-rpc -Directory | ForEach-Object { './cmd/openim-rpc/' + $_.Name })
  $targets += @('./cmd/openim-api', './cmd/openim-msggateway', './cmd/openim-push', './cmd/openim-msgtransfer', './cmd/openim-crontask')
  if ($IncludeSmoke) { $targets += './cmd/platform-im-smoke' }
  foreach ($target in $targets) {
    $name = Split-Path $target -Leaf
    & go build -p 2 -trimpath -ldflags '-s -w' -o (Join-Path $output $name) $target
    if ($LASTEXITCODE -ne 0) { throw "OpenIM build failed: $name" }
    Write-Host "Built $name"
  }
} finally {
  Pop-Location
  $env:GOOS = $previousGoOs
  $env:GOARCH = $previousGoArch
  $env:CGO_ENABLED = $previousCgo
}
