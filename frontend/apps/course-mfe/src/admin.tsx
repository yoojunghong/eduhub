import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Container, CssBaseline, Typography } from '@mui/material';
import { CourseAdminPage } from './pages/admin/CourseAdminPage';

// 관리 화면(FR-03) 진입점: http://localhost:3001/admin.html
// 목록·상세 화면(main.tsx)과 따로 둔다. 라우터를 붙인 뒤에는 /admin 경로로 옮긴다
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <CssBaseline />
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Typography variant="h5" component="h1" gutterBottom>
        과정 관리
      </Typography>
      <CourseAdminPage />
    </Container>
  </StrictMode>,
);
