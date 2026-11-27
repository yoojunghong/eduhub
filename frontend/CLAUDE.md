# frontend/CLAUDE.md

npm workspaces: `apps/course-mfe`(React 18 + MUI, Vite), `packages/api-client`(생성 클라이언트).

- 실행: `npm run dev -w apps/course-mfe` → http://localhost:3000 (`/` 목록, `/admin` 과정 관리. `/api`는 Vite 프록시로 8080에 전달)
- 테스트·타입 검사: `npm test -w apps/course-mfe`, `npm run typecheck -w apps/course-mfe`
- API 클라이언트 재생성(백엔드 실행 중): `npm run generate:api`. `packages/api-client/src/generated`는 직접 고치지 않는다

- UI는 MUI 컴포넌트를 쓴다
- API 호출은 OpenAPI 스펙에서 생성한 Axios 클라이언트만 쓴다 (`axios.create` 직접 사용 금지)
- 서버 데이터 조회는 커스텀 훅(`src/hooks/useXxx.ts`)으로 감싼다
- 화면 컴포넌트는 API를 직접 부르지 않고 훅만 쓴다
- 토큰·401 처리는 `src/api/`(tokenStore, clients, unauthorized), 로그인 화면은 `src/auth/`. 생성 클라이언트 인스턴스는 `src/api/clients.ts`에서만 만든다
