import { Link as RouterLink } from 'react-router-dom';
import {
  Alert,
  Button,
  Chip,
  CircularProgress,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import { useState } from 'react';
import { useCourse } from '../hooks/useCourse';
import { EnrollButton, SessionStatusChip, type EnrollNotice } from '../enrollment/EnrollButton';

function BackToList() {
  return (
    <Button component={RouterLink} to="/" sx={{ alignSelf: 'flex-start' }}>
      ← 목록으로
    </Button>
  );
}

/** 과정을 찾을 수 없을 때의 안내. 잘못된 주소(/courses/abc)에서도 쓴다. */
export function CourseNotFound() {
  return (
    <Stack spacing={2}>
      <Alert severity="warning">과정을 찾을 수 없습니다. 종료되었거나 없는 과정입니다.</Alert>
      <BackToList />
    </Stack>
  );
}

/** 과정 상세와 회차 목록(FR-02). */
export function CourseDetailPage({ id }: { id: number }) {
  const { data, loading, error, notFound, reload } = useCourse(id);
  const [enrolling, setEnrolling] = useState(false);
  const [result, setResult] = useState<EnrollNotice>();

  if (loading && !data) return <CircularProgress />;
  if (notFound) return <CourseNotFound />;
  if (error || !data)
    return (
      <Stack spacing={2}>
        <Alert severity="error">과정 정보를 불러오지 못했습니다.</Alert>
        <BackToList />
      </Stack>
    );

  const sessions = data.sessions ?? [];

  return (
    <Stack spacing={2}>
      <BackToList />
      <Stack direction="row" spacing={1} alignItems="center">
        <Typography variant="h6" component="h2">
          {data.title}
        </Typography>
        <Chip size="small" label={data.category} />
        {data.status === 'CLOSED' && <Chip size="small" color="default" label="종료" />}
      </Stack>
      {data.description && <Typography color="text.secondary">{data.description}</Typography>}

      <Typography variant="subtitle1" component="h3">
        회차
      </Typography>
      {result && (
        <Alert severity={result.severity} onClose={() => setResult(undefined)}>
          {result.message}
        </Alert>
      )}
      {sessions.length === 0 ? (
        <Typography color="text.secondary">등록된 회차가 없습니다.</Typography>
      ) : (
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>일정</TableCell>
              <TableCell>장소</TableCell>
              <TableCell align="right">정원</TableCell>
              <TableCell align="right">남은 자리</TableCell>
              <TableCell>상태</TableCell>
              <TableCell align="right">신청</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {sessions.map((s) => {
              return (
                <TableRow key={s.id}>
                  <TableCell>{s.startDate}</TableCell>
                  <TableCell>{s.place}</TableCell>
                  <TableCell align="right">{s.capacity}</TableCell>
                  <TableCell align="right">{s.remainingSeats}</TableCell>
                  <TableCell>
                    <SessionStatusChip session={s} />
                  </TableCell>
                  <TableCell align="right">
                    <EnrollButton
                      session={s}
                      courseClosed={data.status === 'CLOSED'}
                      busy={enrolling}
                      onBusyChange={setEnrolling}
                      onResult={setResult}
                      onEnrolled={reload}
                    />
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      )}
    </Stack>
  );
}
