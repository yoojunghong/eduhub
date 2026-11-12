import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    // 목록(/), 상세(/courses/:id), 관리(/admin)를 한 앱의 라우터로 보여 준다
    port: 3000,
    strictPort: true,
    // CORS를 피하려고 /api 요청은 개발 서버가 백엔드(8080)로 넘긴다. 백엔드 코드는 바꾸지 않는다
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});
