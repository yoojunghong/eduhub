import type { ReactNode } from 'react';
import { Link as RouterLink, useLocation } from 'react-router-dom';
import { AppBar, Box, Container, Tab, Tabs, Toolbar, Typography } from '@mui/material';

/** 주소로 지금 탭을 고른다. /admin 으로 시작하면 관리, 나머지(목록·상세)는 교육과정. */
function currentTab(pathname: string) {
  return pathname === '/admin' || pathname.startsWith('/admin/') ? '/admin' : '/';
}

/** 모든 화면의 공통 틀: 상단 탭(교육과정 / 과정 관리)과 본문 영역. */
export function Layout({ children }: { children: ReactNode }) {
  const { pathname } = useLocation();

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'grey.50' }}>
      <AppBar position="static" color="default" elevation={0} sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Toolbar sx={{ gap: 4 }}>
          <Typography variant="h6" component={RouterLink} to="/" color="inherit" sx={{ textDecoration: 'none' }}>
            EduHub
          </Typography>
          <Tabs value={currentTab(pathname)} aria-label="메뉴" sx={{ alignSelf: 'stretch', '& .MuiTab-root': { minHeight: 64 } }}>
            <Tab label="교육과정" value="/" component={RouterLink} to="/" />
            <Tab label="과정 관리" value="/admin" component={RouterLink} to="/admin" />
          </Tabs>
        </Toolbar>
      </AppBar>
      <Container maxWidth="md" component="main" sx={{ py: 4 }}>
        {children}
      </Container>
    </Box>
  );
}
