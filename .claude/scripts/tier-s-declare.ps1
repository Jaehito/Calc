# Declare a Tier-S batch: up to 3 pure C# files under Dev/** that the MAIN agent may edit
# directly. main-edit-gate.ps1 reads this marker and lets those exact paths through.
#
# Scene/SO/prefab/UI/Input/PlayMode-test paths can NOT be declared - those always go through
# the implementer. The hook re-validates every rule below, so a hand-written marker still
# cannot unlock a forbidden path; this script exists to fail fast with a readable message.
#
# Usage:
#   powershell -NoProfile -File .claude/scripts/tier-s-declare.ps1 -Files "Dev/Assets/Scripts/Battle/Bo/DeckBo.cs"
#   powershell -NoProfile -File .claude/scripts/tier-s-declare.ps1 -Clear
#
# The marker expires 30 minutes after declaration (enforced by the hook). Re-declaring
# overwrites the previous batch.

param(
    [string[]]$Files = @(),
    [switch]$Clear
)

$ErrorActionPreference = 'Stop'

$markerDir = Join-Path $env:TEMP 'claude-sos-playtest'
$markerPath = Join-Path $markerDir 'tier-s.json'

if ($Clear) {
    if (Test-Path $markerPath) { Remove-Item $markerPath -Force -Confirm:$false }
    Write-Output 'tier-s-cleared'
    exit 0
}

# powershell -File does not split "a,b,c" into a [string[]] - each element may itself carry
# commas, so flatten before counting or the cap check counts batches instead of files.
$flatFiles = @()
foreach ($entry in $Files) {
    foreach ($piece in (([string]$entry) -split ',')) {
        $trimmed = $piece.Trim()
        if ($trimmed.Length -gt 0) { $flatFiles += $trimmed }
    }
}
$Files = $flatFiles

if ($Files.Count -eq 0) {
    Write-Output 'tier-s-error: no files given. Pass -Files with 1..3 Dev/** .cs paths.'
    exit 1
}
if ($Files.Count -gt 3) {
    Write-Output ('tier-s-error: ' + $Files.Count + ' files declared - Tier S allows at most 3. Use the implementer flow instead.')
    exit 1
}

# Mirrors playtest-gate.ps1 $playPathPatterns plus asset extensions: anything a PlayMode run
# (or the implementer flow) must own stays out of Tier S.
$deniedPatterns = @(
    '(?i)\.(unity|prefab|asset)$',
    '(?i)^Dev/Assets/Scripts/.*/UI/',
    '(?i)^Dev/Assets/Scripts/.*(View|Layout|Overlay|Panel|Hud|Popup|Toast)\.cs$',
    '(?i)^Dev/Assets/Tests/PlayMode/',
    '(?i)^Dev/Assets/Scripts/.*Input.*\.cs$'
)

$normalizedFiles = @()
foreach ($file in $Files) {
    $normalized = (([string]$file) -replace '\\', '/').TrimStart('/')
    if ($normalized -notmatch '^Dev/') {
        Write-Output ('tier-s-error: not under Dev/**: ' + $normalized)
        exit 1
    }
    if ($normalized -notmatch '(?i)\.cs$') {
        Write-Output ('tier-s-error: Tier S covers pure C# only, not: ' + $normalized)
        exit 1
    }
    foreach ($pattern in $deniedPatterns) {
        if ($normalized -match $pattern) {
            Write-Output ('tier-s-error: PlayMode-relevant path cannot be Tier S (delegate to implementer): ' + $normalized)
            exit 1
        }
    }
    $normalizedFiles += $normalized
}

New-Item -ItemType Directory -Force -Path $markerDir | Out-Null
$payload = @{
    declaredUtc = [DateTime]::UtcNow.ToString('o')
    files       = $normalizedFiles
}
Set-Content -Path $markerPath -Value ($payload | ConvertTo-Json -Compress) -Encoding UTF8
(Get-Item $markerPath).LastWriteTimeUtc = [DateTime]::UtcNow

Write-Output ('tier-s-declared: ' + ($normalizedFiles -join ', ') + '  (expires in 30 min)')
exit 0
