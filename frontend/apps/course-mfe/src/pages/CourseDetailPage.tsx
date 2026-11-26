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
import { EnrollInProgressError, useEnroll } from '../hooks/useEnroll';
import { apiErrorMessage } from '../api/apiError';

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
  const { enroll, enrollingSessionId } = useEnroll();
  const [result, setResult] = useState<{ severity: 'success' | 'info' | 'error'; message: string }>();

  const apply = async (sessionId: number) => {
    setResult(undefined);
    try {
      const enrollment = await enroll(sessionId);
      setResult(
        enrollment.status === 'WAITING'
          ? {
              severity: 'info',
              message:
                enrollment.waitingNumber != null
                  ? `정원이 차서 대기 ${enrollment.waitingNumber}번으로 등록되었습니다.`
                  : '정원이 차서 대기자로 등록되었습니다.',
            }
          : { severity: 'success', message: '수강 신청이 확정되었습니다.' },
      );
      reload(); // 남은 자리를 새로 보여 준다
    } catch (e) {
      // 진행 중 두 번째 클릭: 첫 요청이 처리 중이므로 알림·새로 고침 없이 무시한다
      if (e instanceof EnrollInProgressError) return;
      setResult({ severity: 'error', message: apiErrorMessage(e, '신청하지 못했습니다.') });
      reload(); // 실패(409·503 등) 뒤에도 남은 자리·종료 상태를 새로 보여 준다
    }
  };

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
              const full = (s.remainingSeats ?? 0) <= 0;
              const closed = s.status === 'CLOSED';
              return (
                <TableRow key={s.id}>
                  <TableCell>{s.startDate}</TableCell>
                  <TableCell>{s.place}</TableCell>
                  <TableCell align="right">{s.capacity}</TableCell>
                  <TableCell align="right">{s.remainingSeats}</TableCell>
                  <TableCell>
                    {closed ? (
                      <Chip size="small" label="종료" />
                    ) : full ? (
                      <Chip size="small" color="warning" label="마감" />
                    ) : (
                      <Chip size="small" color="success" label="신청 가능" />
                    )}
                  </TableCell>
                  <TableCell align="right">
                    <Button
                      size="small"
                      variant={full ? 'outlined' : 'contained'}
                      disabled={closed || data.status === 'CLOSED' || enrollingSessionId !== undefined}
                      onClick={() => apply(s.id!)}
                    >
                      {full ? '대기 신청' : '신청'}
                    </Button>
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
