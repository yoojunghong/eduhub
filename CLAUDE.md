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

## 자동화 (훅, `.claude/hooks/`)
- 파일 수정 시 자동 포맷, 보호 파일·위험 명령은 훅이 차단한다. 차단 메시지의 안내를 따른다
- 백엔드 코드가 바뀐 채로 응답을 마치면 Stop 훅이 테스트를 돌린다. 실패하면 고친 뒤 마무리한다

## 개발 진행 절차 (기능 단위)
1. 시작: /fr-start <요구사항ID> → 설계명세(docs/design/) → 개발자 승인 → 구현계획(docs/plans/) → 개발자 승인
2. 구현: 계획의 작업마다 implementer에게 맡기고, 끝나면 reviewer로 검토한다
   - 심각·경고는 implementer에게 수정시킨다. 수정·재검토는 최대 2회, 넘으면 개발자에게 보고한다
3. 최종 검토: 모든 작업이 끝나면 critical-reviewer에게 설계명세 + 전체 diff + 1차 검토 결과를 넘긴다
4. 서브에이전트를 호출할 때 model은 지정하지 않는다 (정의 파일의 model을 따른다)
5. 3개 이상 파일·여러 계층에 걸친 작업이나 diff 1,000줄 이상은 개발자에게 알리고 지시를 받는다
6. 수량 변경(정원, 대기 순번)에는 반드시 락, 중복 방지는 DB 유일 제약으로 보장한다
