# .claude/hooks/guard-bash.ps1 — PreToolUse(Bash|PowerShell): 위험 명령 차단
# 최선의 노력용 안전망이다. 명령 변형을 모두 막을 수는 없으므로 권한 deny, main 브랜치 보호와 함께 쓴다
# Windows PowerShell 5.1이 한글 문자열을 바르게 읽도록 이 파일은 UTF-8 (BOM)으로 저장한다

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Block([string]$message) {
    [Console]::Error.WriteLine($message)
    exit 2
}

try {
    $hookInput = [Console]::In.ReadToEnd() | ConvertFrom-Json
} catch {
    exit 0
}
$cmd = [string]$hookInput.tool_input.command
if ([string]::IsNullOrEmpty($cmd)) { exit 0 }

# 운영 호스트·DB 이름 (팀 명명 규칙)
if ($cmd -match 'eduhub-prod|prod-db') {
    Block '운영 환경 접속 명령은 실행할 수 없습니다. 개발 DB(localhost)나 테스트용 내장 PostgreSQL을 사용하세요.'
}
# DB 전체 삭제
if ($cmd -match 'flywayClean') {
    Block 'flywayClean은 DB를 모두 지웁니다. 개발 DB 초기화가 필요하면 개발자에게 요청하세요.'
}
# 강제 푸시 (--force, --force-with-lease, -f)
if ($cmd -match '\bpush\b[^;&|]*\s(--force\S*|-f)(\s|$)') {
    Block '강제 푸시는 금지입니다. 이력을 고치지 말고 새 커밋으로 수정하세요.'
}
# main 직접 푸시 (origin main, HEAD:main, refs/heads/main)
if ($cmd -match '\bgit\s+push\b[^;&|]*([\s:]|/heads/)main(\s|$|[;&|])') {
    Block 'main에 직접 푸시하지 않습니다. 작업 브랜치를 푸시하고 PR을 만드세요.'
}
exit 0
