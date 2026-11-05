# CLAUDE.md

EduHub: 사내 교육과정 조회, 수강 신청 시스템 (7주 Toy 프로젝트).
임직원은 과정을 찾아 신청하고, 교육 담당자는 과정·회차를 관리한다.

## 디렉터리 지도
- `docs/requirements.md`: 요구사항 기준 문서 (FR, NFR, 결정 사항 D-xx)
- `backend/`: Spring Boot API. 규칙은 `backend/CLAUDE.md`
- `frontend/`: React 화면 (아직 코드 없음). 규칙은 `frontend/CLAUDE.md`
- `.github/pull_request_template.md`: PR 본문은 이 형식으로 쓴다

## 실행 명령
- 백엔드 테스트: `cd backend && ./gradlew test`
- 백엔드 포맷: `cd backend && ./gradlew spotlessApply`

## 공통 규칙
- 요구사항은 `docs/requirements.md`를 기준으로 한다 (결정 사항 D-xx 포함)
- 작업은 `main`이 아닌 브랜치에서 한다 (`feature/…`, `chore/…`). `main`에 직접 커밋하지 않는다
- 커밋 메시지는 Conventional Commits 형식 (`feat(course): …`, `docs: …`)
- 비밀 정보는 코드와 설정 파일에 쓰지 않는다. 환경 변수와 `.env`(커밋 금지)만 사용
