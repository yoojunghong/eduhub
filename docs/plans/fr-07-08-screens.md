# 구현계획: FR-07 내 신청 내역 · FR-08 명단·수료 · 로그인과 화면 (Step 10~16)

설계: `docs/design/my-enrollments-roster.md`, `docs/design/login-screens.md` (결정 D-09·D-10).
백엔드 루트 `backend/src/main/java/com/example/eduhub`(`…`), 테스트 루트 `backend/src/test/java/com/example/eduhub`(`t…`), 화면 루트 `frontend/apps/course-mfe/src`(`f…`).
백엔드는 `.claude/skills/eduhub-api/SKILL.md`, 화면은 `frontend/CLAUDE.md`를 따른다. Java·TS는 Edit/Write로 고친다. 백엔드 완료 명령에는 `spotlessCheck`, 화면은 `npm test`·`typecheck`를 포함한다.

## 백엔드
| 작업 | 변경 파일 | 내용 | 완료 명령 |
|---|---|---|---|
| B1 도메인·쿼리 | `db/migration/V4__enrollment_employee_index.sql`, `…/enrollment/domain/{EnrollmentStatus,Enrollment,EnrollmentRepository}.java`, 신규 projection record(`…/enrollment/domain/MyEnrollmentRow`, `RosterRow` 등), `t…/enrollment/domain/{EnrollmentTest,EnrollmentRepositoryTest}.java` | `INCOMPLETE` 추가. `complete()`·`markIncomplete()`(CONFIRMED에서만, 아니면 예외). 쿼리: ① 사원별 내역 — 회차·과정 조인 + 대기 순번 서브쿼리(같은 회차 WAITING 중 id ≤ 내 id), 상태 필터 선택, id 내림차순, **SQL 1번** ② 명단 — 회차의 확정·처리·대기 신청 + `left join Employee m on m.id = e.employeeId`(사번·이름·부서), id 순 ③ `findBySessionIdAndEmployeeIdIn`. 테스트: 각 쿼리, 사원 행 없는 신청도 결과에 나옴, 순번(앞사람 취소 후 당겨짐), 도메인 전이 | `./gradlew test --tests "*Enrollment*"` |
| B2 FR-07 | `…/enrollment/application/MyEnrollmentQueryService.java`, `…/enrollment/api/{MyEnrollmentController,MyEnrollmentResponse}.java`, `…/common/api/GlobalExceptionHandler.java`(필요 시), `t…/enrollment/api/MyEnrollmentApiTest.java`(SpringBootTest+MockMvc, 실제 토큰 또는 TestAuth) | `GET /api/v1/me/enrollments?status=`(설계 2-1). 사원은 `CurrentUser.from(jwt)`. `cancelable` = 확정·대기 && 오늘(주입 Clock) < 시작일. `enrolledAt` KST. OpenAPI(@Operation, 200/400/401). 테스트: 설계 6장 FR-07 행 전부, **Hibernate Statistics로 SQL 수 1** | `./gradlew test --tests "*MyEnrollment*"` |
| B3 FR-08 | `…/enrollment/application/{RosterService,CompletionNotOpenException,CompletionOutcome}.java`, `…/enrollment/api/{AdminSessionController,RosterResponse,CompletionRequest,CompletionResult}.java`, `GlobalExceptionHandler.java`, `t…/enrollment/api/AdminSessionApiTest.java`, `t…/enrollment/CompletionConcurrencyTest.java` | 명단(설계 2-2, `completionOpen` = 오늘 ≥ 시작일, D-09)과 처리(2-3, 설계 5장 순서: **회차 락 먼저** — 기존 `setLockTimeout`+`findByIdForUpdate`). 요청 검증(1~100개, 중복 제거, outcome 필수), 실패 사유 3종, 409 `COMPLETION_NOT_OPEN`. OpenAPI. 테스트: 설계 6장 FR-08 행 전부 + 사원 403. 동시성: 같은 요청 2개 동시 → 한쪽 processed, 다른 쪽 ALREADY_PROCESSED(기존 동시성 테스트 구성 따름) | `./gradlew test --tests "*AdminSession*" --tests "*CompletionConcurrency*"` |
| B4 개발 데이터 | `t…/LocalDevConfiguration.java` | 수료 확인용으로 **이미 시작한 회차**(시작일 = 오늘) 하나와 E1001·E1002 확정 신청을 만든다(교재 6회차 확인 데이터와 같음). 기존 데이터 유지 | `./gradlew test` (+ 메인 bootTestRun 확인) |

(메인: 백엔드를 띄워 `npm run generate:api` → 생성 클라이언트 커밋)

## 화면
| 작업 | 변경 파일 | 내용 | 완료 명령 |
|---|---|---|---|
| F1 로그인 기반 | `f…/api/{tokenStore,clients,unauthorized}.ts`(+test), 기존 `api/courseApi.ts` 제거·import 정리, `f…/auth/{AuthContext,RequireAuth,LoginPage}.tsx`(+test), `f…/Layout.tsx`(+test), `f…/App.tsx`, `f…/main.tsx`, `apps/course-mfe/package.json`(axios 1.21.0 — api-client와 같은 버전, 인터셉터용), `frontend/CLAUDE.md`(경로 한 줄) | 설계 login-screens 3장 그대로. 토큰은 `Configuration.accessToken`, 401은 생성 클라이언트가 쓰는 기본 axios에 응답 인터셉터 **하나**(로그인 요청 제외, 403 제외). **`axios.create` 금지.** 라우트 `/login`, 보호 라우트 래핑. 상단 바 탭(교육과정 / 내 신청 내역 로그인 시 / 과정 관리 관리자만) + 사용자 영역·로그아웃. 개발 모드에서만 계정 안내. 테스트: 설계 7장 tokenStore·401·AuthContext·RequireAuth·LoginPage·상단 바 행 | `npm test -w apps/course-mfe && npm run typecheck -w apps/course-mfe` |
| F2 수강생 화면 | `f…/enrollment/{EnrollButton.tsx, MyEnrollmentsPage.tsx, hooks/useEnroll.ts, hooks/useMyEnrollments.ts, hooks/useCancelEnrollment.ts}`(+test), `f…/pages/CourseDetailPage.tsx`(+test), 기존 `hooks/useEnroll.ts` 이동, `App.tsx`(`/my/enrollments` RequireAuth) | 설계 4장. 상세 신청 버튼은 `enrollment/EnrollButton`으로 옮기고 비로그인이면 "로그인 후 신청". 내 신청 내역: 필터·표·대기 N번·취소(확인 대화상자, 성공 후 다시 조회, 409 안내)·빈 목록 | 위와 같음 |
| F3 관리자 화면 | `f…/enrollment/{RosterPage.tsx, hooks/useRoster.ts, hooks/useCompletion.ts}`(+test), `f…/pages/admin/CourseAdminDetailPage.tsx`(+test, 회차 행 **명단** 버튼), `App.tsx`(`/admin/*` RequireAuth admin, `/admin/sessions/:id/roster`) | 설계 5장. 체크박스는 CONFIRMED만, 수료·미수료 버튼, `completionOpen` false면 비활성+안내, 결과 안내(실패 이름·사유), 처리 후 다시 조회, 사원 정보 없으면 "알 수 없음" | 위와 같음 |

**순서**: B1 → B2 → B3 → B4 → (클라이언트 재생성) → F1 → F2 → F3 → 메인 브라우저 확인 → critical-reviewer

**위험 영역**
- B1 대기 순번 서브쿼리와 SQL 1번 — 행마다 다시 조회하는 계획이면 거절
- B3 회차 락 순서와 일부 실패 처리(예외로 전체 롤백되지 않게: 사유를 모으고 처리 가능한 것만 상태 변경)
- F1 401 처리: 로그인 요청의 401로 이벤트가 나가면 로그인 화면이 깜빡이며 오류 문구가 사라진다. 403에 로그아웃하면 안 된다
- F1~F3 경계: `enrollment/` → `pages/` import 금지, 화면에서 axios·fetch 금지
- 서브에이전트 Gradle·Stop 훅 동시 실행 충돌 → 작업마다 커밋 후 다음 작업

**규모 안내**: 백엔드 4작업 + 화면 3작업, 파일 40개 안팎·diff 2,000줄 이상 예상 (CLAUDE.md 절차 5 — 개발자에게 알림). 백엔드(B1~B4)와 화면(F1~F3)은 커밋을 나눠 리뷰하기 쉽게 한다
