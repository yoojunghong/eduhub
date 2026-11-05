# frontend/CLAUDE.md

- UI는 MUI 컴포넌트를 쓴다
- API 호출은 OpenAPI 스펙에서 생성한 Axios 클라이언트만 쓴다 (`axios.create` 직접 사용 금지)
- 서버 데이터 조회는 커스텀 훅(`src/hooks/useXxx.ts`)으로 감싼다
- 화면 컴포넌트는 API를 직접 부르지 않고 훅만 쓴다
