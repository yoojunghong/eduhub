# .claude/hooks/stop-test.ps1 — Stop: 백엔드 코드가 바뀌었으면 테스트를 돌리고, 실패하면 계속 고치게 한다
# 실패 줄만 넘겨 토큰을 아낀다. 테스트를 돌릴 수 없는 상황(입력 오류, git 없음)에서는 막지 않는다
# Windows PowerShell 5.1이 한글 문자열을 바르게 읽도록 이 파일은 UTF-8 (BOM)으로 저장한다

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

try {
    $hookInput = [Console]::In.ReadToEnd() | ConvertFrom-Json
} catch {
    exit 0
}
# Stop 훅 때문에 이미 한 번 더 진행 중이면 반복하지 않는다 (무한 반복 방지)
if ($hookInput.stop_hook_active -eq $true) { exit 0 }

$root = $env:CLAUDE_PROJECT_DIR
if ([string]::IsNullOrEmpty($root)) { exit 0 }

# 커밋되지 않은 백엔드 변경이 없으면 테스트하지 않는다
$changed = & git -C $root status --porcelain -- backend/src 2>$null
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($changed -join ''))) { exit 0 }

$backend = Join-Path $root 'backend'
# PowerShell 5.1에서 2>&1을 쓰면 stderr 줄이 RemoteException 객체가 되므로, cmd에서 합쳐 문자열로 받는다
$gradlew = Join-Path $backend 'gradlew.bat'
$out = & cmd.exe /c "`"$gradlew`" -p `"$backend`" test --console=plain 2>&1"
if ($LASTEXITCODE -eq 0) { exit 0 }

# 실패 테스트(FAILED), 예외, 컴파일 오류(error:) 줄만 20줄
$fails = ($out | Select-String -Pattern 'FAILED|Exception|error:' | Select-Object -First 20 | ForEach-Object { $_.Line.Trim() }) -join "`n"
$reason = "백엔드 테스트가 실패했습니다(./gradlew test). 원인을 고친 뒤 마무리하세요.`n$fails"
$json = @{ decision = 'block'; reason = $reason } | ConvertTo-Json -Compress
[Console]::Out.Write($json)
exit 0
