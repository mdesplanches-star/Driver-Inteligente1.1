param(
    [switch]$RunGradle,
    [switch]$CheckAdb
)

$ErrorActionPreference = "Stop"
$repo = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $repo

function Fail {
    param([string]$Message)
    Write-Error $Message
    exit 1
}

function Assert-File {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        Fail "Missing required file: $Path"
    }
}

function Assert-Match {
    param(
        [string]$Path,
        [string]$Pattern,
        [string]$Message
    )
    $content = Get-Content -Raw $Path
    if ($content -notmatch $Pattern) {
        Fail $Message
    }
}

function Assert-Agent {
    param(
        [string]$Name,
        [string]$Model,
        [string]$Effort,
        [string]$Sandbox
    )
    $path = ".codex\agents\$Name.toml"
    Assert-File $path
    $text = Get-Content -Raw $path
    foreach ($field in @("name", "description", "developer_instructions")) {
        if ($text -notmatch "(?m)^$field\s*=") {
            Fail "Agent $Name is missing required field $field."
        }
    }
    if ($text -notmatch "(?m)^name\s*=\s*`"$Name`"") {
        Fail "Agent $Name has mismatched name."
    }
    if ($text -notmatch "(?m)^model\s*=\s*`"$Model`"") {
        Fail "Agent $Name must use $Model."
    }
    if ($text -notmatch "(?m)^model_reasoning_effort\s*=\s*`"$Effort`"") {
        Fail "Agent $Name must use effort $Effort."
    }
    if ($text -notmatch "(?m)^sandbox_mode\s*=\s*`"$Sandbox`"") {
        Fail "Agent $Name must use sandbox $Sandbox."
    }
    $tripleCount = ([regex]::Matches($text, '"""')).Count
    if (($tripleCount % 2) -ne 0) {
        Fail "Agent $Name has an unclosed triple-quoted string."
    }
}

Assert-File "AGENTS.md"
Assert-File ".codex\config.toml"
Assert-File ".codex\hooks.json"
Assert-File ".codex\hooks\driver_policy_guard.ps1"
Assert-File ".codex\scripts\simulate-routing.ps1"
Assert-File "docs\CODEX_ORCHESTRATION.md"
foreach ($path in @(
    ".ai\README.md",
    ".ai\contracts\task-brief.schema.yaml",
    ".ai\contracts\agent-result.schema.yaml",
    ".ai\contracts\offer-snapshot.md",
    ".ai\contracts\decision-result.md",
    ".ai\templates\task-brief.yaml",
    ".ai\policies\delegation.yaml",
    ".ai\policies\file-ownership.yaml",
    ".ai\policies\resource-budget.yaml",
    ".ai\policies\research-policy.yaml",
    ".ai\skills\project-state\SKILL.md",
    ".ai\skills\android-change\SKILL.md",
    ".ai\skills\offer-parser\SKILL.md",
    ".ai\skills\compose-ui-review\SKILL.md",
    ".ai\skills\targeted-testing\SKILL.md",
    ".ai\skills\release-gate\SKILL.md",
    ".ai\skills\concise-agent-output\SKILL.md",
    "docs\CURRENT_STATE.md",
    "docs\ARCHITECTURE.md",
    "docs\MODULE_MAP.md",
    "docs\DECISIONS.md",
    "docs\TEST_MATRIX.md",
    "docs\RELEASE_STATUS.md"
)) { Assert-File $path }

$config = Get-Content -Raw ".codex\config.toml"
if ($config -notmatch '(?m)^model\s*=\s*"gpt-5\.6-terra"') { Fail "Project default model must be gpt-5.6-terra." }
if ($config -notmatch '(?m)^model_reasoning_effort\s*=\s*"medium"') { Fail "Project default effort must be medium." }
if ($config -notmatch '(?m)^sandbox_mode\s*=\s*"workspace-write"') { Fail "Project sandbox must be workspace-write." }
if ($config -notmatch '(?m)^approval_policy\s*=\s*"on-request"') { Fail "Project approval policy must be on-request." }
if ($config -notmatch '(?m)^max_threads\s*=\s*3') { Fail "agents.max_threads must be 3." }
if ($config -notmatch '(?m)^max_depth\s*=\s*1') { Fail "agents.max_depth must be 1." }
if ($config -notmatch '(?m)^network_access\s*=\s*false') { Fail "workspace network access must be false." }

Assert-Agent "explorer" "gpt-5.6-luna" "low" "read-only"
Assert-Agent "android_worker" "gpt-5.6-terra" "medium" "workspace-write"
Assert-Agent "ui_compose" "gpt-5.6-terra" "medium" "workspace-write"
Assert-Agent "ocr_parser" "gpt-5.6-terra" "medium" "workspace-write"
Assert-Agent "architect" "gpt-5.6-sol" "max" "workspace-write"
Assert-Agent "debugger" "gpt-5.6-terra" "high" "workspace-write"
Assert-Agent "tester" "gpt-5.6-terra" "medium" "workspace-write"
Assert-Agent "privacy_security" "gpt-5.6-sol" "high" "workspace-write"
Assert-Agent "reviewer" "gpt-5.6-terra" "high" "read-only"
Assert-Agent "release_gate" "gpt-5.6-sol" "high" "workspace-write"
Assert-Agent "docs_research" "gpt-5.6-terra" "medium" "read-only"

$agentCount = (Get-ChildItem ".codex\agents\*.toml").Count
if ($agentCount -ne 11) {
    Fail "Expected 11 custom agents, found $agentCount."
}

Assert-Match "AGENTS.md" "Classe A.+Nao use Sol" "AGENTS.md must forbid Sol for Class A."
Assert-Match "AGENTS.md" "Maximo simultaneo normal: dois" "AGENTS.md must cap normal parallelism at two."
Assert-Match "AGENTS.md" "Profundidade maxima: uma camada" "AGENTS.md must cap delegation depth at one layer."
Assert-Match ".ai\policies\delegation.yaml" "default_parallel_limit:\s*2" "Delegation policy must default to two parallel agents."
Assert-Match ".ai\policies\delegation.yaml" "absolute_parallel_limit:\s*3" "Delegation policy must cap parallel agents at three."
Assert-Match ".ai\policies\resource-budget.yaml" "max_initial_files:\s*12" "Resource budget must limit initial reconnaissance."
Assert-Match ".ai\policies\research-policy.yaml" "default:\s*disabled" "External research must default to disabled."
Assert-Match ".ai\contracts\task-brief.schema.yaml" "external_research_allowed" "Task Brief must control external research."
Assert-Match ".ai\contracts\agent-result.schema.yaml" "needs_followup" "Agent result contract must include follow-up status."

powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".codex\hooks\driver_policy_guard.ps1"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".codex\scripts\simulate-routing.ps1" -Validate

if ($RunGradle) {
    .\gradlew.bat testDebugUnitTest
    if ($LASTEXITCODE -ne 0) { Fail "testDebugUnitTest failed." }
    .\gradlew.bat lintDebug
    if ($LASTEXITCODE -ne 0) { Fail "lintDebug failed." }
    .\gradlew.bat assembleDebug
    if ($LASTEXITCODE -ne 0) { Fail "assembleDebug failed." }
    .\gradlew.bat assembleRelease
    if ($LASTEXITCODE -ne 0) { Fail "assembleRelease failed." }
}

if ($CheckAdb) {
    $adb = "C:\Users\mdesp\AppData\Local\Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $adb) {
        & $adb devices
    } else {
        Write-Output "adb not found at expected Android SDK path."
    }
}

Write-Output "Codex orchestration validation OK"
