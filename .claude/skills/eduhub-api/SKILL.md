---
name: eduhub-api
description: EduHub 백엔드에 REST API를 추가하거나 바꿀 때 쓰는 팀 절차. 새 엔드포인트 추가, 기존 API의 요청·응답·상태 코드 변경, 새 기능(FR)의 백엔드 구현, 테이블·엔티티·DTO 추가처럼 backend/의 Controller·Service·Repository를 건드리는 작업이면 시작 전에 읽는다. 프론트 화면만 바꾸는 작업에는 쓰지 않는다.
---

# EduHub API 추가 절차

기준 예시: `backend/src/main/java/com/example/eduhub/course` (Controller·Service·DTO·테스트).
도메인 패키지(`course`, `enrollment`, `common`) 안에 `api` / `application` / `domain` 계층을 둔다 (NFR-07).

## 1. Flyway 마이그레이션
- `src/main/resources/db/migration/V{n}__설명.sql`. `n`은 기존 최대 번호 + 1 (다른 브랜치와 겹치는지 확인)
- 이미 적용된 파일은 고치지 않는다. `ddl-auto: validate`이므로 엔티티와 컬럼이 정확히 맞아야 한다
- 중복 방지는 코드가 아니라 **DB 유일 제약(부분 인덱스 포함)**으로 보장한다

## 2. 엔티티 (`domain`)
- `protected` 기본 생성자, 상태 변경은 의미 있는 메서드(`close()`, `confirm()`)로. setter 금지
- 연관관계는 `FetchType.LAZY`. 상태는 `@Enumerated(EnumType.STRING)`
- 도메인 예외는 `domain` 패키지에 `XxxNotFoundException`, `XxxClosedException`처럼 둔다

## 3. DTO (`api`)
- 요청·응답 모두 `record`. 요청은 Bean Validation(`@NotBlank`, `@Size`, `@Positive` …)
- 응답은 `static from(Entity)` 팩토리. **엔티티를 응답으로 반환하지 않는다**
- 목록은 `common.api.PageResponse`, 오류는 `common.api.ErrorResponse(code, message)`

## 4. Repository (`domain`)
- `JpaRepository` 상속. N+1이 생기는 조회는 fetch join / `@EntityGraph` / 프로젝션(`CourseSummaryRow`)
- 수량(정원·순번)을 바꾸는 흐름의 조회는 `@Lock(PESSIMISTIC_WRITE)` + `findByIdForUpdate`

## 5. Service (`application`)
- 쓰기는 `@Transactional`, 조회 전용 서비스·메서드는 `@Transactional(readOnly = true)`
- 없으면 `orElseThrow(() -> new XxxNotFoundException(id))`. `Optional.get()` 금지
- 잠금 → 확인 → 저장은 **한 트랜잭션** 안에서. 트랜잭션 안에서 외부 호출 금지

## 6. Controller (`api`)
- `@RestController`, 경로는 `/api/v1/...`. Repository 직접 호출 금지
- `@Tag`, `@Operation(summary, description)`, 오류마다 `@ApiResponse(responseCode, content = ErrorResponse)` (NFR-04)
- 생성은 `201 Created` + `Location` 헤더
- 로그인 사용자 정보가 필요한 값(employeeId 등)은 요청 본문에서 받지 않는다. 인증 전에는 `TODO(NFR-03)`

## 7. 예외 → HTTP 상태 (`common.api.GlobalExceptionHandler`)
| 예외 | 상태 | code |
|---|---|---|
| `XxxNotFoundException` | 404 | `XXX_NOT_FOUND` |
| 상태 충돌(`CourseClosedException`, `DuplicateEnrollmentException`) | 409 | `COURSE_CLOSED`, `DUPLICATE_ENROLLMENT` |
| `MethodArgumentNotValidException`, 본문 오류 | 400 | `INVALID_REQUEST` |
- 유일 제약 위반(`DataIntegrityViolationException`)은 서비스에서 도메인 예외로 바꿔 409로 보낸다 (500 금지)

## 8. 테스트
- Service·Repository: `@DataJpaTest` + `@AutoConfigureEmbeddedDatabase(provider = EMBEDDED)` + `@ActiveProfiles("test")` (Docker·Testcontainers 쓰지 않음)
- Controller: `@WebMvcTest(XxxController.class)` + `@MockitoBean` 서비스. 성공·404·409·400을 각각 확인
- 동시성: `@SpringBootTest` + 내장 PostgreSQL + `CountDownLatch`로 동시에 출발
- `@DisplayName`은 한국어 문장으로 기대 동작을 쓴다

## 9. 프론트 API 클라이언트 재생성
- 백엔드 실행 중(`./gradlew bootTestRun`)에 `cd frontend && npm run generate:api`
- `packages/api-client/src/generated`는 직접 고치지 않는다

## 10. 검증 (끝내기 전에 반드시)
```bash
cd backend && ./gradlew spotlessApply test
```
모두 통과한 결과(테스트 수)를 보고한다. 실패하면 끝났다고 하지 않는다.
