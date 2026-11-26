# 설계명세: 인증 기반 (NFR-03)

> 교재 5회차 Step 9-1. FR-07(내 신청 내역)·FR-08(관리자 명단·수료) 전에 "누가 요청했는가"를 토큰으로 확인하게 한다.
> FR-04·FR-06 설계의 `X-Employee-Id` 임시 헤더(`TODO(NFR-03)`)를 없애는 것이 핵심이다.

## 1. 요구사항 정리
| ID | 내용 | 이번 범위 |
|---|---|---|
| NFR-03 | 로그인한 사용자만 신청·취소, 관리 기능은 관리자만. 비밀은 환경 변수 | ✅ |
| FR-03 | 과정 등록·수정·회차 추가·종료는 관리자 (3회차에 인증을 미뤘던 구멍) | ✅ 권한만 |
| FR-04 / FR-06 | 신청자·취소자는 요청 값이 아니라 **토큰의 사원** | ✅ 헤더 제거 |
| D-02 | 관리자 대리 취소 | ✗ (FR-08 때 관리자 API로) |
| 화면 로그인 | 로그인 화면·토큰 저장 | ✗ (교재 Step 13~14) — 질문 3 |

**사람이 정한 것 (교재 기준, 그대로 따른다)**
- JWT Bearer, 서버는 세션을 저장하지 않음(STATELESS), CSRF 끔(쿠키가 아니라 헤더 인증)
- 교육용 개발 로그인: 사번 + 비밀번호(BCrypt). 실제 회사는 SSO지만 토큰을 받은 **뒤** 구조는 같다
- HS256 공유 비밀키는 환경 변수 `EDUHUB_JWT_SECRET`(32바이트 이상)로만. `application*.yml`에 값으로 쓰지 않는다
- 토큰 검증은 Spring Security 리소스 서버(`oauth2ResourceServer().jwt()`)를 쓴다. **직접 만든 필터로 파싱하지 않는다**(만료·서명·알고리즘 검사 누락 방지)
- 사용자 정보는 `CurrentUser.from(jwt)` **한 곳**에서만 꺼낸다
- 로그인 실패는 사번이 없든 비밀번호가 틀리든 **같은 401**

**질문과 결정** (2026-11-27 개발자가 1~4 모두 제안대로 승인)
1. **경로**: 교재는 `/api/auth/login`, `/api/me`, `/api/admin/**`이지만 이 저장소 API는 모두 `/api/v1` 아래다 → **`POST /api/v1/auth/login`, `GET /api/v1/me`, 관리자 API는 `/api/v1/admin/**`**로 맞추기를 제안
2. **enrollment.employee_id 외래 키**: 이번에 `employee` 테이블을 처음 만든다. FK를 걸면 기존 테스트·동시성 테스트가 임의 번호(1001, 1~100 등)로 신청하던 것을 모두 사원 행을 만들어 쓰도록 고쳐야 한다 → **이번에는 FK 없이**, 신청자는 토큰에서만 오므로 실제 사원 번호만 들어간다. FK는 별도 작업(마이그레이션 + 테스트 정리)으로 제안
3. **화면 공백**: 헤더를 없애면 지금 화면(course-mfe)의 신청 버튼과 과정 관리 화면이 로그인 화면(교재 Step 13~14)이 생길 때까지 **401로 동작하지 않는다**. 교재 순서(인증 → FR-07 → FR-08 → 화면)대로 **그 사이 화면 공백을 허용**하기를 제안. 대안은 이번에 최소 로그인(토큰 저장·헤더 첨부)까지 넣는 것(범위 커짐)
4. **로컬 실행(`bootTestRun`) 비밀키**: `bootTestRun`은 `src/test`에만 있고 배포되지 않는 내장 DB 실행이다. 매번 환경 변수를 넣지 않아도 되게 **`LocalEduhubApplication`에서만 로컬 전용 기본값**을 쓰기를 제안(환경 변수가 있으면 그것을 우선). `dev` 프로필(로컬 PostgreSQL)과 운영은 환경 변수 필수 — 없으면 시작 실패

## 2. API
| 메서드·경로 | 권한 | 요청 | 응답 | 오류 |
|---|---|---|---|---|
| `POST /api/v1/auth/login` | 공개 | `{employeeNo, password}` | 200 `{accessToken, tokenType:"Bearer", expiresIn, user}` | 400 `INVALID_REQUEST`(빈 값), 401 `INVALID_CREDENTIALS`("사번 또는 비밀번호가 올바르지 않습니다.") |
| `GET /api/v1/me` | 로그인 | — | 200 `CurrentUser {employeeId, employeeNo, name, role}` | 401 |
| `POST /api/v1/sessions/{id}/enrollments` | 로그인 | (헤더 `X-Employee-Id` **삭제**) | 기존과 같음 | 401 추가 |
| `POST /api/v1/enrollments/{id}/cancel` | 로그인 | (헤더 삭제) | 기존과 같음 | 401 추가. 남의 신청은 기존대로 404 |
| `GET /api/v1/courses/**` | 공개 | | | |
| 그 밖의 `/api/v1/courses/**` (FR-03 쓰기) | **ADMIN** | | | 401 / 403 |
| `/api/v1/admin/**` (FR-08 예정) | **ADMIN** | | | 401 / 403 |
| `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/api/health` | 공개 | | | |
| 그 밖 | 로그인 | | | 401 |

- **401·403 응답 본문**도 기존 `ErrorResponse` 형식으로: 401 `UNAUTHORIZED`("로그인이 필요합니다."), 403 `FORBIDDEN`("권한이 없습니다.") — `AuthenticationEntryPoint`, `AccessDeniedHandler`. 토큰 문제는 `WWW-Authenticate: Bearer` 헤더 유지
- **공개 GET에 만료·위조 토큰이 붙으면 401** (2026-11-27 결정, Spring 기본 동작 유지). 화면은 401을 받으면 저장한 토큰을 지우고 다시 요청하거나 로그인으로 보낸다(Step 14)
- `/error`는 공개 — 그래야 비로그인 요청의 404·405·500이 401로 덮이지 않는다
- 토큰 검증: HS256만, `iss=eduhub` 확인, 시계 허용 오차 60초(기본값)
- OpenAPI: `bearerAuth`(HTTP bearer, JWT) 보안 스키마를 등록해 Swagger UI에서 **Authorize**로 토큰을 넣을 수 있게 한다. 로그인 API는 보안 요구 없음

### 토큰
| 항목 | 값 |
|---|---|
| 알고리즘 | HS256, 키 = `EDUHUB_JWT_SECRET` UTF-8 바이트(32 미만이면 **시작 실패**) |
| 클레임 | `iss=eduhub`, `sub=사원 id`, `employeeNo`, `name`, `role`(EMPLOYEE·ADMIN), `iat`, `exp` |
| 만료 | 60분 (`eduhub.jwt.ttl-minutes`, 기본 60). 리프레시 토큰 없음 |
| 권한 매핑 | `role` 클레임 → `ROLE_ADMIN` / `ROLE_EMPLOYEE` (`JwtGrantedAuthoritiesConverter`, 접두사 `ROLE_`) |
| 시계 | 발급 시각은 주입된 `Clock`(KST) 사용 → 만료 테스트 가능 |

## 3. 스키마 — `V3__create_employee.sql`
```sql
CREATE TABLE employee (
    id            BIGSERIAL PRIMARY KEY,
    employee_no   VARCHAR(20)  NOT NULL,
    name          VARCHAR(50)  NOT NULL,
    department    VARCHAR(50),
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL
);
CREATE UNIQUE INDEX ux_employee_no ON employee (employee_no);
```
- `enrollment.employee_id`와의 FK는 이번에 두지 않는다 (질문 2)
- 계정 생성 API는 없다. 개발용 계정은 로컬 실행 데이터로만 (4장)

## 4. 패키지·클래스 (NFR-07: 도메인별 패키지)
| 패키지 | 클래스 | 역할 |
|---|---|---|
| `employee.domain` | `Employee`(엔티티, setter 없음), `EmployeeRole { EMPLOYEE, ADMIN }`, `EmployeeRepository.findByEmployeeNo` | 사원 |
| `auth.config` | `SecurityConfig`(필터 체인·권한 규칙·`PasswordEncoder`·401/403 핸들러), `JwtConfig`(`JwtEncoder`/`JwtDecoder`, 키 길이 검사) | 보안 설정 |
| `auth.application` | `TokenService.issue(employee)`, `LoginService.login(no, pw)` → 토큰·사용자, `InvalidCredentialsException` | 로그인 |
| `auth.api` | `AuthController`(login, me), `LoginRequest`, `LoginResponse`, `CurrentUser`(record, `from(Jwt)`, `isAdmin()`) | API |

- 컨트롤러는 `@AuthenticationPrincipal Jwt jwt` → `CurrentUser.from(jwt).employeeId()`를 서비스에 넘긴다. 서비스 시그니처(`enroll(sessionId, employeeId)`, `cancel(id, employeeId)`)는 **바꾸지 않는다**
- 로그인 로직은 컨트롤러가 아니라 `LoginService`에 둔다(계층 규칙: 컨트롤러에서 리포지토리 직접 호출 금지)
- **개발용 계정**(`bootTestRun`의 `LocalDevConfiguration`에 추가): `E1001` 김사원, `E1002` 이사원(EMPLOYEE), `A0001` 박관리(ADMIN), 비밀번호 `dev-password` — 교재 6회차 확인 계정과 같다. 운영 코드(src/main)에는 들어가지 않는다

## 5. 설정·비밀
```yaml
# application.yml
eduhub:
  jwt:
    secret: ${EDUHUB_JWT_SECRET}   # 값은 환경 변수로만
    ttl-minutes: 60
```
- `src/test/resources/application-test.yml`: 테스트 전용 비밀(32바이트 이상, "test-only"가 드러나는 문자열). 운영 비밀이 아니므로 커밋
- `.env.example`에 `EDUHUB_JWT_SECRET=` 이름만 — **개발자가 직접 추가**(4회차 deny 규칙으로 Claude는 이 파일을 읽지 않는다)
- 로그·응답에 비밀번호·토큰을 남기지 않는다. 로그인 실패 로그는 사번만(실명 없음)

## 6. 테스트 시나리오
| 종류 | 시나리오 | 기대 |
|---|---|---|
| 로그인 (SpringBootTest + MockMvc) | 맞는 사번·비밀번호 | 200, 토큰으로 `/me` 호출 시 같은 사원 |
| | 틀린 비밀번호 / 없는 사번 | 둘 다 401 `INVALID_CREDENTIALS`, **메시지 동일** |
| | 빈 값 | 400 |
| 토큰 | 없음 / 위조(다른 키로 서명) / 만료 / `alg=none` | 401 `UNAUTHORIZED` |
| 키 | 32바이트 미만 비밀 | `JwtConfig` 생성 실패 |
| 권한 | 과정 목록·상세 GET 토큰 없이 | 200 |
| | 과정 등록 POST: 토큰 없음 / 사원 / 관리자 | 401 / 403 / 201 |
| | 과정 수정·회차 추가·종료: 사원 | 403 |
| | `/api/v1/admin/**` 사원 | 403 (아직 핸들러 없음 → 관리자는 404여도 됨) |
| 신청·취소 | 토큰 없이 신청 | 401 |
| | 사원 A 토큰으로 신청 | 저장된 `employee_id` = A |
| | A의 신청을 B 토큰으로 취소 / A 토큰으로 취소 | 404 / 200 |
| 회귀 | 기존 `EnrollmentControllerTest`·`CourseControllerTest`(WebMvc) | 헤더 대신 `jwt()` 요청 후처리로 고쳐 그대로 통과. 동시성 테스트는 서비스 직접 호출이라 변경 없음 |
| 테스트의 테스트 | `/api/v1/courses/**` ADMIN 줄을 지우면 | "과정 등록은 관리자만" 실패 (개발자 직접 확인) |

## 7. 범위 밖·알려진 제한
| 항목 | 현재 | 이후 |
|---|---|---|
| 리프레시 토큰·로그아웃 | 없음. 만료(60분)까지 유효 | 필요 시 짧은 액세스 + 리프레시 |
| 비밀번호 변경·계정 관리 | 없음 | SSO로 바꾸면 불필요 |
| 로그인 시도 제한 | 없음 (개발용 로그인) | 운영은 SSO가 담당 |
| 관리자 대리 취소 (D-02) | 없음 | FR-08 관리자 API에서 |
| enrollment → employee FK | 없음 (질문 2) | 별도 작업 |
| 화면 | 로그인 화면 전까지 신청·관리 화면 401 (질문 3) | 교재 Step 13~14 |
