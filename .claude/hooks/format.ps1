# .claude/hooks/format.ps1 — PostToolUse(Edit|Write): 수정한 파일만 포맷
# 포맷 도구가 없거나 실패해도 작업이 멈추지 않도록 항상 exit 0

try {
    $hookInput = [Console]::In.ReadToEnd() | ConvertFrom-Json
} catch {
    exit 0
}
$file = [string]$hookInput.tool_input.file_path
$root = $env:CLAUDE_PROJECT_DIR
if ([string]::IsNullOrEmpty($file) -or [string]::IsNullOrEmpty($root)) { exit 0 }

# 출력은 모두 버린다: PostToolUse 출력이 컨텍스트를 채우지 않게
try {
    if ($file -match '\.java$') {
        # -p 로 backend 폴더를 프로젝트로 지정 → 현재 폴더를 바꿀 필요가 없다
        $backend = Join-Path $root 'backend'
        & (Join-Path $backend 'gradlew.bat') -p $backend spotlessApply -q *> $null
    }
    elseif ($file -match '\.tsx?$') {
        # 저장소에 설치된 prettier가 있을 때만 (npx로 새로 내려받지 않는다)
        $prettier = Join-Path $root 'frontend/node_modules/.bin/prettier.cmd'
        if (Test-Path -LiteralPath $prettier) {
            & $prettier --write $file *> $null
        }
    }
} catch {
    # gradlew.bat·prettier가 없거나 실패하면 포맷만 건너뛴다
}
exit 0
