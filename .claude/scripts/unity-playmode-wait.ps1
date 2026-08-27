# Wait until Unity PlayMode is released, then exit. Run in the BACKGROUND (PowerShell tool,
# run_in_background=true) so the completion notification is the "editor is free now" signal.
#
# Lock contract: see .claude/hooks/unity-playmode-gate.ps1 and
# Dev/Packages/com.aftertime.playtest/Editor/PlaytestPlayModeLock.cs
# With two editors (working tree + agent mirror) there is one lock file per project;
# pass -Project to watch a single one instead of all of them.
#
# Use this ONLY for a `test-run` hold (another verification, clears in seconds). A `user-play`
# hold can last half an hour: bail out immediately instead and let the orchestrator batch later.
# See the wait-policy table in .claude/rules/playtest.md.
#
# Exit codes:
#   0 -> free (no held lock). Re-check and run the verification.
#   2 -> cap reached while still held. Do NOT force the run; report "verification not run".

param(
    [string]$Project = '',
    [int]$TimeoutMinutes = 5,
    [int]$PollSeconds = 5,
    [int]$StaleSeconds = 15
)

$ErrorActionPreference = 'Stop'

$lockDir = Join-Path $env:TEMP 'claude-sos-playtest'

# Lock files are playmode-{pid}.lock and identify their editor by the "project" field inside,
# so nothing here re-derives a filename from a path (that rule used to live in three languages).
function Get-NormalizedPath([string]$path) {
    if ([string]::IsNullOrWhiteSpace($path)) { return '' }
    return ((($path -replace '\\', '/').TrimEnd('/')).ToLowerInvariant())
}

# Returns "project (source)" for the first held lock that matches the filter, or $null when free.
function Get-HeldDescription {
    if (-not (Test-Path $lockDir)) { return $null }
    $needle = Get-NormalizedPath $Project
    $files = @()
    try {
        $files = @(Get-ChildItem -Path $lockDir -Filter 'playmode-*.lock' -File -ErrorAction SilentlyContinue)
    } catch {
        return $null
    }
    foreach ($lock in $files) {
        try {
            if (([DateTime]::UtcNow - $lock.LastWriteTimeUtc).TotalSeconds -gt $StaleSeconds) { continue }
        } catch { continue }
        $source = 'unknown'
        $project = ''
        try {
            $json = (Get-Content -Path $lock.FullName -Raw -Encoding UTF8) | ConvertFrom-Json
            if ($null -ne $json) {
                if ($json.PSObject.Properties.Name -contains 'source') {
                    $candidate = [string]$json.source
                    if (-not [string]::IsNullOrWhiteSpace($candidate)) { $source = $candidate }
                }
                if ($json.PSObject.Properties.Name -contains 'project') { $project = [string]$json.project }
            }
        } catch { continue }
        if ($needle.Length -gt 0 -and (Get-NormalizedPath $project) -ne $needle) { continue }
        $shown = $project
        if ([string]::IsNullOrWhiteSpace($shown)) { $shown = $lock.Name }
        return ($shown + ' (' + $source + ')')
    }
    return $null
}

$initial = Get-HeldDescription
if ($null -eq $initial) {
    Write-Output 'unity-free: PlayMode is not held. Proceed.'
    exit 0
}

Write-Output ('unity-busy: PlayMode held by ' + $initial + '. Waiting up to ' + $TimeoutMinutes + ' min.')

$deadline = [DateTime]::UtcNow.AddMinutes($TimeoutMinutes)
while ([DateTime]::UtcNow -lt $deadline) {
    Start-Sleep -Seconds $PollSeconds
    if ($null -eq (Get-HeldDescription)) {
        Write-Output 'unity-free: PlayMode released. Re-check the lock, then run the verification.'
        exit 0
    }
}

$last = Get-HeldDescription
if ($null -eq $last) {
    Write-Output 'unity-free: PlayMode released. Re-check the lock, then run the verification.'
    exit 0
}

Write-Output ('unity-busy-timeout: still held by ' + $last + ' after ' + $TimeoutMinutes +
    ' min. Report "verification not run" to the user; do not force the call.')
exit 2
