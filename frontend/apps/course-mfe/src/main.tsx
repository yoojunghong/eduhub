import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { CssBaseline } from '@mui/material';
import { App } from './App';
import { Layout } from './Layout';
import { routerFuture } from './routerFuture';
import { AuthProvider } from './auth/AuthContext';
import { registerUnauthorizedInterceptor } from './api/unauthorized';

// 401 처리(토큰 삭제 + 이벤트)는 앱 시작 때 한 번만 등록한다
registerUnauthorizedInterceptor();

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <CssBaseline />
    <BrowserRouter future={routerFuture}>
      <AuthProvider>
        <Layout>
          <App />
        </Layout>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);
