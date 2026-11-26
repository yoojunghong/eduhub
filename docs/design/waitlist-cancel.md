# 설계명세: 대기자 등록·순번 (FR-05), 신청 취소·자동 승급 (FR-06)

> 바탕: `docs/design/enrollment.md`(FR-04). 그 문서 5장의 약속을 지킨다:
> **enrollment 상태를 바꾸는 모든 쓰기는 같은 회차 행 락을 먼저 잡는다**, 대기 순번은 **저장하지 않고 id 순으로 계산**한다.

## 1. 요구사항 정리
| ID | 내용 | 이번 범위 |
|---|---|---|
| FR-05 | 정원이 차면 대기자로 등록되고 대기 순번을 알려 준다 | ✅ |
| FR-06 | 신청을 취소하면 대기 1순위가 자동으로 신청 확정된다 | ✅ |
| D-02 | 취소는 회차 시작 1일 전까지 본인만. 관리자는 예외적으로 대리 취소 | ✅ 본인 취소 / ⏳ 관리자 대리 취소는 인증(NFR-03) 뒤 |
| D-03 | 대기 인원은 정원의 50%까지. 회차 시작 1일 전부터는 자동 승급하지 않는다 | ✅ |
| NFR-01 | 동시 요청에도 정원·순번이 어긋나지 않는다 | ✅ 동시성 테스트 3개 추가 |
| NFR-03 | 본인 확인 | ⏳ FR-04와 같이 `X-Employee-Id` 헤더 임시 사용 + `TODO(NFR-03)` |
| D-04 | 정원 변경·회차 종료 시 승급·일괄 취소 | ✗ (FR-03 후속) |
| FR-09 | 확정·승급 알림 | ✗ (6회차). 승급 지점을 한 메서드로 모아 이벤트를 붙일 자리만 남긴다 |

**질문과 결정** (2026-11-26 개발자가 제안대로 승인 → `docs/requirements.md` D-06~D-08, 5는 이 문서 2-2)
1. **대기 상한 계산**: 정원 × 50%를 **내림**(정원 1 → 대기 0명, 정원 15 → 7명)으로 제안. "50%까지"를 넘지 않는 해석. 올림을 원하면 정원 1에도 대기 1명
2. **취소 마감**: D-01(신청은 "전날까지")과 같게 **오늘 < 시작일**이면 취소 가능(전날까지 가능, 당일부터 불가)으로 제안
3. **승급 중단 시점**: D-03 "시작 1일 전부터 승급 안 함" → **오늘 ≥ 시작일 − 1일**이면 확정자가 취소해도 대기자를 올리지 않고 자리만 빈다. (전날 취소분은 승급 없음. 전날 갑자기 확정되면 참석 준비가 어렵기 때문)
4. **취소 후 대기 순번**: 순번을 저장하지 않으므로 앞사람이 빠지면 자동으로 **당겨진다**(3번 → 2번). 순번 "유지"는 하지 않는다
5. **취소 API 모양**: 상태 변경이므로 `DELETE`가 아니라 `POST /api/v1/enrollments/{id}/cancel`로 제안

## 2. API

### 2-1. 신청 (FR-04 API 확장) `POST /api/v1/sessions/{sessionId}/enrollments`
| 변경 | 내용 |
|---|---|
| 확정 조건 | `대기자 없음 && 확정 < 정원`일 때만 확정. **대기자가 있으면 자리가 비어 있어도 대기** (대기자 새치기 방지. 1-3의 "승급 안 함" 기간에 빈 자리가 생겨도 새 신청이 대기열을 건너뛰지 않는다) |
| 대기 상한 | 대기로 가야 하는데 `대기 수 ≥ floor(정원 × 0.5)`이면 **409 `WAITLIST_FULL`** (저장하지 않음) |
| 응답 | `EnrollmentResponse`에 **`waitingNumber`** 추가 (`WAITING`이면 1부터의 순번, 그 밖에는 `null`) |

```json
{ "id": 31, "sessionId": 10, "courseId": 3, "employeeId": 1001, "status": "WAITING", "waitingNumber": 2, "enrolledAt": "2026-11-26T10:00:00+09:00" }
```

### 2-2. 취소 (새 API) `POST /api/v1/enrollments/{enrollmentId}/cancel`
| 항목 | 내용 |
|---|---|
| 요청 헤더 | `X-Employee-Id` (임시, NFR-03) |
| 200 | 취소된 신청의 `EnrollmentResponse` (`status: CANCELED`, `waitingNumber: null`). 누가 승급됐는지는 응답에 넣지 않는다(다른 사람 정보) |
| 400 `INVALID_REQUEST` | 헤더 누락·형식 오류 |
| 404 `ENROLLMENT_NOT_FOUND` | 신청이 없거나 **본인 것이 아님** (남의 신청 존재 여부를 알려 주지 않으려고 403 대신 404) |
| 409 `ENROLLMENT_NOT_CANCELABLE` | 이미 취소·수료된 신청 |
| 409 `CANCEL_PERIOD_ENDED` | 오늘 ≥ 시작일 |
| 503 `SESSION_BUSY` | 회차 락을 3초 안에 얻지 못함 |

## 3. 스키마
**변경 없음.** 대기 순번은 계산, 상태 값 `CANCELED`는 이미 enum에 있다.
- 부분 유일 인덱스 `ux_enrollment_active_course`(확정·대기만)가 그대로 동작 → 취소 후 같은 과정 재신청 가능
- 승급 대상 조회 `WHERE session_id = ? AND status = 'WAITING' ORDER BY id LIMIT 1`은 기존 `idx_enrollment_session_status (session_id, status)`를 쓴다. 회차당 대기 최대 정원의 50%라 정렬 비용은 작다

## 4. 도메인
- `Enrollment.cancel()`: `CONFIRMED`/`WAITING` → `CANCELED`. 그 밖의 상태면 `EnrollmentNotCancelableException`
- `Enrollment.promote()`: `WAITING` → `CONFIRMED`. 그 밖의 상태면 `IllegalStateException`(프로그래밍 오류)
- 순번 계산: `EnrollmentRepository.countWaitingAhead(sessionId, id)` = 같은 회차 `WAITING` 중 `id ≤ 내 id` 개수
- 대기 상한: `CourseSession.waitlistLimit()` = `capacity / 2` (정수 나눗셈 = 내림, 질문 1)
- 예외: `EnrollmentNotFoundException`(404), `EnrollmentNotCancelableException`(409), `CancelPeriodEndedException`(409), `WaitlistFullException`(409)

## 5. 트랜잭션·동시성

### 5-1. 신청 (기존 1~7단계 중 5~6 변경)
```
 1~4. (FR-04 그대로) 회차 행 FOR NO KEY UPDATE(최대 3초) → OPEN 확인 → 기간 확인 → 중복 확인
 5. confirmed = count(CONFIRMED), waiting = count(WAITING)      ← 둘 다 락 안에서
 6. waiting == 0 && confirmed < capacity → 확정
    else waiting < capacity / 2         → 대기 (순번 = waiting + 1)
    else                                 → WaitlistFullException
 7. saveAndFlush (FR-04 그대로)
```
- 순번 `waiting + 1`이 맞는 이유: 같은 회차의 신청·취소는 회차 락으로 줄을 서므로, 락 안에서 센 대기 수에 새 행(가장 큰 id)을 더한 값이 계산식(`id ≤ 내 id`)과 같다

### 5-2. 취소
```
EnrollmentService.cancel(enrollmentId, employeeId)   @Transactional
 1. sessionId = enrollmentRepository.findSessionIdByIdAndEmployeeId(...)   ← 락 없이 회차 번호만
      없으면 EnrollmentNotFoundException
 2. 회차 행 FOR NO KEY UPDATE (FR-04와 같은 메서드, lock_timeout 3s)
 3. enrollment = findById (락을 잡은 뒤에 읽어야 최신 상태)
 4. today ≥ startDate → CancelPeriodEndedException
 5. wasConfirmed = enrollment.isConfirmed(); enrollment.cancel()
 6. wasConfirmed && today < startDate − 1일 이면:
      findFirstWaiting(sessionId) (id 순 1건) → 있으면 promote()     ← 승급 지점 (FR-09 이벤트 자리)
 7. 커밋 → 락 해제
```
| 위험 | 대책 |
|---|---|
| 취소와 신규 신청 동시 → 신규가 대기자를 건너뛰고 확정 | 둘 다 회차 락으로 줄을 선다. 취소가 먼저면 승급이 같은 트랜잭션에서 자리를 채워 신규는 대기. 신규가 먼저면 정원이 차 있어 대기 |
| 두 확정자가 동시에 취소 → 같은 대기자를 두 번 승급 | 회차 락으로 차례로 실행. 두 번째 취소는 첫 번째 승급이 커밋된 뒤 다음 대기자를 본다 (`SKIP LOCKED` 불필요) |
| 1단계에서 읽은 상태가 락 사이에 바뀜 | 1단계는 회차 번호만 쓰고, 상태 검사는 3단계(락 후 읽기)에서 한다. 신청의 회차는 바뀌지 않는다 |
| 교착 | 한 트랜잭션에서 잠그는 행은 회차 하나. enrollment 행은 락 없이 UPDATE(회차 락이 직렬화) |
| 승급 안 함 기간에 생긴 빈 자리 | 5-1의 "대기자가 있으면 대기" 규칙으로 새 신청이 대기열을 건너뛰지 못한다. 빈 자리는 그대로 둔다 (D-04 정원 변경 시 처리) |

## 6. 테스트 시나리오
| 종류 | 시나리오 | 기대 |
|---|---|---|
| 도메인 | `cancel()` 확정·대기 → CANCELED / 취소·수료 → 예외. `promote()` 대기만 | |
| Service | 정원 2, 3번째·4번째 신청 | WAITING, 순번 1·2 |
| | 정원 2(대기 상한 1), 4번째 신청 | 409 WAITLIST_FULL, 저장 안 됨 |
| | 정원 1(대기 상한 0), 2번째 신청 | 409 WAITLIST_FULL |
| | 확정자 취소(시작 7일 전), 대기 2명 | 대기 1순위 CONFIRMED, 2순위 순번 1로 당겨짐 |
| | 대기자 취소 | 아무도 승급 안 함, 뒤 순번 당겨짐 |
| | 확정자 취소, 오늘 = 시작일 − 1 | 취소 성공, 승급 없음 |
| | 위 상태(빈 자리 + 대기자 있음)에서 새 신청 | WAITING (새치기 없음). *새 신청은 D-01상 전날에도 가능* |
| | 오늘 = 시작일에 취소 | 409 CANCEL_PERIOD_ENDED |
| | 남의 신청 취소 / 없는 신청 | 404 |
| | 이미 취소된 신청 재취소 | 409 NOT_CANCELABLE |
| | 취소 후 같은 과정 재신청 | 성공 (부분 유일 인덱스) |
| Controller | 취소 200, 헤더 없음 400, 404, 409 두 종류. 신청 409 WAITLIST_FULL. 응답에 `waitingNumber` | |
| **동시성** | 정원 10(대기 상한 5), 서로 다른 30명 동시 신청 | 확정 10, 대기 5(순번 1~5 중복·누락 없음), 15명 WAITLIST_FULL |
| **동시성** | 정원 10 꽉 참 + 대기 3: 확정자 5명 동시 취소 | 확정 8(=5 남음+3 승급), 대기 0, 승급자는 원래 대기 1~3순위 |
| **동시성** | 정원 10 꽉 참 + 대기 1: 확정자 1명 취소와 새 신청 10명 동시 | 확정 10 유지, 원래 대기자가 확정, 새 신청은 대기(상한까지)·나머지 WAITLIST_FULL |
| 테스트의 테스트 | 취소 경로의 회차 락을 빼고 위 동시성 실행 | 실패해야 함 (락이 실제로 지키는지) |

## 7. 범위 밖·알려진 제한
| 항목 | 현재 | 이후 |
|---|---|---|
| NFR-03 | 헤더 임시. 남의 번호로 남의 신청을 취소할 수 있다 → 공유 환경 배포 금지 (FR-04와 같음) | 인증 후 로그인 정보로 |
| 관리자 대리 취소 (D-02) | 없음 | 인증·권한(관리자 역할) 후 같은 `cancel` 흐름에 "본인 확인 생략" 경로 |
| FR-09 알림 | 없음 | 승급 지점(5-2의 6) 한 곳에서 이벤트 발행 |
| 프런트 | 신청 결과 안내에 "대기 N번" 표시만 | 취소 버튼은 FR-07 "내 신청 내역" 화면에서 |
| 종료(CLOSED) 회차의 취소 | 회차·과정 상태를 보지 않으므로, 종료된 회차에서 확정자가 취소하면 대기자가 승급된다 | D-04 구현 때 종료 시 진행 중 신청을 일괄 취소하면 대기자가 남지 않아 해소된다 |
| 테스트 한계 | Service 테스트(`@DataJpaTest`)는 한 트랜잭션이라 "락을 잡은 뒤 읽기"를 검증하지 못한다 | 동시성 테스트(T6)와 락 제거 실험이 맡는다 |
