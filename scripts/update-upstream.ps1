$ErrorActionPreference = "Stop"
$repos = @(
    "livekit-client-android",
    "livekit-server",
    "srs",
    "openim-sdk-core",
    "openim-server",
    "ZLMediaKit",
    "billd-live-server",
    "billd-live-kotlin",
    "billd-live-flutter",
    "live-revenue-integrity-lab"
)
foreach ($repo in $repos) {
    git -C (Join-Path $PSScriptRoot "..\third_party\$repo") pull --ff-only
}
