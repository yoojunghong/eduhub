import { useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import {
  Alert,
  Button,
  Chip,
  CircularProgress,
  Link,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  Typography,
} from '@mui/material';
import { useCourses } from '../../hooks/useCourses';
import { useCreateCourse } from '../../hooks/useCreateCourse';
import { CourseFormDialog } from './CourseFormDialog';

/** 과정 상태 표시. 운영 중은 초록, 종료는 회색. 관리 화면 목록과 상세에서 함께 쓴다. */
export function CourseStatusChip({ status }: { status?: string }) {
  const closed = status === 'CLOSED';
  return <Chip size="small" label={closed ? '종료' : '운영 중'} color={closed ? 'default' : 'success'} />;
}

/**
 * 과정 관리 목록 (FR-03). 운영 중·종료 과정을 모두 표로 보여 주고, 제목을 누르면 관리 상세로 간다.
 * 인증·관리자 권한 구분은 이후 회차에 붙인다.
 */
export function CourseAdminPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [creating, setCreating] = useState(false);

  const courses = useCourses({ status: 'ALL', page, size });
  const { create, saving } = useCreateCourse();

  return (
    <Stack spacing={3}>
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Typography variant="h4" component="h1">
          과정 관리
        </Typography>
        <Button variant="contained" size="large" onClick={() => setCreating(true)}>
          과정 등록
        </Button>
      </Stack>

      {courses.error ? (
        <Alert severity="error">과정 목록을 불러오지 못했습니다.</Alert>
      ) : (
        <Paper>
          <TableContainer>
            <Table aria-label="과정 목록">
              <TableHead>
                <TableRow>
                  <TableCell>제목</TableCell>
                  <TableCell sx={{ width: 160 }}>분류</TableCell>
                  <TableCell sx={{ width: 160 }}>상태</TableCell>
                  <TableCell align="right" sx={{ width: 120 }}>
                    회차 수
                  </TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {courses.loading ? (
                  <TableRow>
                    <TableCell colSpan={4} align="center">
                      <CircularProgress size={24} />
                    </TableCell>
                  </TableRow>
                ) : courses.data?.content?.length ? (
                  courses.data.content.map((c) => (
                    <TableRow key={c.id} hover>
                      <TableCell>
                        <Link component={RouterLink} to={`/admin/courses/${c.id}`} underline="hover">
                          {c.title}
                        </Link>
                      </TableCell>
                      <TableCell>{c.category}</TableCell>
                      <TableCell>
                        <CourseStatusChip status={c.status} />
                      </TableCell>
                      <TableCell align="right">{c.sessionCount}</TableCell>
                    </TableRow>
                  ))
                ) : (
                  <TableRow>
                    <TableCell colSpan={4}>등록된 과정이 없습니다.</TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination
            component="div"
            count={courses.data?.totalElements ?? 0}
            page={page}
            rowsPerPage={size}
            rowsPerPageOptions={[10, 20, 50]}
            onPageChange={(_, p) => setPage(p)}
            onRowsPerPageChange={(e) => {
              setSize(Number(e.target.value));
              setPage(0);
            }}
            labelRowsPerPage="페이지당"
            labelDisplayedRows={({ from, to, count }) => `${from}-${to} / 전체 ${count}`}
          />
        </Paper>
      )}

      <CourseFormDialog
        open={creating}
        title="과정 등록"
        saving={saving}
        onSubmit={async (value) => {
          const created = await create(value);
          navigate(`/admin/courses/${created.id}`); // 등록하면 바로 회차를 추가할 수 있게 상세로 간다
        }}
        onClose={() => setCreating(false)}
      />
    </Stack>
  );
}
