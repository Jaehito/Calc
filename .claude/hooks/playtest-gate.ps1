# Verification gate: route Dev/** edits to the cheapest verification that can actually catch the
# regression, and make the MAIN agent close the loop before the turn ends.
#
#   PlayMode-relevant edit (scene, prefab, UI/View/Layout code, input, PlayMode tests)
#       -> ask the user about the play test (Korean phrase required), then run it once.
#   Any other Dev/** edit (Model, Rule, FSM, Run, importers, data...)
#       -> EditMode tests are enough (Assembly-CSharp-Editor, no Play Mode, no scene takeover).
#          Satisfied by a run_tests call or by asking the user.
#
# Single script, five modes (-Mode):
#   edit-marker : PostToolUse Edit/Write/MultiEdit/NotebookEdit -> classify the path, touch the play or code marker.
#                 Subagent edits (payload has agent_id) also refresh that agent's "busy" marker.
#   ask-marker  : PostToolUse AskUserQuestion -> if the question text contains "play test" (Korean), touch the ask marker.
#   run-marker  : PostToolUse mcp__unityMCP__run_tests -> touch the run marker for the test mode that ran.
#   agent-stop  : SubagentStop -> clear that agent's busy marker.
#   stop-gate   : Stop -> block once if a requirement is unmet; always pass when stop_hook_active=true.
#                 Defers (passes without clearing) while any subagent is still busy, so the question is asked
#                 once the background work is finished instead of on every turn mid-implementation.
#
# Markers live in %TEMP%\claude-sos-playtest\{session_id}.* (fixed project key fallback when session_id is absent).
# Busy markers are {session_id}.agent-{agent_id}.active and expire after $AgentBusyStaleMinutes so a missed
# SubagentStop can never disable the gate permanently.
# Fail-open on unexpected payloads so a hook bug never blocks legitimate work.

param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('edit-marker', 'ask-marker', 'run-marker', 'agent-stop', 'stop-gate')]
    [string]$Mode
)

$ErrorActionPreference = 'Stop'

# A busy marker older than this is treated as abandoned (agent crashed / SubagentStop never fired).
$AgentBusyStaleMinutes = 60

# Korean phrase needle built from code points so this file stays encoding-proof under PS 5.1:
# U+D50C U+B808 U+C774 (space) U+D14C U+C2A4 U+D2B8  => "play test" in Korean.
$koreanPlaytestNeedle = -join @([char]0xD50C, [char]0xB808, [char]0xC774, [char]0x0020, [char]0xD14C, [char]0xC2A4, [char]0xD2B8)

# Paths where only a real Play Mode run can catch the regression. Everything else under Dev/**
# is covered by the EditMode suite.
$playPathPatterns = @(
    '(?i)\.unity$',
    '(?i)\.prefab$',
    '(?i)^Dev/Assets/Scripts/.*/UI/',
    '(?i)^Dev/Assets/Scripts/.*(View|Layout|Overlay|Panel|Hud|Popup|Toast)\.cs$',
    '(?i)^Dev/Assets/Tests/PlayMode/',
    '(?i)^Dev/Assets/Scripts/.*Input.*\.cs$'
)

# Verification tooling (not game code) — excluded from the gate: no test suite covers it.
$toolPathPatterns = @(
    '(?i)^Dev/Packages/com\.aftertime\.playtest/'
)

try {
    try { [Console]::InputEncoding = [System.Text.Encoding]::UTF8 } catch {}
    $raw = [Console]::In.ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($raw)) { exit 0 }
    $payload = $raw | ConvertFrom-Json
} catch {
    exit 0
}

# Resolve the marker key: session_id when present, otherwise a fixed project key.
$sessionKey = 'sos-project'
try {
    if ($payload.PSObject.Properties.Name -contains 'session_id') {
        $candidate = [string]$payload.session_id
        if (-not [string]::IsNullOrWhiteSpace($candidate)) {
            # Keep it filesystem-safe.
            $sessionKey = ($candidate -replace '[^0-9A-Za-z\-_\.]', '_')
        }
    }
} catch {}

$markerDir = Join-Path $env:TEMP 'claude-sos-playtest'
$playEditMarker = Join-Path $markerDir ($sessionKey + '.dev-edit-play')
$codeEditMarker = Join-Path $markerDir ($sessionKey + '.dev-edit-code')
$askMarker = Join-Path $markerDir ($sessionKey + '.ask')
$runPlayMarker = Join-Path $markerDir ($sessionKey + '.run-play')
$runEditMarker = Join-Path $markerDir ($sessionKey + '.run-edit')

function Touch-Marker([string]$path) {
    try {
        if (-not (Test-Path $markerDir)) {
            New-Item -ItemType Directory -Path $markerDir -Force | Out-Null
        }
        $now = [DateTime]::UtcNow
        Set-Content -Path $path -Value $now.ToString('o') -Encoding Ascii
        (Get-Item $path).LastWriteTimeUtc = $now
    } catch {}
}

function Get-MarkerTime([string]$path) {
    if (-not (Test-Path $path)) { return $null }
    try { return (Get-Item $path).LastWriteTimeUtc } catch { return $null }
}

function Remove-Markers {
    foreach ($path in @($playEditMarker, $codeEditMarker, $askMarker, $runPlayMarker, $runEditMarker)) {
        try { if (Test-Path $path) { Remove-Item $path -Force -Confirm:$false } } catch {}
    }
}

# Subagent id from the payload; empty string when the call came from the main agent.
function Get-AgentId {
    try {
        if ($payload.PSObject.Properties.Name -contains 'agent_id') {
            $candidate = [string]$payload.agent_id
            if (-not [string]::IsNullOrWhiteSpace($candidate)) {
                return ($candidate -replace '[^0-9A-Za-z\-_\.]', '_')
            }
        }
    } catch {}
    return ''
}

function Get-AgentBusyMarker([string]$agentId) {
    return (Join-Path $markerDir ($sessionKey + '.agent-' + $agentId + '.active'))
}

# True when at least one subagent of this session is still working. Prunes abandoned markers on the way.
function Test-AnyAgentBusy {
    try {
        if (-not (Test-Path $markerDir)) { return $false }
        $cutoff = [DateTime]::UtcNow.AddMinutes(-$AgentBusyStaleMinutes)
        $busy = $false
        foreach ($item in (Get-ChildItem -Path $markerDir -Filter ($sessionKey + '.agent-*.active') -File -ErrorAction SilentlyContinue)) {
            if ($item.LastWriteTimeUtc -lt $cutoff) {
                try { Remove-Item $item.FullName -Force -Confirm:$false } catch {}
            }
            else {
                $busy = $true
            }
        }
        return $busy
    } catch {
        return $false
    }
}

if ($Mode -eq 'agent-stop') {
    $agentId = Get-AgentId
    if ([string]::IsNullOrEmpty($agentId)) { exit 0 }
    try {
        $busyMarker = Get-AgentBusyMarker $agentId
        if (Test-Path $busyMarker) { Remove-Item $busyMarker -Force -Confirm:$false }
    } catch {}
    exit 0
}

if ($Mode -eq 'edit-marker') {
    # A subagent is editing files -> it is alive; refresh its busy marker (any path, not just Dev/**).
    $agentId = Get-AgentId
    if (-not [string]::IsNullOrEmpty($agentId)) {
        Touch-Marker (Get-AgentBusyMarker $agentId)
    }

    # Unity MCP script tools edit Dev/** without going through Edit/Write, so they must
    # leave the same verification markers. Their payloads carry the target under different
    # keys, and when no path can be recovered the conservative fallback is the code marker.
    $toolName = ''
    try {
        if ($payload.PSObject.Properties.Name -contains 'tool_name') { $toolName = [string]$payload.tool_name }
    } catch {}
    $isMcpScriptTool = ($toolName -match '^mcp__unityMCP__(manage_script|create_script|delete_script|script_apply_edits|apply_text_edits)$')

    # Extract target file path from tool_input (Edit/Write/MultiEdit: file_path, NotebookEdit: notebook_path)
    $filePath = $null
    $toolInput = $null
    $pathKeys = @('file_path', 'notebook_path')
    if ($isMcpScriptTool) { $pathKeys = @('file_path', 'path', 'uri', 'script_path') }
    try {
        if ($payload.PSObject.Properties.Name -contains 'tool_input') { $toolInput = $payload.tool_input }
        if ($null -ne $toolInput) {
            foreach ($key in $pathKeys) {
                if ($toolInput.PSObject.Properties.Name -contains $key) {
                    $candidate = [string]$toolInput.$key
                    if (-not [string]::IsNullOrEmpty($candidate)) { $filePath = $candidate; break }
                }
            }
        }
    } catch { exit 0 }
    if ([string]::IsNullOrEmpty($filePath)) {
        if ($isMcpScriptTool) { Touch-Marker $codeEditMarker }
        exit 0
    }

    # Normalize to a project-relative path (same pattern as main-edit-gate.ps1)
    $root = $env:CLAUDE_PROJECT_DIR
    if ([string]::IsNullOrEmpty($root) -and $payload.PSObject.Properties.Name -contains 'cwd') {
        $root = [string]$payload.cwd
    }
    $normalized = $filePath -replace '\\', '/'
    if ($normalized.StartsWith('unity://path/', [System.StringComparison]::OrdinalIgnoreCase)) {
        $normalized = $normalized.Substring('unity://path/'.Length)
    }
    if (-not [string]::IsNullOrEmpty($root)) {
        $rootNorm = (($root -replace '\\', '/').TrimEnd('/')) + '/'
        if ($normalized.StartsWith($rootNorm, [System.StringComparison]::OrdinalIgnoreCase)) {
            $normalized = $normalized.Substring($rootNorm.Length)
        }
    }
    # MCP paths are relative to the Unity project (Assets/..., Packages/...), which lives in Dev/.
    if ($isMcpScriptTool -and $normalized -match '^(Assets|Packages)/') {
        $normalized = 'Dev/' + $normalized
    }

    if ($normalized -notmatch '^Dev/') {
        if ($isMcpScriptTool) { Touch-Marker $codeEditMarker }
        exit 0
    }

    # Verification tooling is not game code: it has no test suite of its own, so demanding an
    # EditMode run here would block every turn with nothing meaningful to execute. The tool is
    # verified directly (panel API reads + browser DOM + compile check) - see .claude/rules/playtest.md.
    foreach ($pattern in $toolPathPatterns) {
        if ($normalized -match $pattern) { exit 0 }
    }

    $isPlayPath = $false
    foreach ($pattern in $playPathPatterns) {
        if ($normalized -match $pattern) { $isPlayPath = $true; break }
    }
    if ($isPlayPath) { Touch-Marker $playEditMarker } else { Touch-Marker $codeEditMarker }
    exit 0
}

if ($Mode -eq 'ask-marker') {
    $toolInputJson = ''
    try {
        if ($payload.PSObject.Properties.Name -contains 'tool_input') {
            $toolInputJson = ($payload.tool_input | ConvertTo-Json -Compress -Depth 10)
        }
    } catch { exit 0 }
    if ([string]::IsNullOrEmpty($toolInputJson)) { exit 0 }

    $hasKorean = $toolInputJson.IndexOf($koreanPlaytestNeedle, [System.StringComparison]::Ordinal) -ge 0
    $hasAscii = $toolInputJson.IndexOf('playtest', [System.StringComparison]::OrdinalIgnoreCase) -ge 0 `
        -or $toolInputJson.IndexOf('play test', [System.StringComparison]::OrdinalIgnoreCase) -ge 0
    if ($hasKorean -or $hasAscii) {
        Touch-Marker $askMarker
    }
    exit 0
}

if ($Mode -eq 'run-marker') {
    $toolInputJson = ''
    try {
        if ($payload.PSObject.Properties.Name -contains 'tool_input') {
            $toolInputJson = ($payload.tool_input | ConvertTo-Json -Compress -Depth 10)
        }
    } catch { exit 0 }

    $isPlay = $toolInputJson -match '(?i)play'
    $isEdit = $toolInputJson -match '(?i)edit'
    if (-not $isPlay -and -not $isEdit) {
        # Test mode not stated -> credit both rather than nag about a run that did happen.
        $isPlay = $true
        $isEdit = $true
    }
    if ($isPlay) { Touch-Marker $runPlayMarker }
    if ($isEdit) { Touch-Marker $runEditMarker }
    exit 0
}

if ($Mode -eq 'stop-gate') {
    # Never loop: if a previous Stop hook already blocked this stop, always pass.
    $stopHookActive = $false
    try {
        if ($payload.PSObject.Properties.Name -contains 'stop_hook_active') {
            $stopHookActive = [bool]$payload.stop_hook_active
        }
    } catch {}
    if ($stopHookActive) {
        Remove-Markers
        exit 0
    }

    $playEditTime = Get-MarkerTime $playEditMarker
    $codeEditTime = Get-MarkerTime $codeEditMarker
    if ($null -eq $playEditTime -and $null -eq $codeEditTime) {
        # No Dev/** edits this session batch -> nothing to gate.
        Remove-Markers
        exit 0
    }

    $askTime = Get-MarkerTime $askMarker
    $runEditTime = Get-MarkerTime $runEditMarker

    # PlayMode-relevant edits need the user consulted about the play test.
    $needsPlayAsk = $false
    if ($null -ne $playEditTime) {
        if ($null -eq $askTime -or $askTime -lt $playEditTime) { $needsPlayAsk = $true }
    }

    # Plain C# edits need the EditMode suite run (or the user consulted, which covers a decline).
    $needsEditRun = $false
    if ($null -ne $codeEditTime) {
        $coveredByRun = ($null -ne $runEditTime -and $runEditTime -ge $codeEditTime)
        $coveredByAsk = ($null -ne $askTime -and $askTime -ge $codeEditTime)
        if (-not $coveredByRun -and -not $coveredByAsk) { $needsEditRun = $true }
    }

    if (-not $needsPlayAsk -and -not $needsEditRun) {
        Remove-Markers
        exit 0
    }

    if (Test-AnyAgentBusy) {
        # Background implementation is still running. Pass WITHOUT clearing the edit markers so the
        # requirement lands on the first turn that ends after every subagent has finished.
        exit 0
    }

    $parts = @()
    if ($needsPlayAsk) {
        $parts += ('PlayMode-relevant files were edited (scene / prefab / UI / input). FIRST check whether a scenario or ' +
            'PlayMode test already covers this change (mirror panel /api/scenarios, Dev/Assets/Tests/PlayMode/**). ' +
            'If one covers it: run ONLY that one (test_names, or panel POST /api/run-scenario on the mirror) - no question needed. ' +
            'If nothing covers it: ask the user via AskUserQuestion whether to AUTHOR a covering scenario - the question text ' +
            'MUST contain the Korean phrase "' + $koreanPlaytestNeedle + '". ' +
            'Do NOT run the full PlayMode suite as a substitute - full regression only on explicit user request ' +
            '(see .claude/rules/playtest.md "검증 범위"). Report the TestArtifacts screenshots.')
    }
    if ($needsEditRun) {
        $parts += ('Plain C# under Dev/** was edited. Run unityMCP run_tests (EditMode) for ONLY the test(s) covering the ' +
            'change - use test_names / group_names / category_names, NOT the whole Assembly-CSharp-Editor suite. ' +
            'Do NOT run the full regression suite unless the user explicitly asked (see .claude/rules/playtest.md "검증 범위"). ' +
            'No need to ask first. If Unity is unavailable or PlayMode is held, say so explicitly instead of skipping silently.')
    }
    $reason = 'playtest-gate: ' + ($parts -join ' ALSO: ') +
        ' Verify ONCE per turn as the orchestrator - do not have each implementer run tests. See .claude/rules/playtest.md.'

    $result = @{
        decision = 'block'
        reason   = $reason
    }
    [Console]::Out.WriteLine(($result | ConvertTo-Json -Compress -Depth 5))
    exit 0
}

exit 0
