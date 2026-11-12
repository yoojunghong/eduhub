import { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  List,
  ListItemButton,
  ListItemText,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TablePagination,
  TextField,
  Typography,
} from '@mui/material';
import { useCourses } from '../../hooks/useCourses';
import { useCourseAdmin } from '../../hooks/useCourseAdmin';
import { useCreateCourse } from '../../hooks/useCreateCourse';
import { apiErrorMessage } from '../../api/apiError';
import { CourseFormDialog } from './CourseFormDialog';
import { SessionFormDialog } from './SessionFormDialog';

const PAGE_SIZE = 10;

type DialogKind = 'create' | 'edit' | 'session' | 'close';

/**
 * 과정·회차 관리 화면 (FR-03). 왼쪽에서 과정을 고르고 오른쪽에서 수정·회차 추가·종료한다.
 * 목록 API는 운영 중(OPEN)인 과정만 돌려주므로, 종료한 과정은 선택이 유지되는 동안에만 보인다.
 * 인증·관리자 권한 구분은 4주차 이후에 붙인다.
 */
export function CourseAdminPage() {
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [refreshKey, setRefreshKey] = useState(0);
  const [selectedId, setSelectedId] = useState<number>();
  const [dialog, setDialog] = useState<DialogKind>();
  const [closeError, setCloseError] = useState<string>();

  const courses = useCourses({ keyword, page, size: PAGE_SIZE, refreshKey });
  const course = useCourseAdmin(selectedId);
  const { create, saving: creating } = useCreateCourse();

  const detail = course.data;
  const closed = detail?.status === 'CLOSED';
  const refreshList = () => setRefreshKey((k) => k + 1);

  const confirmClose = async () => {
    setCloseError(undefined);
    try {
      await course.close();
      setDialog(undefined);
      refreshList(); // 종료한 과정은 목록에서 빠진다
    } catch (e) {
      setCloseError(apiErrorMessage(e));
    }
  };

  return (
    <Stack direction={{ xs: 'column', md: 'row' }} spacing={3} alignItems="flex-start">
      <Paper variant="outlined" sx={{ p: 2, width: { xs: '100%', md: 320 }, flexShrink: 0 }}>
        <Stack spacing={2}>
          <Button variant="contained" onClick={() => setDialog('create')}>
            새 과정 등록
          </Button>
          <TextField
            label="과정명 검색"
            size="small"
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value);
              setPage(0);
            }}
          />
          {courses.error ? (
            <Alert severity="error">과정 목록을 불러오지 못했습니다.</Alert>
          ) : courses.loading ? (
            <CircularProgress size={24} />
          ) : (
            <List dense disablePadding aria-label="운영 중인 과정">
              {courses.data?.content?.map((c) => (
                <ListItemButton
                  key={c.id}
                  selected={c.id === selectedId}
                  onClick={() => setSelectedId(c.id)}
                >
                  <ListItemText primary={c.title} secondary={`${c.category} · 회차 ${c.sessionCount}`} />
                </ListItemButton>
              ))}
            </List>
          )}
          <TablePagination
            component="div"
            count={courses.data?.totalElements ?? 0}
            page={page}
            rowsPerPage={PAGE_SIZE}
            rowsPerPageOptions={[PAGE_SIZE]}
            onPageChange={(_, p) => setPage(p)}
            labelDisplayedRows={({ from, to, count }) => `${from}-${to} / ${count}`}
          />
        </Stack>
      </Paper>

      <Box sx={{ flexGrow: 1, width: '100%' }}>
        {selectedId === undefined ? (
          <Typography color="text.secondary">왼쪽에서 과정을 고르거나 새 과정을 등록하세요.</Typography>
        ) : course.error ? (
          <Alert severity="error">{apiErrorMessage(course.error, '과정을 불러오지 못했습니다.')}</Alert>
        ) : course.loading || !detail ? (
          <CircularProgress />
        ) : (
          <Stack spacing={2}>
            <Stack direction="row" spacing={1} alignItems="center">
              <Typography variant="h6" component="h2">
                {detail.title}
              </Typography>
              <Chip
                size="small"
                label={closed ? '종료' : '운영 중'}
                color={closed ? 'default' : 'success'}
              />
            </Stack>
            <Typography variant="body2" color="text.secondary">
              {detail.category}
            </Typography>
            {detail.description && <Typography>{detail.description}</Typography>}
            {closed && <Alert severity="info">종료된 과정은 수정하거나 회차를 추가할 수 없습니다.</Alert>}
            <Stack direction="row" spacing={1}>
              <Button variant="outlined" disabled={closed} onClick={() => setDialog('edit')}>
                수정
              </Button>
              <Button variant="outlined" disabled={closed} onClick={() => setDialog('session')}>
                회차 추가
              </Button>
              <Button
                variant="outlined"
                color="error"
                disabled={closed}
                onClick={() => {
                  setCloseError(undefined);
                  setDialog('close');
                }}
              >
                과정 종료
              </Button>
            </Stack>
            <Table size="small" aria-label="회차 목록">
              <TableHead>
                <TableRow>
                  <TableCell>시작일</TableCell>
                  <TableCell>장소</TableCell>
                  <TableCell align="right">정원</TableCell>
                  <TableCell>상태</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {detail.sessions?.length ? (
                  detail.sessions.map((s) => (
                    <TableRow key={s.id}>
                      <TableCell>{s.startDate}</TableCell>
                      <TableCell>{s.place}</TableCell>
                      <TableCell align="right">{s.capacity}</TableCell>
                      <TableCell>{s.status === 'CLOSED' ? '종료' : '모집'}</TableCell>
                    </TableRow>
                  ))
                ) : (
                  <TableRow>
                    <TableCell colSpan={4}>등록된 회차가 없습니다.</TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </Stack>
        )}
      </Box>

      <CourseFormDialog
        open={dialog === 'create'}
        title="과정 등록"
        saving={creating}
        onSubmit={async (value) => {
          const created = await create(value);
          refreshList();
          setSelectedId(created.id);
        }}
        onClose={() => setDialog(undefined)}
      />
      <CourseFormDialog
        open={dialog === 'edit'}
        title="과정 수정"
        initial={
          detail
            ? { title: detail.title ?? '', category: detail.category ?? '', description: detail.description }
            : undefined
        }
        saving={course.saving}
        onSubmit={async (value) => {
          await course.update(value);
          refreshList();
        }}
        onClose={() => setDialog(undefined)}
      />
      <SessionFormDialog
        open={dialog === 'session'}
        saving={course.saving}
        onSubmit={async (value) => {
          await course.addSession(value);
          refreshList(); // 목록의 회차 수를 맞춘다
        }}
        onClose={() => setDialog(undefined)}
      />
      <Dialog open={dialog === 'close'} onClose={() => setDialog(undefined)}>
        <DialogTitle>과정 종료</DialogTitle>
        <DialogContent>
          {closeError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {closeError}
            </Alert>
          )}
          <DialogContentText>
            &lsquo;{detail?.title}&rsquo; 과정과 모든 회차를 종료합니다. 종료한 과정은 다시 열거나 수정할 수 없습니다.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialog(undefined)}>취소</Button>
          <Button color="error" variant="contained" disabled={course.saving} onClick={confirmClose}>
            종료
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
