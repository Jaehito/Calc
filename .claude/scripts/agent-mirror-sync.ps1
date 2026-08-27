# Sync the working tree -> agent verification mirror (ONE WAY).
#
# Copies only what Unity needs to reproduce the project: Assets, Packages, ProjectSettings
# (including .meta files, so GUIDs and therefore prefab/scene references survive).
# Library/Temp/Logs/UserSettings stay per-project: the mirror imports on its own.
#
# /MIR mirrors deletions too, so anything created inside the mirror is removed on the next
# sync. The mirror is disposable by design - never author anything there.
#
# Writes a stamp file on success; .claude/hooks/unity-playmode-gate.ps1 compares source
# mtimes against that stamp and blocks run_tests when the mirror is behind.

param(
    [string]$MirrorRoot = '',
    [string]$MirrorProject = '',
    [switch]$Quiet
)

$ErrorActionPreference = 'Stop'

$repoRoot = $env:CLAUDE_PROJECT_DIR
if ([string]::IsNullOrWhiteSpace($repoRoot)) { $repoRoot = (Get-Location).Path }

$configPath = Join-Path $repoRoot '.claude\agent-mirror.json'
if (Test-Path $configPath) {
    try {
        $config = (Get-Content -Path $configPath -Raw -Encoding UTF8) | ConvertFrom-Json
        if ($null -ne $config) {
            if ([string]::IsNullOrWhiteSpace($MirrorRoot) -and $config.PSObject.Properties.Name -contains 'mirrorRoot') {
                $MirrorRoot = [string]$config.mirrorRoot
            }
            if ([string]::IsNullOrWhiteSpace($MirrorProject) -and $config.PSObject.Properties.Name -contains 'mirrorProject') {
                $MirrorProject = [string]$config.mirrorProject
            }
        }
    } catch {}
}
if ([string]::IsNullOrWhiteSpace($MirrorRoot)) { $MirrorRoot = 'C:\UnityProjects\SOS-agent' }
# The mirror's Unity project folder is named differently from the source's "Dev" on purpose:
# the folder name IS the Unity project name, so both editors are told apart at a glance
# (window title, Unity Hub, and the MCP instance identifier Name@hash).
if ([string]::IsNullOrWhiteSpace($MirrorProject)) { $MirrorProject = 'sos_agent' }

$sourceDev = Join-Path $repoRoot 'Dev'
$mirrorDev = Join-Path $MirrorRoot $MirrorProject
if (-not (Test-Path $sourceDev)) {
    Write-Output ('sync-failed: source not found: ' + $sourceDev)
    exit 1
}

$folders = @('Assets', 'Packages', 'ProjectSettings')
$codes = @()
$failed = $false
$changed = $false

foreach ($folder in $folders) {
    $from = Join-Path $sourceDev $folder
    $to = Join-Path $mirrorDev $folder
    if (-not (Test-Path $from)) { continue }
    New-Item -ItemType Directory -Force -Path $to | Out-Null

    # /MIR = mirror (copy + purge). /XD excludes generated dirs that must stay per-project.
    & robocopy $from $to /MIR /XD Library Temp Logs obj .vs /XF UnityLockfile /NFL /NDL /NJH /NJS /R:2 /W:1 | Out-Null
    $code = $LASTEXITCODE
    # robocopy bit flags: 1 = files copied, 2 = extras removed, 4 = mismatched, >= 8 = real failure.
    if ($code -ge 8) {
        Write-Output ('sync-failed: robocopy exit ' + $code + ' on ' + $folder)
        $failed = $true
    }
    else {
        $codes += ($folder + '=' + $code)
        if (($code -band 7) -ne 0) { $changed = $true }
    }
}

if ($failed) { exit 1 }

$stampDir = Join-Path $env:TEMP 'claude-sos-playtest'
New-Item -ItemType Directory -Force -Path $stampDir | Out-Null
$stampPath = Join-Path $stampDir 'mirror-sync.stamp'
$now = [DateTime]::UtcNow
Set-Content -Path $stampPath -Value ($MirrorRoot + '|' + $now.ToString('o')) -Encoding Ascii
(Get-Item $stampPath).LastWriteTimeUtc = $now

if (-not $Quiet) {
    # robocopy exit codes per folder: 0 = nothing changed, 1 = copied, 2 = extras purged, 3 = both.
    if ($changed) {
        Write-Output ('sync-ok: ' + ($codes -join ' ') + '  (0 = no change). Run refresh_unity on the mirror instance next.')
    }
    else {
        Write-Output ('sync-ok-nochange: ' + ($codes -join ' ') + '  (mirror already up to date - SKIP refresh_unity, go straight to run_tests).')
    }
}

# Overwriting the scene file the agent editor has open makes Unity raise a modal
# ("The open scene(s) have been modified externally"). That modal blocks Unity's main thread,
# so imports, compilation and MCP all stop - while /api/info, /api/status, port LISTEN and the
# process "Responding" flag all keep looking healthy. Nobody sits at the agent window, so the
# dialog never closes on its own (measured 2026-08-06: 84 minutes wedged).
#
# We only WARN. Clicking the button from a script risks hitting the user's editor by mistake,
# which would discard their unsaved scene work - unrecoverable. A human presses Reload.
# Detection failure must never fail the sync, so everything below is best-effort and exits 0.
try {
    Add-Type -ErrorAction Stop -TypeDefinition @'
using System;
using System.Text;
using System.Collections.Generic;
using System.Runtime.InteropServices;
public class AgentMirrorDialogProbe {
    [DllImport("user32.dll")] static extern bool EnumWindows(EnumWindowsProc cb, IntPtr l);
    [DllImport("user32.dll")] static extern bool EnumChildWindows(IntPtr p, EnumWindowsProc cb, IntPtr l);
    [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
    [DllImport("user32.dll")] static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
    [DllImport("user32.dll")] static extern int GetClassName(IntPtr h, StringBuilder s, int n);
    [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr h);
    delegate bool EnumWindowsProc(IntPtr h, IntPtr l);
    public static List<string> Probe(uint pid) {
        List<string> found = new List<string>();
        EnumWindows(delegate(IntPtr h, IntPtr l) {
            uint owner;
            GetWindowThreadProcessId(h, out owner);
            if (owner != pid || !IsWindowVisible(h)) { return true; }
            StringBuilder cls = new StringBuilder(64);
            GetClassName(h, cls, 64);
            if (cls.ToString() != "#32770") { return true; }
            StringBuilder title = new StringBuilder(400);
            GetWindowText(h, title, 400);
            List<string> buttons = new List<string>();
            EnumChildWindows(h, delegate(IntPtr kid, IntPtr x) {
                StringBuilder kc = new StringBuilder(64);
                GetClassName(kid, kc, 64);
                if (kc.ToString() == "Button") {
                    StringBuilder kt = new StringBuilder(120);
                    GetWindowText(kid, kt, 120);
                    if (kt.Length > 0) { buttons.Add(kt.ToString()); }
                }
                return true;
            }, IntPtr.Zero);
            found.Add(title.ToString() + " || buttons: " + string.Join(" / ", buttons.ToArray()));
            return true;
        }, IntPtr.Zero);
        return found;
    }
}
'@

    # The Unity window title starts with the project folder name, and we own that name
    # ($MirrorProject) precisely so the agent editor is distinguishable from the source "Dev".
    $titlePrefix = $MirrorProject + ' - '
    $agentProcs = @(Get-Process Unity -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowTitle -and $_.MainWindowTitle.StartsWith($titlePrefix) })

    # Unity's own progress popups ("Running managed callbacks (busy for 42s)... [Cancel]") are
    # #32770 too, and they close by themselves. Reporting those as "wedged" would cry wolf on
    # every ordinary import, and a warning that fires constantly is one the operator stops
    # reading - which defeats the whole point. So classify: only a dialog that actually waits
    # for a human is reported as blocking.
    foreach ($proc in $agentProcs) {
        $dialogs = [AgentMirrorDialogProbe]::Probe([uint32]$proc.Id)
        $blocking = @()
        $transient = @()
        foreach ($dialog in $dialogs) {
            # The externally-modified-scene modal is the known wedger: title says so, and it
            # offers Reload/Ignore with no Cancel. Anything else is treated as transient.
            if ($dialog -match 'modified externally' -or ($dialog -match '\bReload\b' -and $dialog -match '\bIgnore\b')) {
                $blocking += $dialog
            }
            else {
                $transient += $dialog
            }
        }

        foreach ($dialog in $blocking) {
            Write-Output ('sync-blocked-dialog: agent Unity (pid ' + $proc.Id + ') is waiting on a modal -> ' + $dialog)
        }
        if ($blocking.Count -gt 0) {
            Write-Output 'sync-blocked-dialog: the agent editor cannot import, compile or answer MCP until a human dismisses it.'
            Write-Output 'sync-blocked-dialog: press Reload in the agent window, then verify. Do NOT wait in a polling loop.'
        }

        # Still surfaced, but plainly marked self-closing so it is not mistaken for a wedge.
        foreach ($dialog in $transient) {
            Write-Output ('sync-dialog-transient: agent Unity (pid ' + $proc.Id + ') shows a self-closing dialog (no action needed) -> ' + $dialog)
        }
    }
} catch {
    Write-Output ('sync-dialog-probe-skipped: ' + $_.Exception.Message)
}

exit 0
