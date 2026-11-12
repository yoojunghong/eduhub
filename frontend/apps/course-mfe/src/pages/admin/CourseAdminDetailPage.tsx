import { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
  Alert,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import { useCourseAdmin } from '../../hooks/useCourseAdmin';
import { apiErrorMessage } from '../../api/apiError';
import { CourseFormDialog } from './CourseFormDialog';
import { SessionFormDialog } from './SessionFormDialog';
import { CourseStatusChip } from './CourseAdminPage';

type DialogKind = 'edit' | 'session' | 'close';

/** 과정 하나의 관리 화면 (FR-03): 수정, 회차 추가, 종료. */
export function CourseAdminDetailPage({ id }: { id: number }) {
  const course = useCourseAdmin(id);
  const [dialog, setDialog] = useState<DialogKind>();
  const [closeError, setCloseError] = useState<string>();

  const detail = course.data;
  const closed = detail?.status === 'CLOSED';

  const confirmClose = async () => {
    setCloseError(undefined);
    try {
      await course.close();
      setDialog(undefined);
    } catch (e) {
      setCloseError(apiErrorMessage(e));
    }
  };

  const back = (
    <Button component={RouterLink} to="/admin" sx={{ alignSelf: 'flex-start' }}>
      ← 과정 관리 목록
    </Button>
  );

  if (course.error) {
    return (
      <Stack spacing={2}>
        <Alert severity="error">{apiErrorMessage(course.error, '과정을 불러오지 못했습니다.')}</Alert>
        {back}
      </Stack>
    );
  }
  if (course.loading || !detail) return <CircularProgress />;

  return (
    <Stack spacing={3}>
      {back}
      <Stack direction="row" spacing={1.5} alignItems="center">
        <Typography variant="h4" component="h1">
          {detail.title}
        </Typography>
        <CourseStatusChip status={detail.status} />
      </Stack>
      <Typography color="text.secondary">{detail.category}</Typography>
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
      <Paper>
        <Table aria-label="회차 목록">
          <TableHead>
            <TableRow>
              <TableCell>시작일</TableCell>
              <TableCell>장소</TableCell>
              <TableCell align="right">정원</TableCell>
              <TableCell align="right">남은 자리</TableCell>
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
                  <TableCell align="right">{s.remainingSeats}</TableCell>
                  <TableCell>{s.status === 'CLOSED' ? '종료' : '모집'}</TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell colSpan={5}>등록된 회차가 없습니다.</TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </Paper>

      <CourseFormDialog
        open={dialog === 'edit'}
        title="과정 수정"
        initial={{ title: detail.title ?? '', category: detail.category ?? '', description: detail.description }}
        saving={course.saving}
        onSubmit={course.update}
        onClose={() => setDialog(undefined)}
      />
      <SessionFormDialog
        open={dialog === 'session'}
        saving={course.saving}
        onSubmit={course.addSession}
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
            &lsquo;{detail.title}&rsquo; 과정과 모든 회차를 종료합니다. 종료한 과정은 다시 열거나 수정할 수 없습니다.
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
