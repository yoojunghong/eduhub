import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert,
  Button,
  Chip,
  CircularProgress,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
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

type StatusFilter = 'ALL' | 'OPEN' | 'CLOSED';

/**
 * 과정 관리 목록 (FR-03). 검색어·상태로 걸러 표로 보여 주고, 행을 누르면 관리 상세로 간다.
 * 검색 조건은 "검색" 버튼(또는 Enter)을 눌렀을 때 적용한다. 인증·관리자 권한 구분은 이후 회차에 붙인다.
 */
export function CourseAdminPage() {
  const navigate = useNavigate();
  const [keywordInput, setKeywordInput] = useState('');
  // '' = 고르지 않음(전체). 비워 두면 MUI가 "상태" 라벨을 칸 안에 보여 준다
  const [statusInput, setStatusInput] = useState<StatusFilter | ''>('');
  const [query, setQuery] = useState<{ keyword: string; status: StatusFilter }>({ keyword: '', status: 'ALL' });
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [creating, setCreating] = useState(false);

  const courses = useCourses({ keyword: query.keyword, status: query.status, page, size });
  const { create, saving } = useCreateCourse();

  const search = () => {
    setQuery({ keyword: keywordInput.trim(), status: statusInput || 'ALL' });
    setPage(0);
  };

  return (
    <Stack spacing={3}>
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Typography variant="h5" component="h1">
          과정 관리
        </Typography>
        <Button variant="contained" onClick={() => setCreating(true)}>
          과정 등록
        </Button>
      </Stack>

      <Stack
        component="form"
        direction={{ xs: 'column', sm: 'row' }}
        spacing={2}
        onSubmit={(e) => {
          e.preventDefault();
          search();
        }}
      >
        <TextField
          placeholder="검색어"
          inputProps={{ 'aria-label': '검색어' }}
          value={keywordInput}
          onChange={(e) => setKeywordInput(e.target.value)}
          sx={{ flexGrow: 1, bgcolor: 'background.paper' }}
        />
        <FormControl sx={{ minWidth: 150, bgcolor: 'background.paper' }}>
          <InputLabel id="admin-status-label">상태</InputLabel>
          <Select
            labelId="admin-status-label"
            label="상태"
            value={statusInput}
            onChange={(e) => setStatusInput(e.target.value as StatusFilter | '')}
          >
            <MenuItem value="">전체</MenuItem>
            <MenuItem value="OPEN">운영 중</MenuItem>
            <MenuItem value="CLOSED">종료</MenuItem>
          </Select>
        </FormControl>
        <Button type="submit" variant="outlined" sx={{ minWidth: 80 }}>
          검색
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
                  <TableCell sx={{ fontWeight: 600 }}>제목</TableCell>
                  <TableCell sx={{ fontWeight: 600, width: '25%' }}>분류</TableCell>
                  <TableCell sx={{ fontWeight: 600, width: '25%' }}>상태</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {courses.loading ? (
                  <TableRow>
                    <TableCell colSpan={3} align="center">
                      <CircularProgress size={24} />
                    </TableCell>
                  </TableRow>
                ) : courses.data?.content?.length ? (
                  courses.data.content.map((c) => (
                    <TableRow
                      key={c.id}
                      hover
                      tabIndex={0}
                      sx={{ cursor: 'pointer' }}
                      onClick={() => navigate(`/admin/courses/${c.id}`)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') navigate(`/admin/courses/${c.id}`);
                      }}
                    >
                      <TableCell>{c.title}</TableCell>
                      <TableCell>{c.category}</TableCell>
                      <TableCell>
                        <CourseStatusChip status={c.status} />
                      </TableCell>
                    </TableRow>
                  ))
                ) : (
                  <TableRow>
                    <TableCell colSpan={3}>조건에 맞는 과정이 없습니다.</TableCell>
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
            labelRowsPerPage="페이지당 행 수"
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
