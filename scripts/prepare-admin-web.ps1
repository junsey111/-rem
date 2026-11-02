$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$webRoot = Join-Path $projectRoot 'third_party/billd-live'
$entry = Get-Content (Join-Path $projectRoot 'UPSTREAM.lock') | Where-Object { $_.StartsWith('billd-live=') }
if (-not $entry -or $entry -notmatch '^billd-live=(.+)@([0-9a-f]{40})$') { throw 'Missing pinned billd-live revision' }
$sourceUrl = $Matches[1]
$revision = $Matches[2]
if (-not (Test-Path -LiteralPath $webRoot)) {
  git clone $sourceUrl $webRoot
  if ($LASTEXITCODE) { throw 'Web upstream clone failed' }
  git -C $webRoot checkout --detach $revision
  if ($LASTEXITCODE) { throw 'Web upstream checkout failed' }
} else {
  $actual = git -C $webRoot rev-parse HEAD
  if ($LASTEXITCODE -or $actual -ne $revision) { throw 'Existing Web checkout is at another revision; preserve its work and review manually' }
}
$overlayRoot = Join-Path $projectRoot 'upstream-overlays/billd-live'
Get-ChildItem -LiteralPath $overlayRoot -Recurse -File | ForEach-Object {
  $relative = $_.FullName.Substring($overlayRoot.Length + 1)
  $destination = Join-Path $webRoot $relative
  New-Item -ItemType Directory -Force -Path (Split-Path $destination) | Out-Null
  Copy-Item -LiteralPath $_.FullName -Destination $destination -Force
}
Push-Location $webRoot
try {
  pnpm install --frozen-lockfile
  if ($LASTEXITCODE) { throw 'Web dependency installation failed' }
} finally { Pop-Location }
