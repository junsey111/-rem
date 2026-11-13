$ErrorActionPreference = 'Stop'
$node = (Get-Command node -ErrorAction Stop).Source
$projectRoot = Split-Path -Parent $PSScriptRoot
$worker = Join-Path $PSScriptRoot 'dev-auto-update.cjs'
$action = New-ScheduledTaskAction -Execute $node -Argument "`"$worker`"" -WorkingDirectory $projectRoot
$trigger = New-ScheduledTaskTrigger -AtLogOn -User "$env:USERDOMAIN\$env:USERNAME"
$settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit (New-TimeSpan -Seconds 0)
Register-ScheduledTask -TaskName 'LiveRoomDevAutoUpdate' -Action $action -Trigger $trigger `
    -Settings $settings -Description 'Build and publish local Android app updates after source changes' -Force
