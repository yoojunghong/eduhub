import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  // 목록·상세(index.html)와 관리 화면(admin.html, FR-03)을 각각 빌드한다
  build: {
    rollupOptions: {
      input: { main: 'index.html', admin: 'admin.html' },
    },
  },
  server: {
    port: 3001,
    strictPort: true,
    // CORS를 피하려고 /api 요청은 개발 서버가 백엔드(8080)로 넘긴다. 백엔드 코드는 바꾸지 않는다
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});
