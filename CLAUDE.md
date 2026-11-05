# CLAUDE.md

EduHub: 사내 교육과정 조회, 수강 신청 시스템 (7주 Toy 프로젝트).

## 구조
- `docs/requirements.md`: 요구사항 기준 문서 (FR, NFR, 결정 사항 D-xx)
- `backend/`: Spring Boot 3.5, Java 21, Gradle Kotlin DSL
  - `com.example.eduhub.{course, enrollment, common}`: 도메인별 패키지 (NFR-07). 새 코드는 해당 도메인 패키지 안에 계층별로 둔다
  - 프로필: `dev`(기본, 로컬 PostgreSQL), `test`(자동 테스트)
  - DB 스키마는 Flyway 마이그레이션(`src/main/resources/db/migration`)으로만 바꾼다. `ddl-auto`는 `validate`
- 프론트엔드(React + Module Federation)는 아직 없다 (2주차 이후)

## 명령
- 빌드·테스트: `cd backend && ./gradlew test`
- 포맷: `./gradlew spotlessApply` (검사는 `spotlessCheck`, `check`에 포함)
- API 문서: 실행 후 `/swagger-ui.html`

## 팀 규칙
- 요구사항은 docs/requirements.md를 기준으로 한다 (결정 사항 D-xx 포함)
- 계층 규칙: Controller → Service → Repository. Controller에서 Repository 직접 호출 금지
- 엔티티를 API 응답으로 그대로 반환하지 않는다 (DTO 사용)
- 비밀 정보는 코드와 설정 파일에 쓰지 않는다. 환경 변수와 .env(커밋 금지)만 사용
- 작업은 `main`이 아닌 feature 브랜치에서 하고, 커밋 메시지는 Conventional Commits 형식
