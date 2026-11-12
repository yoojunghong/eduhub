# 설계명세: 수강 신청 (FR-04, NFR-01)

> 범위: 신청 API와 동시성 처리. 대기 순번 부여·대기 인원 상한(FR-05, D-03)과 취소·자동 승급(FR-06)은 하지 않지만,
> 정원이 차면 `WAITING`으로 저장해 다음 작업이 이어 붙일 자리를 남긴다.

## 1. 요구사항 정리
| ID | 내용 | 이번 범위 |
|---|---|---|
| FR-04 | 회차에 수강 신청한다. 같은 회차에 중복 신청할 수 없다 | ✅ |
| D-01 | 신청은 회차 시작 전날까지. 같은 과정에서 진행 중(확정·대기) 신청은 1건만 | ✅ |
| NFR-01 | 100명이 동시에 마지막 자리에 신청해도 정원을 넘지 않는다 | ✅ 동시성 테스트 |
| NFR-03 | 로그인한 사용자만 신청 | ⏳ 인증 전. `X-Employee-Id` 헤더 임시 사용 + `TODO(NFR-03)`. **서버가 검증하지 않으므로 공유 환경 배포 금지** (7장) |
| NFR-04 | OpenAPI 문서, 클라이언트는 스펙에서 생성 | ✅ |
| FR-05 / FR-06 | 대기 순번·상한, 취소·승급 | ✗ (WAITING 저장만) |

**질문과 이번 결정** (개발자 확인 필요)
1. D-01의 "같은 과정에 1건"을 회차 단위보다 넓게 본다 → **유일 인덱스를 `(course_id, employee_id)`에 둔다.** 회차 단위 중복은 자동으로 포함된다
2. 인증 전 employeeId 출처 → 요청 본문이 아니라 **헤더 `X-Employee-Id`**(임시). 인증이 붙으면 로그인 정보로 바꾼다
3. 대기 인원 상한(정원 50%, D-03)은 FR-05에서 적용 → 이번에는 정원 초과분을 모두 WAITING으로 저장

## 2. API
`POST /api/v1/sessions/{sessionId}/enrollments`

| 항목 | 내용 |
|---|---|
| 요청 헤더 | `X-Employee-Id: <long>` (필수, 양수. 임시 — NFR-03. 실제 직원인지 검증하지 않음) |
| 요청 본문 | 없음 |
| 201 | `EnrollmentResponse` + `Location: /api/v1/enrollments/{id}` |
| 400 `INVALID_REQUEST` | 헤더 누락·형식 오류 |
| 404 `SESSION_NOT_FOUND` | 회차가 없음 |
| 409 `SESSION_CLOSED` | 회차 또는 과정이 종료됨 |
| 409 `ENROLLMENT_PERIOD_ENDED` | 오늘이 회차 시작일 이후 또는 당일 (D-01: 전날까지) |
| 409 `DUPLICATE_ENROLLMENT` | 같은 과정에 진행 중 신청이 있음 |
| 503 `SESSION_BUSY` | 회차 락을 3초 안에 얻지 못함 (신청이 몰림. 잠시 후 다시 시도) |

```json
// EnrollmentResponse
{ "id": 1, "sessionId": 10, "courseId": 3, "employeeId": 1001, "status": "CONFIRMED", "enrolledAt": "2026-11-12T10:00:00+09:00" }
```

회차 조회 응답(`SessionResponse.remainingSeats`)은 이제 `정원 - 확정 수`로 계산한다 (FR-02 연계).

## 3. 스키마 변경 — `V2__create_enrollment.sql`
```sql
CREATE TABLE enrollment (
    id          BIGSERIAL PRIMARY KEY,
    session_id  BIGINT      NOT NULL REFERENCES course_session (id),
    course_id   BIGINT      NOT NULL REFERENCES course (id),
    employee_id BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL,
    enrolled_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_enrollment_session_status ON enrollment (session_id, status);
-- D-01: 같은 과정에서 진행 중(확정·대기) 신청은 1인 1건. 동시에 두 번 눌러도 DB가 막는다
CREATE UNIQUE INDEX ux_enrollment_active_course
    ON enrollment (course_id, employee_id)
    WHERE status IN ('CONFIRMED', 'WAITING');
```
- `course_id`는 회차의 과정을 그대로 복사한다(엔티티 팩토리에서만 설정). 과정 단위 유일 인덱스를 위해 둔다
- 대기 순번은 **저장하지 않는다**. FR-05에서 **`id` 순**으로 계산한다 (저장하는 `max+1`은 또 다른 경쟁 조건)
  - `enrolled_at`은 서버마다 시계가 달라 락 순서와 어긋날 수 있다. `id`(BIGSERIAL)는 회차 락 안에서 INSERT할 때 정해지므로 회차별로 락 순서와 같다

## 4. 도메인
- `EnrollmentStatus { CONFIRMED, WAITING, CANCELED, COMPLETED }` (이번에 만드는 것은 앞의 둘)
- `Enrollment.confirm(session, employeeId, now)`, `Enrollment.waiting(session, employeeId, now)` 정적 팩토리. setter 없음
- 예외: `SessionNotFoundException`(404), `SessionClosedException`(409), `EnrollmentPeriodEndedException`(409), `DuplicateEnrollmentException`(409)

## 5. 트랜잭션·동시성
```
EnrollmentService.enroll(sessionId, employeeId)   @Transactional (한 트랜잭션)
 1. sessionRepository.findByIdForUpdate(sessionId)       ← 회차 행 SELECT … FOR UPDATE (락 대기 최대 3초)
      없으면 SessionNotFoundException
 2. 회차·과정이 OPEN인가 → 아니면 SessionClosedException
 3. today < startDate 인가 → 아니면 EnrollmentPeriodEndedException
 4. enrollmentRepository.existsActiveInCourse(courseId, employeeId) → 있으면 DuplicateEnrollmentException
 5. confirmed = countBySessionIdAndStatus(sessionId, CONFIRMED)
 6. confirmed < capacity ? confirm : waiting
 7. saveAndFlush — 유일 인덱스 위반(DataIntegrityViolationException)은 DuplicateEnrollmentException
커밋 → 락 해제
```
| 위험 | 대책 |
|---|---|
| 정원 초과 (조회-판단-저장 사이 끼어들기) | 1의 **회차 행 비관적 락**. 같은 회차 신청은 줄을 서고, 5의 개수는 락 안에서 센다 |
| 같은 사람이 같은 과정의 **다른 회차**에 동시에 신청 | 회차 락은 서로 다르므로 4로는 못 막음 → **과정 단위 부분 유일 인덱스**가 최종 보장, 위반은 409 |
| 교착 | 한 트랜잭션에서 잠그는 행은 회차 하나뿐. 과정 행은 잠그지 않는다(`join fetch` 없이 회차만 `FOR NO KEY UPDATE`) |
| 락 대기 누적 | 락 조회 직전 `set_config('lock_timeout', '3s', true)` (트랜잭션 로컬). JPA `lock.timeout` 힌트는 PostgreSQL에서 무시되므로 쓰지 않는다. 트랜잭션 안에서 외부 호출 금지 |
| lock_timeout 범위 | 같은 트랜잭션의 **모든** 락 대기에 적용된다. 같은 사람이 다른 회차에 동시에 신청해 유일 인덱스에서 3초 넘게 기다리면 409 대신 503이 될 수 있다 (허용) |
| 서버 여러 대 | `synchronized` 등 JVM 락은 쓰지 않는다. DB 락·제약만 사용 |
| **이후 기능과의 약속** | enrollment 상태를 바꾸는 **모든 쓰기**(FR-06 취소·승급, D-04 정원 변경·종료, 관리자 대리 처리)는 **같은 회차 행 락을 먼저** 잡는다. `FOR NO KEY UPDATE`는 FK 검사의 `KEY SHARE`를 막지 않으므로, 락을 거치지 않는 쓰기는 정원 계산과 동시에 실행될 수 있다 |
| 확정 판단식 | 지금은 `confirmed < capacity ? 확정 : 대기`. 취소·정원 증가가 없어 문제없지만, FR-05/06에서 **"대기자가 없고 자리가 있을 때만 확정"**으로 바꾼다 (대기자 새치기 방지) |

## 6. 테스트 시나리오
| 종류 | 시나리오 | 기대 |
|---|---|---|
| Service (내장 PG) | 자리 있음 | CONFIRMED |
| | 정원 참 | WAITING |
| | 같은 회차 재신청 / 같은 과정 다른 회차 신청 | 409 DUPLICATE |
| | 회차·과정 종료 | 409 SESSION_CLOSED |
| | 시작일 당일·지난 회차 | 409 PERIOD_ENDED |
| | 없는 회차 | 404 |
| Controller (WebMvc) | 201 + Location, 400(헤더 없음), 404, 409 | 상태·code |
| 조회 | 확정 2명인 정원 20 회차 | remainingSeats 18 |
| **동시성 (NFR-01)** | 정원 10, 서로 다른 100명 동시 신청 (`CountDownLatch`) | 확정 10, 대기 90 |
| **동시성** | 같은 사람이 같은 회차에 10번 동시 신청 | 진행 중 신청 1건, 나머지 409 |
| 테스트의 테스트 | `@Lock` 주석 처리 후 실행 | 확정이 10을 넘어 **실패** |
| 동시성 | 같은 사람이 같은 과정의 다른 회차에 동시 신청 | 1건만 성공, 나머지 409 |
| 유일 인덱스 경로 | A가 커밋 전 대기 중일 때 B가 다른 회차 신청 → B가 인덱스에서 기다리는 것을 `pg_stat_activity`로 확인 후 A 커밋 | B는 원인이 `DataIntegrityViolationException`인 409 |
| 락 대기 제한 | 다른 트랜잭션이 회차를 5초 잡고 있음 | 약 3초 뒤 `SESSION_BUSY`(503) |
| 날짜 경계 (KST) | 시작 전날 23:59:59.999 / 시작일 00:00 | 확정 / 409 PERIOD_ENDED |

> 동시성 테스트는 미리 채운 커넥션 풀(`PrefilledPoolTestConfig`)로 실행한다. 풀이 없으면 연결 생성 지연 때문에 신청이 차례로 실행되어 락을 빼도 통과한다.

## 7. 범위 밖·알려진 제한
| 항목 | 현재 | 이후 |
|---|---|---|
| **NFR-03 인증** | `X-Employee-Id` 헤더를 서버가 검증하지 않는다. 다른 사람 번호·없는 번호로도 신청되고, 번호를 바꿔 가며 자리를 모두 채울 수 있다 | 인증이 붙기 전에는 **공유 환경에 배포하지 않는다**. 인증 후 헤더를 없애고 로그인 정보에서 꺼낸다 |
| **D-04 종료 시 신청 취소** | 과정·회차를 종료해도 진행 중(확정·대기) 신청이 남는다 | 구현 순서: **회차 행을 id 순으로 먼저 잠그거나 UPDATE한 뒤** enrollment 일괄 취소. 거꾸로 하면 아직 커밋되지 않은 신청을 일괄 취소가 놓쳐 종료된 회차에 확정이 남는다 |
| D-04 정원 변경 | 정원 변경 API 없음 | 정원 증가 시 대기 순서(id)대로 승급. 회차 락 안에서 |
| 날짜 기준 | `Clock`은 `Asia/Seoul` 고정 | 사내 시스템이라 KST 하나로 충분 |
| remainingSeats | 화면 표시용. 회차 조회와 확정 수 조회가 다른 문장이라 순간적으로 어긋날 수 있다 | 정원 감소(D-04)가 생기면 음수 보정(0) 대신 검증·로그 |
