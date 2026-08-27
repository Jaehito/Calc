# PreToolUse gate: block the MAIN agent from editing Dev/** and docs/** directly.
# Subagent tool calls (payload contains agent_id) are allowed through.
#
# Tier-S exception: the orchestrator may edit up to 3 declared, pure-C#, non-PlayMode-relevant
# files under Dev/** directly. Declared via .claude/scripts/tier-s-declare.ps1 (30-minute
# expiry). The hook re-validates count, extension and path patterns, so a hand-written marker
# still cannot unlock scene/SO/prefab/UI/Input paths or docs/**.
#
# Fail-open on unexpected payloads so a hook bug never blocks legitimate work.

$ErrorActionPreference = 'Stop'

$TierSMaxFiles = 3
$TierSExpiryMinutes = 30
# Mirrors playtest-gate.ps1 $playPathPatterns plus asset extensions: PlayMode-relevant paths
# never qualify for Tier S regardless of what the marker claims.
$TierSDeniedPatterns = @(
    '(?i)\.(unity|prefab|asset)$',
    '(?i)^Dev/Assets/Scripts/.*/UI/',
    '(?i)^Dev/Assets/Scripts/.*(View|Layout|Overlay|Panel|Hud|Popup|Toast)\.cs$',
    '(?i)^Dev/Assets/Tests/PlayMode/',
    '(?i)^Dev/Assets/Scripts/.*Input.*\.cs$'
)

function Test-TierSAllowed([string]$relPath) {
    try {
        if ($relPath -notmatch '^Dev/') { return $false }
        if ($relPath -notmatch '(?i)\.cs$') { return $false }
        foreach ($pattern in $TierSDeniedPatterns) {
            if ($relPath -match $pattern) { return $false }
        }
        $markerPath = Join-Path (Join-Path $env:TEMP 'claude-sos-playtest') 'tier-s.json'
        if (-not (Test-Path $markerPath)) { return $false }
        $ageMinutes = ([DateTime]::UtcNow - (Get-Item $markerPath).LastWriteTimeUtc).TotalMinutes
        if ($ageMinutes -gt $TierSExpiryMinutes) { return $false }
        $marker = (Get-Content -Path $markerPath -Raw -Encoding UTF8) | ConvertFrom-Json
        if ($null -eq $marker -or -not ($marker.PSObject.Properties.Name -contains 'files')) { return $false }
        $declared = @($marker.files)
        if ($declared.Count -eq 0 -or $declared.Count -gt $TierSMaxFiles) { return $false }
        foreach ($file in $declared) {
            $declaredNorm = (([string]$file) -replace '\\', '/').TrimStart('/')
            if ([string]::Equals($declaredNorm, $relPath, [System.StringComparison]::OrdinalIgnoreCase)) { return $true }
        }
    } catch {}
    return $false
}

try {
    $raw = [Console]::In.ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($raw)) { exit 0 }
    $payload = $raw | ConvertFrom-Json
} catch {
    exit 0
}

# agent_id is present only for subagent tool calls -> allow subagents
$agentId = $null
if ($payload.PSObject.Properties.Name -contains 'agent_id') { $agentId = $payload.agent_id }
if (-not [string]::IsNullOrEmpty([string]$agentId)) { exit 0 }

# Unity MCP script tools (write actions) always target the Unity project under Dev/**,
# so a main-agent call is a Dev edit by definition - no path parsing needed.
$toolName = ''
if ($payload.PSObject.Properties.Name -contains 'tool_name') { $toolName = [string]$payload.tool_name }
if ($toolName -match '^mcp__unityMCP__(manage_script|create_script|delete_script|script_apply_edits|apply_text_edits)$') {
    $action = ''
    if ($payload.PSObject.Properties.Name -contains 'tool_input') {
        $ti = $payload.tool_input
        if ($null -ne $ti -and $ti.PSObject.Properties.Name -contains 'action') { $action = [string]$ti.action }
    }
    if ($action -match '^(read|get|list)') { exit 0 }
    $result = @{
        hookSpecificOutput = @{
            hookEventName            = 'PreToolUse'
            permissionDecision       = 'deny'
            permissionDecisionReason = 'main-edit-gate: Unity MCP script tools modify Dev/**, and the main agent must not edit Dev/** directly. Delegate the change to the implementer subagent via the Task tool (with the user-approved plan in the handoff).'
        }
    }
    [Console]::Out.WriteLine(($result | ConvertTo-Json -Compress -Depth 5))
    exit 0
}

# Extract target file path from tool_input (Edit/Write/MultiEdit: file_path, NotebookEdit: notebook_path)
$filePath = $null
$toolInput = $null
if ($payload.PSObject.Properties.Name -contains 'tool_input') { $toolInput = $payload.tool_input }
if ($null -ne $toolInput) {
    foreach ($key in @('file_path', 'notebook_path')) {
        if ($toolInput.PSObject.Properties.Name -contains $key) {
            $candidate = [string]$toolInput.$key
            if (-not [string]::IsNullOrEmpty($candidate)) { $filePath = $candidate; break }
        }
    }
}
if ([string]::IsNullOrEmpty($filePath)) { exit 0 }

# Normalize to a project-relative path
$root = $env:CLAUDE_PROJECT_DIR
if ([string]::IsNullOrEmpty($root) -and $payload.PSObject.Properties.Name -contains 'cwd') {
    $root = [string]$payload.cwd
}
$normalized = $filePath -replace '\\', '/'
if (-not [string]::IsNullOrEmpty($root)) {
    $rootNorm = (($root -replace '\\', '/').TrimEnd('/')) + '/'
    if ($normalized.StartsWith($rootNorm, [System.StringComparison]::OrdinalIgnoreCase)) {
        $normalized = $normalized.Substring($rootNorm.Length)
    }
}

if ($normalized -match '^(Dev|docs)/') {
    if (Test-TierSAllowed $normalized) { exit 0 }
    $result = @{
        hookSpecificOutput = @{
            hookEventName            = 'PreToolUse'
            permissionDecision       = 'deny'
            permissionDecisionReason = ('main-edit-gate: The main agent must not edit Dev/** or docs/** directly. Delegate the change to ' +
                'the implementer subagent via the Task tool (with the user-approved plan in the handoff). EXCEPTION for Tier S ' +
                '(<=3 pure C# files, no scene/SO/prefab/UI/Input/PlayMode-test paths): declare them first with ' +
                'powershell -NoProfile -File ".claude/scripts/tier-s-declare.ps1" -Files "<Dev/... .cs paths>" then retry this edit.')
        }
    }
    [Console]::Out.WriteLine(($result | ConvertTo-Json -Compress -Depth 5))
    exit 0
}

exit 0
