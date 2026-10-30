$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$overlayRoot = Join-Path $root 'upstream-overlays'
Get-ChildItem $overlayRoot -Directory | ForEach-Object {
  $overlay = $_
  $targetRoot = Join-Path $root (Join-Path 'third_party' $overlay.Name)
  if (-not (Test-Path $targetRoot)) { throw "Missing upstream checkout: $targetRoot" }
  Get-ChildItem $overlay.FullName -Recurse -File | ForEach-Object {
    $relative = $_.FullName.Substring($overlay.FullName.Length + 1)
    $destination = Join-Path $targetRoot $relative
    New-Item -ItemType Directory -Force -Path (Split-Path $destination) | Out-Null
    Copy-Item $_.FullName $destination -Force
  }
}
Write-Host 'Upstream overlays applied.'
