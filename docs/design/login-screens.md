# 설계명세: 로그인과 화면 완성 (Step 13~16)

> 교재 5회차 6장. 지금 하나의 React 앱(`frontend/apps/course-mfe`, :3000)에 로그인과 나머지 화면을 붙여 FR-01~08을 브라우저에서 끝까지 쓸 수 있게 한다.
> 6회차에 shell·course-mfe·enrollment-mfe로 쪼개므로 **폴더를 갈 곳별로 나눠 둔다.**

## 1. 기준과 이 저장소에 맞춘 점
| 교재 | 이 저장소 | 이유 |
|---|---|---|
| 포트 3001, 경로 `/courses` | **포트 3000, 목록 `/`, 관리 `/admin`** 그대로 | 이미 쓰는 경로·문서를 바꾸지 않는다 |
| `src/api/http.ts`에서 axios 인스턴스 생성 | **`axios.create` 금지**(frontend/CLAUDE.md) → 생성 클라이언트의 `Configuration.accessToken`으로 토큰을 붙이고, 401은 생성 클라이언트가 쓰는 기본 axios에 응답 인터셉터 한 개 | 팀 규칙 유지. 토큰·401 처리는 `src/api/` 한 곳 |
| Vitest | **Jest**(현재 설정) | |
| 상세 화면의 신청 버튼 `enrollment/EnrollButton` | 현재 `useEnroll` + 상세 화면 버튼을 `enrollment/` 폴더로 옮겨 정리 | 6회차 분리 대비 |

## 2. 화면 지도
| 경로 | 화면 | 요구사항 | 누가 | 폴더 (6회차에 갈 곳) |
|---|---|---|---|---|
| `/login` | 로그인 | NFR-03 | 누구나 | `auth/` (shell) |
| `/` | 과정 목록 (기존) | FR-01 | 누구나 | `pages/` (course-mfe) |
| `/courses/:id` | 과정 상세 + 회차별 신청 버튼 (기존 + 로그인 안내) | FR-02·04·05 | 누구나(신청은 로그인) | `pages/` + `enrollment/` |
| `/my/enrollments` | **내 신청 내역**(상태 필터), 취소 | FR-06·07 | 로그인 | `enrollment/` (enrollment-mfe) |
| `/admin`, `/admin/courses/:id` | 과정 관리 (기존) + 회차 행에 **명단** 링크 | FR-03 | 관리자 | `pages/admin/` (course-mfe) |
| `/admin/sessions/:id/roster` | **회차 명단, 수료·미수료 처리** | FR-08 | 관리자 | `enrollment/` (enrollment-mfe) |

- 상단 바: 탭 **교육과정** / **내 신청 내역**(로그인 시) / **과정 관리**(관리자일 때만). 오른쪽에 이름·사번·역할과 **로그아웃**, 비로그인이면 **로그인** 버튼
- 화면 확인용 사진(다른 팀 결과)과 레이아웃은 달라도 된다

## 3. 로그인·토큰 (Step 14)
| 요소 | 동작 |
|---|---|
| `api/tokenStore.ts` | `localStorage`의 `eduhub.accessToken`, `eduhub.user`. get/set/clear |
| `api/clients.ts` (기존 `courseApi.ts` 대체) | `new Configuration({ basePath: '', accessToken: () => tokenStore.get() ?? '' })`로 `courseApi`, `enrollmentApi`, `authApi`, `myEnrollmentApi`, `adminSessionApi` 생성(이름은 생성 결과에 맞춤). **생성 클라이언트 인스턴스는 여기서만** |
| 401 처리 | 응답 인터셉터: 401이면 토큰을 지우고 `eduhub:unauthorized` 이벤트. **로그인 요청 자체의 401은 제외**(틀린 비밀번호). **403은 로그아웃하지 않는다**(권한 없음 안내만) |
| `auth/AuthContext` | `user`, `login(no, pw)`, `logout()`, `expired`. 새로고침 시 토큰이 있으면 `/api/v1/me`로 확인(401이면 비로그인). `eduhub:unauthorized`를 받으면: 보호 화면이면 `/login`으로(원래 경로 보관, 만료 안내), 공개 화면이면 토큰만 지우고 화면이 다시 불러온다(공개 GET 만료 토큰 401 결정에 맞춤) |
| `auth/RequireAuth` | 비로그인 → `/login`(state.from). `admin` 옵션이면 관리자가 아닐 때 "권한이 없습니다" 화면(로그아웃 아님) |
| `auth/LoginPage` | 사번·비밀번호, 실패 "사번 또는 비밀번호가 올바르지 않습니다.", 성공 시 원래 가려던 곳(없으면 `/`). 만료로 왔으면 "다시 로그인해 주세요" — 질문 1 |

## 4. 수강생 화면 (Step 15)
- **상세의 신청 버튼**: 비로그인 → "로그인 후 신청"(`/login`, 돌아올 경로 포함). 기존 신청·대기·마감 규칙과 안내 문구는 그대로. 성공 후 상세 다시 조회
- **내 신청 내역** `/my/enrollments`: 상태 필터(전체·확정·대기·취소·수료·미수료), 표(과정명→상세 링크, 시작일, 장소, 상태 칩 — 대기는 "대기 N번", 신청일시), `cancelable`이면 **취소** 버튼 → 확인 대화상자 → 취소 API → 성공 안내 후 **목록 다시 조회**(승급은 서버가 한다). 409(마감·이미 처리)는 서버 메시지 안내. 비어 있으면 "신청 내역이 없습니다"
- 화면은 훅만 쓴다(`enrollment/hooks/useMyEnrollments`, `useCancelEnrollment`)

## 5. 관리자 화면 (Step 16)
- **과정 관리 상세**의 회차 행에 **명단** 버튼 → `/admin/sessions/:id/roster`
- **명단** 화면: 과정명·시작일·장소·정원, 확정자 표(체크박스는 CONFIRMED만, 이름·사번·부서·상태 칩), 대기자 표(순번 순). 버튼 "선택한 N명 수료 처리" / "선택한 N명 미수료 처리" — `completionOpen`이 false면 비활성 + "수료 처리는 회차 시작일부터 할 수 있습니다." 처리 결과 안내(처리 N명, 실패는 이름과 사유), 성공 후 **명단 다시 조회**. 사원 정보가 없으면 "알 수 없음"
- 실패 사유 문구: NOT_ENROLLED "이 회차 신청 내역 없음", NOT_CONFIRMED "확정 상태가 아님", ALREADY_PROCESSED "이미 처리됨"

## 6. 경계 규칙 (6회차 대비, 리뷰 체크리스트)
- `enrollment/`는 `pages/`를 import하지 않는다(반대 방향은 신청 버튼 하나만 허용)
- 화면 파일에서 `axios`·`fetch`·`localhost:8080`을 쓰지 않는다. API는 `src/api/clients.ts`의 인스턴스로만
- 테마는 `main.tsx` 한 곳

## 7. 테스트 (Jest + Testing Library)
| 대상 | 시나리오 |
|---|---|
| tokenStore·401 처리 | 토큰 있으면 Authorization 헤더, 없으면 없음 / 401이면 토큰 삭제·이벤트 / 로그인 요청 401은 이벤트 없음 / 403은 토큰 유지 |
| AuthContext·RequireAuth | 새로고침 시 /me 확인, 비로그인 보호 화면 → /login(원래 경로 보관), 401이 두 번 와도 원래 경로 유지, 사원이 관리자 화면 → 권한 없음(로그아웃 안 됨) |
| LoginPage | 성공 시 원래 경로로, 실패 문구 |
| 상세 신청 버튼 | 비로그인이면 "로그인 후 신청" |
| 내 신청 내역 | 목록·필터·대기 N번, 취소 후 다시 조회, 409 안내, 빈 목록 |
| 명단 | 표시, 시점 전 버튼 비활성, 처리 결과(일부 실패 사유), 처리 후 다시 조회 |
| 상단 바 | 비로그인/사원/관리자별 탭 |

**브라우저 확인**(메인이 미리보기로): E1001 로그인 → 상세에서 신청 → 내 신청 내역 → 취소 / A0001 로그인 → 과정 관리 → 명단 → 수료 처리 / 사원으로 `/admin` → 권한 없음 / 로그아웃 후 `/my/enrollments` → 로그인 → 원래 화면

## 8. 질문과 결정 (2026-11-27 제안대로 승인)
1. **로그인 화면의 개발용 계정 안내**: 개발 모드(`import.meta.env.DEV`)에서만 "개발용 계정: E1001·E1002·A0001 / dev-password"를 작게 보여 주기를 제안. 운영 빌드에는 나오지 않는다
2. **수료 시점·미수료**는 `my-enrollments-roster.md` 질문 1·2를 따른다
