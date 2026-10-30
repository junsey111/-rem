$ErrorActionPreference = "Stop"

if (-not (Get-Command go -ErrorAction SilentlyContinue)) {
    throw "Go is required to build the OpenIM Android AAR."
}

go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest
$goBin = Join-Path (go env GOPATH) "bin"
$gomobile = Join-Path $goBin "gomobile.exe"
if (-not (Test-Path $gomobile)) { throw "gomobile was not installed at $gomobile" }

& $gomobile init
Push-Location (Join-Path $PSScriptRoot "..\third_party\openim-sdk-core")
try {
    & $gomobile bind -androidapi 26 -v -trimpath -ldflags="-s -w" `
        -o ./open_im_sdk.aar -target=android ./open_im_sdk/ ./open_im_sdk_callback/
    $libs = Join-Path $PSScriptRoot "..\app\libs"
    New-Item -ItemType Directory -Force -Path $libs | Out-Null
    Copy-Item ./open_im_sdk.aar (Join-Path $libs "open_im_sdk.aar") -Force
} finally {
    Pop-Location
}
