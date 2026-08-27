# PreToolUse gate for Unity MCP calls that seize an editor.
#
# Two modes, decided by .claude/agent-mirror.json:
#
#   MIRROR MODE (config present, enabled, mirror folder exists)
#     ALL run_tests (EditMode and PlayMode) run in the MIRROR editor, never the user's. Requires:
#       1. mirror editor open   (Dev/Temp/UnityLockfile exists)
#       2. mirror in sync       (no source file newer than the sync stamp)
#       3. mirror not already busy in PlayMode
#     The user's own lock is deliberately NOT consulted: they can stay in Play Mode
#     while verification runs in the mirror. That is the whole point of the mirror.
#     Scene/editor-control calls still target the user's project, so they keep waiting.
#
#   SINGLE-EDITOR MODE (no mirror yet)
#     Any held lock blocks the call; the agent waits for release.
#
# Lock file (written by PlaytestPlayModeLock, Dev/Packages/com.aftertime.playtest/Editor):
#   %TEMP%\claude-sos-playtest\playmode-{project path with non-alphanumerics -> '-'}.lock
#   { "source": "user-play" | "test-run", "pid": <int>, "project": "<path>", "heartbeatUtc": "<ISO>" }
# Heartbeat is 2s; older than StaleSeconds means the editor died -> ignore the file.
#
# Fail-open on unexpected payloads so a hook bug never blocks legitimate work.

$ErrorActionPreference = 'Stop'

$StaleSeconds = 15
$WaitHint = 'Wait for release: run  powershell -NoProfile -File ".claude/scripts/unity-playmode-wait.ps1"  ' +
    'with the PowerShell tool and run_in_background=true (exit 0 = free, exit 2 = 5-minute cap reached). ' +
    'Do NOT retry in a loop and do NOT TaskStop the other worker.'

function Deny([string]$reason) {
    $result = @{
        hookSpecificOutput = @{
            hookEventName            = 'PreToolUse'
            permissionDecision       = 'deny'
            permissionDecisionReason = $reason
        }
    }
    [Console]::Out.WriteLine(($result | ConvertTo-Json -Compress -Depth 5))
    exit 0
}

# MCP instance hash for a project, read from the bridge's own registration files.
# The session-level pin (set_active_instance) has been observed to drop after a Unity
# disconnect/retry, which silently sends agent calls to the USER's editor - so callers must
# pass unity_instance per call and this is how the gate knows the right value.
function Get-InstanceHash([string]$projectDevPath) {
    $registryDir = Join-Path $env:USERPROFILE '.unity-mcp'
    if (-not (Test-Path $registryDir)) { return '' }
    $needle = (($projectDevPath -replace '\\', '/').TrimEnd('/')).ToLowerInvariant()
    foreach ($file in (Get-ChildItem -Path $registryDir -Filter 'unity-mcp-status-*.json' -File -ErrorAction SilentlyContinue)) {
        try {
            $status = (Get-Content -Path $file.FullName -Raw -Encoding UTF8) | ConvertFrom-Json
            if ($null -eq $status -or -not ($status.PSObject.Properties.Name -contains 'project_path')) { continue }
            $projectPath = (([string]$status.project_path) -replace '\\', '/').ToLowerInvariant()
            if ($projectPath.StartsWith($needle)) {
                if ($file.BaseName -match 'unity-mcp-status-(.+)$') { return $matches[1] }
            }
        } catch {}
    }
    return ''
}

# Lock files are named playmode-{pid}.lock and carry the project path as DATA, so no reader
# re-derives a filename from a path. Identification is a normalized string compare of the
# "project" field - the one rule that used to be duplicated across C# and PowerShell.
function Get-LockFiles {
    $lockDir = Join-Path $env:TEMP 'claude-sos-playtest'
    if (-not (Test-Path $lockDir)) { return @() }
    try {
        return @(Get-ChildItem -Path $lockDir -Filter 'playmode-*.lock' -File -ErrorAction SilentlyContinue)
    } catch {
        return @()
    }
}

function Get-NormalizedPath([string]$path) {
    if ([string]::IsNullOrWhiteSpace($path)) { return '' }
    return ((($path -replace '\\', '/').TrimEnd('/')).ToLowerInvariant())
}

# Play that an AGENT started must be distinguishable from Play a human started, or the agent locks itself
# out: manage_editor play makes the lock writer record "user-play" (it only knows test-run vs not), and the
# user-play branch below then denies the agent's own stop. Measured 2026-08-12: the editor stayed in Play
# until the user pressed Stop by hand.
#
# So this hook leaves a request marker just before it lets a play call through. PlaytestPlayModeLock consumes
# it on Play entry and writes source "agent-play" instead. Only that source lets stop through.
# The marker carries the project as DATA (same rule as the lock file) - no reader derives a path from a name.
function Write-AgentPlayRequest([string]$projectPath) {
    if ([string]::IsNullOrWhiteSpace($projectPath)) { return }
    try {
        $lockDir = Join-Path $env:TEMP 'claude-sos-playtest'
        if (-not (Test-Path $lockDir)) { New-Item -ItemType Directory -Path $lockDir -Force | Out-Null }
        $payload = @{
            project     = $projectPath
            requestedUtc = [DateTime]::UtcNow.ToString('o')
        }
        $name = 'agent-play-request-' + [Guid]::NewGuid().ToString('N') + '.json'
        $target = Join-Path $lockDir $name
        [System.IO.File]::WriteAllText($target, ($payload | ConvertTo-Json -Compress), (New-Object System.Text.UTF8Encoding($false)))
    } catch {}
}

# Held locks as objects: @{ source; project }. Stale files (dead editor) are skipped.
function Get-HeldLocks {
    $held = @()
    foreach ($file in Get-LockFiles) {
        try {
            if (([DateTime]::UtcNow - $file.LastWriteTimeUtc).TotalSeconds -gt $StaleSeconds) { continue }
        } catch { continue }
        $source = 'unknown'
        $project = ''
        try {
            $json = (Get-Content -Path $file.FullName -Raw -Encoding UTF8) | ConvertFrom-Json
            if ($null -ne $json) {
                if ($json.PSObject.Properties.Name -contains 'source') {
                    $candidate = [string]$json.source
                    if (-not [string]::IsNullOrWhiteSpace($candidate)) { $source = $candidate }
                }
                if ($json.PSObject.Properties.Name -contains 'project') { $project = [string]$json.project }
            }
        } catch { continue }
        $held += @{ source = $source; project = $project }
    }
    return $held
}

# Returns the "source" of a held lock on that project, or $null when free/stale.
function Get-HeldSourceForProject([string]$projectPath) {
    $needle = Get-NormalizedPath $projectPath
    if ($needle.Length -eq 0) { return $null }
    foreach ($lock in Get-HeldLocks) {
        if ((Get-NormalizedPath $lock.project) -eq $needle) { return $lock.source }
    }
    return $null
}

try {
    try { [Console]::InputEncoding = [System.Text.Encoding]::UTF8 } catch {}
    $raw = [Console]::In.ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($raw)) { exit 0 }
    $payload = $raw | ConvertFrom-Json
} catch {
    exit 0
}

$toolName = ''
try {
    if ($payload.PSObject.Properties.Name -contains 'tool_name') { $toolName = [string]$payload.tool_name }
} catch {}
if ([string]::IsNullOrEmpty($toolName)) { exit 0 }

# agent_id is present only for subagent calls. Subagents never queue on Unity: they stop and
# hand the verification back to the orchestrator.
$isSubagent = $false
try {
    if ($payload.PSObject.Properties.Name -contains 'agent_id') {
        $isSubagent = -not [string]::IsNullOrWhiteSpace([string]$payload.agent_id)
    }
} catch {}

$action = ''
try {
    if ($payload.PSObject.Properties.Name -contains 'tool_input') {
        $toolInput = $payload.tool_input
        if ($null -ne $toolInput -and $toolInput.PSObject.Properties.Name -contains 'action') {
            $action = [string]$toolInput.action
        }
    }
} catch {}

$isRunTests = ($toolName -eq 'mcp__unityMCP__run_tests')
$isEditorControl = ($toolName -eq 'mcp__unityMCP__manage_editor' -and $action -match '^(play|pause|stop)$')
# play ACQUIRES the editor, stop RELEASES it - they cannot share one verdict. Lumping them together is what
# trapped the agent in its own Play session (see Write-AgentPlayRequest above).
$isPlayStart = ($toolName -eq 'mcp__unityMCP__manage_editor' -and $action -eq 'play')
$isStopCall = ($toolName -eq 'mcp__unityMCP__manage_editor' -and $action -eq 'stop')
$isSceneWrite = ($toolName -eq 'mcp__unityMCP__manage_scene' -and $action -match '^(load|open|create|save)')

# execute_code is the back door: arbitrary C# in the editor can do everything the calls above do.
# Reads (the diagnostics that actually find problems) stay free; only state-changing code is gated.
$isCodeWrite = $false
if ($toolName -eq 'mcp__unityMCP__execute_code') {
    $codeText = ''
    try {
        if ($payload.PSObject.Properties.Name -contains 'tool_input') {
            $ti = $payload.tool_input
            if ($null -ne $ti -and $ti.PSObject.Properties.Name -contains 'code') { $codeText = [string]$ti.code }
        }
    } catch {}
    $writeApiPattern = 'OpenScene|SaveScene|SaveOpenScenes|NewScene|isPlaying\s*=|EnterPlaymode|ExitPlaymode|' +
        'EditorApplication\.(Exit|Quit)|AssetDatabase\.(Delete|Move|Save|Create|Import)|' +
        'File\.(Delete|Write|Move|Copy|Append)|Directory\.(Delete|Move|Create)|EditorPrefs\.Set|' +
        'EditorSettings\.|PlayerSettings\.'
    if ($codeText -match $writeApiPattern) { $isCodeWrite = $true }
}

if (-not ($isRunTests -or $isEditorControl -or $isSceneWrite -or $isCodeWrite)) { exit 0 }

# ---- Subagents are excluded from verification entirely (orchestrator owns it) ----
# This is the queue fix: five agents each verifying means five runs through one mirror editor.
# NOTE: it does not stop other agents' in-flight code from reaching the mirror - the orchestrator's
# own sync copies whatever the shared tree holds at that moment. That is inherent to snapshotting a
# shared working tree; the mitigation is to verify after the batch finishes, not mid-flight.
if ($isSubagent -and $isRunTests) {
    Deny ('unity-playmode-gate: verification belongs to the orchestrator, not subagents. Do not run tests here - ' +
        'return a summary of what you changed plus a "needs verification" note and stop. The orchestrator runs ' +
        'the suite once per turn. See .claude/rules/playtest.md.')
}

$repoRoot = $env:CLAUDE_PROJECT_DIR
if ([string]::IsNullOrWhiteSpace($repoRoot)) {
    try {
        if ($payload.PSObject.Properties.Name -contains 'cwd') { $repoRoot = [string]$payload.cwd }
    } catch {}
}
if ([string]::IsNullOrWhiteSpace($repoRoot)) { exit 0 }

$mirrorRoot = ''
$mirrorProject = 'sos_agent'
$mirrorEnabled = $false
try {
    $configPath = Join-Path $repoRoot '.claude\agent-mirror.json'
    if (Test-Path $configPath) {
        $config = (Get-Content -Path $configPath -Raw -Encoding UTF8) | ConvertFrom-Json
        if ($null -ne $config) {
            if ($config.PSObject.Properties.Name -contains 'mirrorRoot') { $mirrorRoot = [string]$config.mirrorRoot }
            if ($config.PSObject.Properties.Name -contains 'mirrorProject') {
                $candidateName = [string]$config.mirrorProject
                if (-not [string]::IsNullOrWhiteSpace($candidateName)) { $mirrorProject = $candidateName }
            }
            if ($config.PSObject.Properties.Name -contains 'enabled') { $mirrorEnabled = [bool]$config.enabled }
        }
    }
} catch {}

$mirrorDev = ''
if ($mirrorEnabled -and -not [string]::IsNullOrWhiteSpace($mirrorRoot)) {
    $candidate = Join-Path $mirrorRoot $mirrorProject
    if (Test-Path $candidate) { $mirrorDev = $candidate }
}

# ---- run_tests: declare the target, but the mirror is NO LONGER forced ----
# Policy: verification may run in EITHER the mirror (web panel / scenario flow) OR the user's project
# (a separate, faster DIRECT PlayMode/EditMode flow that does NOT go through the web panel). So run_tests
# is not forced to the mirror here - it only has to DECLARE its target instance, which the shared TARGETED
# block below enforces (run_tests is included in its condition). Sync freshness is no longer hard-gated:
# it is a soft pre-check the orchestrator runs before a mirror sync (see .claude/rules/playtest.md).
# The web-panel flow (POST /api/run, /api/run-scenario) stays mirror-only by convention, not by this hook.

# ---- TARGETED WRITES: name the editor, or don't write ----
# Scene loads/saves, play/pause/stop and state-changing execute_code all land wherever routing points.
# Routing is decided by the set_active_instance pin; per-call unity_instance can be ignored (observed),
# and the pin itself can be stale after a reconnect. So this check does NOT guarantee where the call
# lands - it forces the caller to state which editor it meant, and blocks calls that never considered
# it. Actual routing must be confirmed with Application.dataPath (see .claude/rules/playtest.md).
if (-not [string]::IsNullOrWhiteSpace($mirrorDev) -and ($isRunTests -or $isEditorControl -or $isSceneWrite -or $isCodeWrite)) {
    $mirrorHash = Get-InstanceHash $mirrorDev
    $sourceHash = Get-InstanceHash (Join-Path $repoRoot 'Dev')

    if (-not [string]::IsNullOrWhiteSpace($mirrorHash) -and -not [string]::IsNullOrWhiteSpace($sourceHash)) {
        $instanceArg = ''
        try {
            if ($payload.PSObject.Properties.Name -contains 'tool_input') {
                $ti2 = $payload.tool_input
                if ($null -ne $ti2 -and $ti2.PSObject.Properties.Name -contains 'unity_instance') {
                    $instanceArg = ([string]$ti2.unity_instance).ToLowerInvariant()
                }
            }
        } catch {}

        $targetsMirror = ($instanceArg.Length -gt 0 -and $instanceArg.IndexOf($mirrorHash.ToLowerInvariant()) -ge 0)
        $targetsSource = ($instanceArg.Length -gt 0 -and $instanceArg.IndexOf($sourceHash.ToLowerInvariant()) -ge 0)

        if (-not $targetsMirror -and -not $targetsSource) {
            if ($isRunTests) {
                Deny ('unity-playmode-gate: run_tests must name its target instance (the mirror is no longer forced). ' +
                    'Pass unity_instance="' + $sourceHash + '" for the user''s project (direct PlayMode/EditMode flow, ' +
                    'no web panel) or "' + $mirrorHash + '" for the verification mirror (web panel / scenario flow). ' +
                    'per-call unity_instance can be IGNORED while the set_active_instance pin wins, so set the pin and ' +
                    'confirm with execute_code returning Application.dataPath BEFORE trusting the run. See .claude/rules/playtest.md.')
            }
            Deny ('unity-playmode-gate: this call changes editor state, so it must name its target instance. ' +
                'Pass unity_instance="' + $sourceHash + '" for the user''s project (scene/SO authoring) or "' +
                $mirrorHash + '" for the verification mirror. Without it the call follows the session pin, which ' +
                'drops silently after a Unity reconnect and would hit the user''s editor. See .claude/rules/playtest.md.')
        }

        if ($targetsMirror) {
            if ($isSceneWrite -and $action -match '^(save|create)') {
                Deny ('unity-playmode-gate: do not author in the mirror - the next sync overwrites it and the work ' +
                    'is lost. Scene/SO edits belong in the user''s project (unity_instance="' + $sourceHash + '").')
            }
            $mirrorBusy = Get-HeldSourceForProject $mirrorDev
            # An agent-play hold is the agent's own Play session - it must stay releasable by the agent.
            if ($null -ne $mirrorBusy -and -not ($isStopCall -and $mirrorBusy -eq 'agent-play')) {
                if ($isSubagent) {
                    Deny ('unity-playmode-gate: the mirror is busy with a verification run. Do not wait - stop and ' +
                        'report to the orchestrator.')
                }
                Deny ('unity-playmode-gate: the mirror is busy with a verification run (clears in seconds). ' + $WaitHint)
            }
			if ($isPlayStart) { Write-AgentPlayRequest $mirrorDev }
            exit 0
        }

        # targets the user's project
        if ($isRunTests) {
            # run_tests on the user's editor is allowed (the direct flow), but it must never interrupt a user
            # Play session, and it blocks only on the SOURCE's own lock - not the mirror's (a mirror run is
            # unrelated to a run aimed at the user's project).
            $sourceHeld = Get-HeldSourceForProject (Join-Path $repoRoot 'Dev')
            if ($null -ne $sourceHeld) {
                if ($sourceHeld -eq 'user-play') {
                    Deny ('unity-playmode-gate: the USER is in Play Mode in their project. Do NOT wait and do NOT ' +
                        'interrupt it - report "verification deferred - editor occupied" so the orchestrator batches ' +
                        'it later. See .claude/rules/playtest.md.')
                }
                Deny ('unity-playmode-gate: a test run is already in progress in the user''s project, which clears ' +
                    'in seconds. ' + $WaitHint)
            }
            exit 0
        }
        # non-run_tests targeting the user's project -> fall through to the lock policy below
    }
}

# ---- SINGLE-EDITOR MODE (and all calls aimed at the user's project): any held lock blocks ----
# Waiting is only worth it for a short, self-clearing hold. A user's Play session can last
# half an hour, and an idling agent burns context, holds its busy marker (which defers the
# verification gate for everyone) and resumes with stale state. So: wait for test runs,
# bail out immediately for user-play - and subagents never wait at all.
foreach ($lock in (Get-HeldLocks)) {
	# The agent's own Play session must be releasable by the agent - that is what agent-play is for.
    # stop only: play/pause against a held editor still bails out or queues exactly as before.
    if ($isStopCall -and $lock.source -eq 'agent-play') { continue }
	
    $where = $lock.project
    if ([string]::IsNullOrWhiteSpace($where)) { $where = 'unknown project' }

    if ($lock.source -eq 'user-play' -or $isSubagent) {
        $who = 'The USER is in Play Mode'
        if ($lock.source -eq 'test-run') { $who = 'Another test run is in progress' }
        Deny ('unity-playmode-gate: ' + $who + ' in ' + $where + '. Do NOT wait and do NOT retry - ' +
            'an idling agent costs context and delays everyone else''s verification. Stop this attempt now, ' +
            'finish the work that does not need Unity, and report "verification deferred - editor occupied" ' +
            'so the orchestrator batches it later. See .claude/rules/playtest.md.')
    }

    Deny ('unity-playmode-gate: a test run is in progress in ' + $where + ', which clears in seconds. ' +
        $WaitHint + ' On timeout, report "verification not run" instead of forcing the call. ' +
        'See .claude/rules/playtest.md.')
}

if ($isPlayStart) { Write-AgentPlayRequest (Join-Path $repoRoot 'Dev') }

exit 0
