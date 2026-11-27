import type { ReactNode } from 'react';
import { Link as RouterLink, Navigate, useLocation } from 'react-router-dom';
import { Alert, Button, CircularProgress, Stack } from '@mui/material';
import { isAdmin, useAuth } from './AuthContext';

/** 로그인해야 보이는 화면을 감싼다. admin이면 관리자만 본다(아니면 안내만, 로그아웃하지 않는다). */
export function RequireAuth({ admin = false, children }: { admin?: boolean; children: ReactNode }) {
  const { user, initializing } = useAuth();
  const location = useLocation();

  if (initializing) return <CircularProgress aria-label="확인 중" />;
  if (!user) return <Navigate to="/login" state={{ from: location }} replace />;
  if (admin && !isAdmin(user)) {
    return (
      <Stack spacing={2}>
        <Alert severity="error">권한이 없습니다. 관리자만 볼 수 있는 화면입니다.</Alert>
        <Button component={RouterLink} to="/" sx={{ alignSelf: 'flex-start' }}>
          ← 목록으로
        </Button>
      </Stack>
    );
  }
  return <>{children}</>;
}
