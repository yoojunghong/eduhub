import { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
  Alert,
  CircularProgress,
  Link,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
} from '@mui/material';
import { useCourses } from '../hooks/useCourses';

const PAGE_SIZE = 10;

// 분류 조회 API가 아직 없어서 화면에 고정한다
const CATEGORIES = ['BACKEND', 'FRONTEND', 'DEVOPS', 'AI', 'COMMON'];

export function CourseListPage() {
  const [keyword, setKeyword] = useState('');
  const [category, setCategory] = useState('');
  const [page, setPage] = useState(0); // MUI TablePagination도 0부터 시작
  const { data, loading, error } = useCourses({ keyword, category, page, size: PAGE_SIZE });

  if (error) return <Alert severity="error">과정 목록을 불러오지 못했습니다.</Alert>;

  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={2}>
        <TextField
          label="과정명 검색"
          value={keyword}
          onChange={(e) => {
            setKeyword(e.target.value);
            setPage(0); // 검색어가 바뀌면 첫 페이지부터
          }}
          fullWidth
        />
        <TextField
          select
          label="분류"
          value={category}
          onChange={(e) => {
            setCategory(e.target.value);
            setPage(0);
          }}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="">전체</MenuItem>
          {CATEGORIES.map((c) => (
            <MenuItem key={c} value={c}>
              {c}
            </MenuItem>
          ))}
        </TextField>
      </Stack>
      {loading ? (
        <CircularProgress />
      ) : (
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>과정명</TableCell>
              <TableCell>분류</TableCell>
              <TableCell align="right">회차 수</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {data?.content?.map((c) => (
              <TableRow key={c.id}>
                <TableCell>
                  <Link component={RouterLink} to={`/courses/${c.id}`}>
                    {c.title}
                  </Link>
                </TableCell>
                <TableCell>{c.category}</TableCell>
                <TableCell align="right">{c.sessionCount}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
      <TablePagination
        component="div"
        count={data?.totalElements ?? 0}
        page={page}
        rowsPerPage={PAGE_SIZE}
        rowsPerPageOptions={[PAGE_SIZE]}
        onPageChange={(_, p) => setPage(p)}
      />
    </Stack>
  );
}
