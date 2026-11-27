# 설계명세: 내 신청 내역 (FR-07), 회차 명단·수료 처리 (FR-08)

> 교재 5회차 Step 10·11. 인증(NFR-03, `docs/design/auth.md`) 위에서 동작한다.
> 사용자는 `CurrentUser.from(jwt)`로만 얻고, 관리자 API는 `/api/v1/admin/**`(ADMIN) 아래에 둔다.

## 1. 요구사항 정리
| ID | 내용 | 이번 범위 |
|---|---|---|
| FR-07 | 내 신청 내역(확정, 대기, 취소, 수료)을 조회한다 | ✅ |
| FR-08 | 관리자는 회차별 신청자·대기자 명단을 보고 수료 처리한다 | ✅ |
| D-05 | 수료 처리는 회차 종료 후 확정자에게만 관리자가 한다. 불참자는 "미수료" | ✅ — 질문 1·2 |
| D-08 | 대기 순번은 id 순으로 계산 | ✅ |
| NFR-02 | N+1 금지, 필요한 인덱스 | ✅ |
| D-02 관리자 대리 취소 | | ✗ (다음 작업) |
| FR-09 알림 | | ✗ (6회차) |

**질문과 결정** (2026-11-27 개발자가 1~3 모두 제안대로 승인 → `docs/requirements.md` D-09·D-10)
1. **수료 처리 시작 시점**: D-05는 "회차 종료 후"인데 회차에는 **종료일이 없다**(시작일만). 교재는 "시작일 당일부터". → **회차를 하루 교육으로 보고 시작일 당일부터 처리 가능**(그 전이면 409)을 제안. 대안: 시작일 다음 날부터 / 관리자가 회차를 종료(CLOSED)한 뒤부터
2. **미수료**: D-05의 "미수료"를 새 상태 **`INCOMPLETE`**로 추가(컬럼이 VARCHAR라 마이그레이션 불필요, 부분 유일 인덱스 대상 아님). 처리 API 하나에 결과를 고르게: `outcome: COMPLETED | INCOMPLETE`. **한 번 처리한 신청은 다시 바꾸지 않는다**(이미 처리됨 사유로 실패). 정정 기능은 필요해지면 따로
3. **일부 실패**: 교재대로 **되는 사람만 처리**하고 안 된 사람은 사유와 함께 돌려준다(전체 롤백 아님)

## 2. API

### 2-1. `GET /api/v1/me/enrollments?status=` (FR-07, 로그인)
| 항목 | 내용 |
|---|---|
| `status` | 선택. `CONFIRMED`·`WAITING`·`CANCELED`·`COMPLETED`·`INCOMPLETE`. 없으면 전체. 형식 오류 400 |
| 정렬 | 최근 신청순(id 내림차순) |
| 페이지 | **없음**(한 사원의 신청은 많아야 수십 건, 교재 결정) |
| 200 | `MyEnrollmentResponse[]` |

```json
{ "enrollmentId": 31, "sessionId": 10, "courseId": 3, "courseTitle": "JPA 실전",
  "startDate": "2026-12-10", "place": "본사 3층", "status": "WAITING", "waitingNumber": 2,
  "enrolledAt": "2026-11-27T10:00:00+09:00", "cancelable": true }
```
- `waitingNumber`: 대기일 때만(D-08 계산식), 아니면 null
- `cancelable`: 상태가 확정·대기이고 오늘 < 시작일(D-07). 화면이 취소 버튼을 보일지 정하는 값이며, 실제 판정은 취소 API가 다시 한다

### 2-2. `GET /api/v1/admin/sessions/{sessionId}/roster` (FR-08, ADMIN)
```json
{ "sessionId": 10, "courseId": 3, "courseTitle": "JPA 실전", "startDate": "2026-12-10", "place": "본사 3층",
  "capacity": 15, "sessionStatus": "OPEN", "completionOpen": false,
  "confirmed": [ { "enrollmentId": 5, "employeeId": 1, "employeeNo": "E1001", "name": "김사원", "department": "개발1팀", "status": "CONFIRMED" } ],
  "waiting":   [ { "enrollmentId": 9, "employeeId": 2, "employeeNo": "E1002", "name": "이사원", "department": "개발2팀", "status": "WAITING", "waitingNumber": 1 } ] }
```
- `confirmed`: CONFIRMED·COMPLETED·INCOMPLETE(신청 순) / `waiting`: WAITING(순번 순). 취소는 빼다
- `completionOpen`: 지금 수료 처리가 가능한지(질문 1의 규칙). 화면이 버튼을 켤지 정하는 값
- 사원 정보는 `employee`와 조인(외래 키가 없으므로 **LEFT JOIN** — 사원 행이 없으면 사번·이름 null, 화면은 "알 수 없음")
- 404 `SESSION_NOT_FOUND`, 401/403

### 2-3. `POST /api/v1/admin/sessions/{sessionId}/completions` (FR-08, ADMIN)
| 항목 | 내용 |
|---|---|
| 요청 | `{ "employeeIds": [1, 3], "outcome": "COMPLETED" }` — `employeeIds` 1~100개, 중복은 한 번으로, `outcome` 필수 |
| 200 | `{ "processed": [1], "failed": [ { "employeeId": 3, "reason": "NOT_CONFIRMED" } ] }` |
| 실패 사유 | `NOT_ENROLLED`(이 회차 신청이 없거나 취소만 있음), `NOT_CONFIRMED`(대기), `ALREADY_PROCESSED`(이미 수료·미수료) |
| 400 `INVALID_REQUEST` | 빈 목록·100개 초과·outcome 없음 |
| 404 `SESSION_NOT_FOUND` | |
| 409 `COMPLETION_NOT_OPEN` | 질문 1의 시점 전 |
| 503 `SESSION_BUSY` | 회차 락 3초 초과 |

## 3. 스키마 — `V4__enrollment_employee_index.sql`
```sql
-- FR-07: 사원별 신청 내역 조회 (NFR-02)
CREATE INDEX idx_enrollment_employee ON enrollment (employee_id, id DESC);
```
- 상태 값 `INCOMPLETE` 추가는 enum만 바꾼다(컬럼 VARCHAR(20)). 부분 유일 인덱스(확정·대기)는 그대로

## 4. 도메인·계층
| 위치 | 내용 |
|---|---|
| `EnrollmentStatus` | `INCOMPLETE` 추가 |
| `Enrollment.complete()` / `markIncomplete()` | CONFIRMED에서만. 아니면 예외(서비스가 사유로 바꿈) |
| `enrollment.application.MyEnrollmentQueryService` | FR-07, `@Transactional(readOnly)` |
| `enrollment.application.RosterService` | FR-08 명단 조회 + 수료 처리 |
| `enrollment.api.MyEnrollmentController` | `GET /api/v1/me/enrollments` |
| `enrollment.api.AdminSessionController` | roster, completions |
| `CompletionNotOpenException` | 409 |

- 엔티티를 응답으로 내지 않는다(record DTO)
- 회차 상세(과정 제목·시작일)와 사원 정보는 **쿼리 한 번**(JPQL 생성자 표현식 + 대기 순번 서브쿼리, `Employee`는 `left join ... on`)

## 5. 트랜잭션·동시성
```
RosterService.complete(sessionId, employeeIds, outcome)   @Transactional
 1. 회차 행 락 (FR-04와 같은 setLockTimeout + findByIdForUpdate)   ← "모든 상태 쓰기는 회차 락 먼저" 약속
 2. 없으면 SessionNotFound, 질문 1 시점 전이면 CompletionNotOpen(409)
 3. 이 회차의 해당 사원 신청을 한 번에 조회 (진행·처리 상태만)
 4. 사원마다 CONFIRMED면 complete()/markIncomplete(), 아니면 사유를 모은다
 5. 커밋
```
| 위험 | 대책 |
|---|---|
| 같은 회차를 두 관리자가 동시에 처리 | 회차 락으로 차례로. 두 번째는 ALREADY_PROCESSED |
| 처리와 신청·취소가 겹침 | 수료 시점(시작일 당일~)에는 신청(전날까지)·취소(전날까지)가 이미 불가. 그래도 회차 락을 거쳐 약속을 지킨다 |
| 확정 수 감소로 남은 자리 계산이 바뀜 | 시작일 이후 회차는 신청할 수 없으므로 영향 없음. 남은 자리는 표시용 |
| N+1 | 명단·내역 모두 쿼리 1번(+확정/대기 집계 없음). 테스트로 SQL 수 확인 |

## 6. 테스트 시나리오
| 종류 | 시나리오 | 기대 |
|---|---|---|
| FR-07 | 사원 A·B 신청 섞임 → A 토큰 | A 것만, 최근순 |
| | status=WAITING | 대기만, `waitingNumber` 정확(앞사람 취소 후 당겨짐 포함) |
| | cancelable | 확정·대기 + 시작 전날 true / 당일·취소·수료 false |
| | SQL 수 | 신청 5건이어도 1번 (Hibernate Statistics) |
| | 토큰 없음 401, 잘못된 status 400 | |
| FR-08 명단 | 확정 2·대기 2·취소 1 | 확정 2(사번·이름), 대기 2(순번 순), 취소 없음 |
| | 사원 행 없는 신청 | 사번·이름 null로 나오고 오류 없음 |
| | 사원 토큰 403, 없는 회차 404 | |
| FR-08 처리 | 시점 이후, 확정 2명 COMPLETED | processed 2, 상태 COMPLETED |
| | INCOMPLETE | 상태 INCOMPLETE |
| | 확정 1 + 대기 1 + 신청 없음 1 + 이미 수료 1 | processed 1, failed 3(사유 각각) |
| | 시점 전 | 409 COMPLETION_NOT_OPEN, 아무것도 안 바뀜 |
| | 빈 목록·101개·outcome 없음 | 400 |
| | 같은 요청 두 번 동시 | 한쪽 processed, 다른 쪽 ALREADY_PROCESSED |
| | 처리 후 FR-07 | 해당 사원 내역에 COMPLETED/INCOMPLETE, cancelable false |

## 7. 범위 밖·알려진 제한
| 항목 | 현재 | 이후 |
|---|---|---|
| 처리 정정(수료↔미수료) | 불가 | 필요하면 별도 API |
| 관리자 대리 취소 (D-02) | 없음 | 다음 작업 |
| 회차 종료일 | 없음 (질문 1) | 여러 날 교육이 생기면 종료일 컬럼 |
| 내역 페이지 | 없음 | 수백 건이 되면 |
| 여러 탭 | 다른 탭에서 다른 계정으로 로그인하면 이 탭은 이전 사용자 이름을 보여 주면서 새 토큰으로 요청한다(서버 권한은 토큰 기준이라 안전) | 필요하면 storage 이벤트로 동기화 |
| `/me` 실패(네트워크·5xx) | 토큰은 남기고 화면만 비로그인으로 보인다 — 일시 장애에 로그아웃시키지 않기 위해 | |
