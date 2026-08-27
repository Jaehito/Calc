# One-time setup for the agent verification mirror (Unity B).
#
# Creates the mirror project from the working tree and writes .claude/agent-mirror.json,
# which is what switches the PlayMode gate into mirror mode. After this runs, YOU still
# have to open the mirror once in Unity Hub so it builds its own Library.
#
# The mirror is disposable: delete the folder and re-run this to rebuild it.

param(
    [string]$MirrorRoot = 'C:\UnityProjects\SOS-agent',
    [string]$MirrorProject = 'sos_agent',
    [switch]$Disable
)

$ErrorActionPreference = 'Stop'

$repoRoot = $env:CLAUDE_PROJECT_DIR
if ([string]::IsNullOrWhiteSpace($repoRoot)) { $repoRoot = (Get-Location).Path }
$configPath = Join-Path $repoRoot '.claude\agent-mirror.json'

if ($Disable) {
    if (Test-Path $configPath) {
        $existing = (Get-Content -Path $configPath -Raw -Encoding UTF8) | ConvertFrom-Json
        $keepProject = 'sos_agent'
        if ($existing.PSObject.Properties.Name -contains 'mirrorProject') {
            $candidate = [string]$existing.mirrorProject
            if (-not [string]::IsNullOrWhiteSpace($candidate)) { $keepProject = $candidate }
        }
        $off = [ordered]@{
            mirrorRoot    = [string]$existing.mirrorRoot
            mirrorProject = $keepProject
            enabled       = $false
        }
        Set-Content -Path $configPath -Value ($off | ConvertTo-Json) -Encoding UTF8
        Write-Output 'mirror-disabled: the gate falls back to single-editor waiting.'
    }
    else {
        Write-Output 'mirror-disabled: no config present, nothing to do.'
    }
    exit 0
}

$sourceDev = Join-Path $repoRoot 'Dev'
if (-not (Test-Path $sourceDev)) {
    Write-Output ('setup-failed: source not found: ' + $sourceDev)
    exit 1
}

$drive = (Split-Path -Qualifier $MirrorRoot)
$free = $null
try { $free = (Get-PSDrive ($drive.TrimEnd(':'))).Free / 1GB } catch {}
if ($null -ne $free -and $free -lt 12) {
    Write-Output ('setup-failed: only {0:N1} GB free on {1} - the mirror needs ~6 GB plus headroom.' -f $free, $drive)
    exit 1
}

$mirrorDev = Join-Path $MirrorRoot $MirrorProject
New-Item -ItemType Directory -Force -Path $mirrorDev | Out-Null

Write-Output ('copying Assets/Packages/ProjectSettings -> ' + $mirrorDev + ' (first run takes a while)')
& powershell -NoProfile -File (Join-Path $repoRoot '.claude\scripts\agent-mirror-sync.ps1') -MirrorRoot $MirrorRoot -MirrorProject $MirrorProject
if ($LASTEXITCODE -ne 0) {
    Write-Output 'setup-failed: initial sync failed. Config not written.'
    exit 1
}

$config = [ordered]@{
    mirrorRoot    = $MirrorRoot
    mirrorProject = $MirrorProject
    enabled       = $true
}
Set-Content -Path $configPath -Value ($config | ConvertTo-Json) -Encoding UTF8

Write-Output ''
Write-Output 'setup-ok. Remaining manual step (once):'
Write-Output ('  1. Unity Hub -> Add project from disk -> ' + $mirrorDev)
Write-Output '  2. Open it. The first import takes 10-20 min and builds its own Library (~6 GB).'
Write-Output '  3. Leave that editor window open. It is the agent''s window from now on.'
Write-Output ''
Write-Output 'To turn the mirror off again: agent-mirror-setup.ps1 -Disable'
exit 0
