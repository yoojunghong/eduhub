import { Fragment, useState, type ReactNode } from 'react';
import { Link as RouterLink, useLocation, useNavigate } from 'react-router-dom';
import { Alert, AppBar, Box, Button, Container, Link, Stack, Tab, Tabs, Toolbar, Typography } from '@mui/material';
import { isAdmin, useAuth } from './auth/AuthContext';

/** 주소로 지금 탭을 고른다. /admin·/my 로 시작하면 해당 탭, 나머지(목록·상세)는 교육과정. */
function currentTab(pathname: string) {
  if (pathname === '/admin' || pathname.startsWith('/admin/')) return '/admin';
  if (pathname === '/my' || pathname.startsWith('/my/')) return '/my/enrollments';
  return '/';
}

/** 모든 화면의 공통 틀: 상단 탭(교육과정 / 내 신청 내역 / 과정 관리), 사용자 영역, 본문 영역. */
export function Layout({ children }: { children: ReactNode }) {
  const location = useLocation();
  const { pathname } = location;
  const { user, logout, epoch, expired } = useAuth();
  const navigate = useNavigate();
  const [dismissedEpoch, setDismissedEpoch] = useState<number>();
  const showExpired = expired && !user && pathname !== '/login' && dismissedEpoch !== epoch;
  const selected = currentTab(pathname);
  const showMy = user !== null;
  const showAdmin = isAdmin(user);
  const visible = selected === '/' || (selected === '/my/enrollments' && showMy) || (selected === '/admin' && showAdmin);

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'grey.50' }}>
      <AppBar position="static" color="default" elevation={0} sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Toolbar sx={{ gap: 4 }}>
          <Typography variant="h6" component={RouterLink} to="/" color="inherit" sx={{ textDecoration: 'none' }}>
            EduHub
          </Typography>
          {/* 보이지 않는 탭이 선택값이면 MUI 경고가 나므로 value를 비운다 (비로그인 /admin 등) */}
          <Tabs
            value={visible ? selected : false}
            aria-label="메뉴"
            sx={{ alignSelf: 'stretch', flexGrow: 1, '& .MuiTab-root': { minHeight: 64 } }}
          >
            <Tab label="교육과정" value="/" component={RouterLink} to="/" />
            {showMy && <Tab label="내 신청 내역" value="/my/enrollments" component={RouterLink} to="/my/enrollments" />}
            {showAdmin && <Tab label="과정 관리" value="/admin" component={RouterLink} to="/admin" />}
          </Tabs>
          {user ? (
            <Stack direction="row" spacing={2} alignItems="center">
              <Typography variant="body2">
                {user.name} ({user.employeeNo}) · {showAdmin ? '관리자' : '사원'}
              </Typography>
              <Button
                size="small"
                onClick={() => {
                  logout();
                  navigate('/'); // 다음 사람이 이전 사람의 보호 화면으로 돌아가지 않게 한다
                }}
              >
                로그아웃
              </Button>
            </Stack>
          ) : (
            <Button component={RouterLink} to="/login" size="small" variant="outlined">
              로그인
            </Button>
          )}
        </Toolbar>
      </AppBar>
      <Container maxWidth="md" component="main" sx={{ py: 4 }}>
        {showExpired && (
          <Alert
            severity="warning"
            sx={{ mb: 2 }}
            onClose={() => setDismissedEpoch(epoch)}
          >
            로그인이 만료되었습니다. 다시 로그인해 주세요.{' '}
            <Link component={RouterLink} to="/login" state={{ from: location }}>
              로그인
            </Link>
          </Alert>
        )}
        {/* epoch가 바뀌면(공개 화면에서 토큰 만료) 화면을 다시 마운트해 토큰 없이 다시 불러온다 */}
        <Fragment key={epoch}>{children}</Fragment>
      </Container>
    </Box>
  );
}
