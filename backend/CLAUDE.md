# backend/CLAUDE.md

Spring Boot 3.5, Java 21, Gradle Kotlin DSL. 기본 패키지 `com.example.eduhub`.

## 명령
- 테스트: `./gradlew test`
- 포맷: `./gradlew spotlessApply` (검사는 `spotlessCheck`, `check`에 포함)
- 프로필: `dev`(기본, 로컬 PostgreSQL), `test`(자동 테스트)
- DB 테스트는 Docker 없이 `@AutoConfigureEmbeddedDatabase(provider = EMBEDDED)`로 PostgreSQL을 띄운다 (Testcontainers 쓰지 않음)

## 계층 규칙
- 도메인별 패키지 `course`, `enrollment`, `common` 안에 계층을 둔다 (NFR-07)
- Controller → Service → Repository. Controller에서 Repository 직접 호출 금지

## JPA 규칙
- 연관관계는 `fetch = FetchType.LAZY`
- 엔티티를 API 응답으로 그대로 반환하지 않는다 (DTO `record` 사용)
- `spring.jpa.open-in-view=false` 유지
- 스키마는 Flyway(`src/main/resources/db/migration/V{n}__설명.sql`)로만 바꾼다. 이미 적용된 마이그레이션은 고치지 않고 새 버전을 추가한다. `ddl-auto`는 `validate`

## OpenAPI 규칙
- 모든 API에 `@Tag`, `@Operation(summary)`를 단다 (NFR-04)
- 오류 응답(404, 409 등)은 `@ApiResponse`로 문서화한다
- Swagger UI: 실행 후 `/swagger-ui.html`
