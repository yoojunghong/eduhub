import { useRef, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
  Alert,
  Button,
  Checkbox,
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
import type { CompletionRequestOutcomeEnum, RosterEntry } from '@eduhub/api-client';
import { apiServerMessage } from '../api/apiError';
import { useRoster } from './hooks/useRoster';
import { useCompletion } from './hooks/useCompletion';

const FAILURE_REASONS: Record<string, string> = {
  NOT_ENROLLED: '이 회차 신청 내역 없음',
  NOT_CONFIRMED: '확정 상태가 아님',
  ALREADY_PROCESSED: '이미 처리됨',
};

const UNKNOWN = '알 수 없음';
const MAX_BATCH = 100; // 서버 제한 1~100

function StatusChip({ status }: { status?: string }) {
  switch (status) {
    case 'CONFIRMED':
      return <Chip size="small" color="success" label="확정" />;
    case 'COMPLETED':
      return <Chip size="small" color="primary" label="수료" />;
    case 'INCOMPLETE':
      return <Chip size="small" color="error" variant="outlined" label="미수료" />;
    default:
      return <Chip size="small" label={status ?? ''} />;
  }
}

/** 회차 명단(FR-08)과 수료·미수료 처리. */
export function RosterPage({ sessionId }: { sessionId: number }) {
  const { data, loading, error, reload } = useRoster(sessionId);
  const { complete, processing } = useCompletion(sessionId);
  const busyRef = useRef(false);
  const [selected, setSelected] = useState<number[]>([]);
  const [result, setResult] = useState<{ severity: 'success' | 'warning' | 'error'; lines: string[] }>();

  if (loading && !data) return <CircularProgress aria-label="불러오는 중" />;
  if (!data) {
    return <Alert severity="error">{apiServerMessage(error, '명단을 불러오지 못했습니다.')}</Alert>;
  }

  const confirmed = data.confirmed ?? [];
  const waiting = data.waiting ?? [];
  const open = data.completionOpen === true;
  const selectableIds = confirmed
    .filter((r) => r.status === 'CONFIRMED' && r.employeeId != null)
    .map((r) => r.employeeId as number);
  // 재조회로 선택 불가가 된 사원은 선택에서 자동으로 빠진다
  const chosen = selected.filter((id) => selectableIds.includes(id));
  const allSelected = selectableIds.length > 0 && selectableIds.every((id) => chosen.includes(id));
  const busy = loading || processing;
  const tooMany = chosen.length > MAX_BATCH;
  const canProcess = open && chosen.length > 0 && !tooMany && !busy;

  const toggle = (id: number) =>
    setSelected((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));
  const toggleAll = () => setSelected(allSelected ? [] : selectableIds);

  const nameOf = (employeeId?: number) => {
    const found = [...confirmed, ...waiting].find((r) => r.employeeId === employeeId);
    return found?.name ?? `사원 ${employeeId}`;
  };

  const process = async (outcome: CompletionRequestOutcomeEnum) => {
    if (busyRef.current) return; // 이중 클릭 방지
    busyRef.current = true;
    setResult(undefined);
    try {
      const res = await complete(chosen, outcome);
      const failed = res.failed ?? [];
      setResult({
        severity: failed.length > 0 ? 'warning' : 'success',
        lines: [
          `${res.processed?.length ?? 0}명 처리했습니다.`,
          ...failed.map((f) => `${nameOf(f.employeeId)}: ${FAILURE_REASONS[f.reason ?? ''] ?? f.reason ?? ''}`),
        ],
      });
    } catch (e) {
      setResult({ severity: 'error', lines: [apiServerMessage(e, '처리하지 못했습니다.')] });
    }
    busyRef.current = false;
    setSelected([]);
    reload();
  };

  const info = (r: RosterEntry) => (
    <>
      <TableCell>{r.name ?? UNKNOWN}</TableCell>
      <TableCell>{r.employeeNo ?? UNKNOWN}</TableCell>
      <TableCell>{r.department ?? UNKNOWN}</TableCell>
    </>
  );

  return (
    <Stack spacing={2}>
      <Button component={RouterLink} to={`/admin/courses/${data.courseId}`} sx={{ alignSelf: 'flex-start' }}>
        ← 과정 관리로
      </Button>
      <Typography variant="h4" component="h1">
        {data.courseTitle} 명단
      </Typography>
      <Typography color="text.secondary">
        시작일 {data.startDate} · 장소 {data.place} · 정원 {data.capacity}명
      </Typography>

      <Typography variant="h6" component="h2">
        확정자 ({confirmed.length}명)
      </Typography>
      {error != null && <Alert severity="error">{apiServerMessage(error, '명단을 다시 불러오지 못했습니다.')}</Alert>}
      {tooMany && <Alert severity="warning">한 번에 100명까지 처리할 수 있습니다.</Alert>}
      {result && (
        <Alert severity={result.severity} onClose={() => setResult(undefined)}>
          {result.lines.map((line, i) => (
            <div key={`${i}-${line}`}>{line}</div>
          ))}
        </Alert>
      )}
      {!open && <Alert severity="info">수료 처리는 회차 시작일부터 할 수 있습니다.</Alert>}
      <Stack direction="row" spacing={1}>
        <Button variant="contained" disabled={!canProcess} onClick={() => process('COMPLETED')}>
          선택한 {chosen.length}명 수료 처리
        </Button>
        <Button variant="outlined" color="error" disabled={!canProcess} onClick={() => process('INCOMPLETE')}>
          선택한 {chosen.length}명 미수료 처리
        </Button>
      </Stack>
      {confirmed.length === 0 ? (
        <Typography color="text.secondary">확정자가 없습니다.</Typography>
      ) : (
        <Table aria-label="확정자 명단">
          <TableHead>
            <TableRow>
              <TableCell padding="checkbox">
                <Checkbox
                  inputProps={{ 'aria-label': '전체 선택' }}
                  checked={allSelected}
                  indeterminate={chosen.length > 0 && !allSelected}
                  disabled={busy || selectableIds.length === 0}
                  onChange={toggleAll}
                />
              </TableCell>
              <TableCell>이름</TableCell>
              <TableCell>사번</TableCell>
              <TableCell>부서</TableCell>
              <TableCell>상태</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {confirmed.map((r) => (
              <TableRow key={r.enrollmentId}>
                <TableCell padding="checkbox">
                  <Checkbox
                    inputProps={{ 'aria-label': `${r.name ?? r.employeeNo ?? `사원 ${r.employeeId}`} 선택` }}
                    checked={r.employeeId != null && chosen.includes(r.employeeId)}
                    disabled={busy || r.status !== 'CONFIRMED' || r.employeeId == null}
                    onChange={() => r.employeeId != null && toggle(r.employeeId)}
                  />
                </TableCell>
                {info(r)}
                <TableCell>
                  <StatusChip status={r.status} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <Typography variant="h6" component="h2">
        대기자 ({waiting.length}명)
      </Typography>
      {waiting.length === 0 ? (
        <Typography color="text.secondary">대기자가 없습니다.</Typography>
      ) : (
        <Table aria-label="대기자 명단">
          <TableHead>
            <TableRow>
              <TableCell>순번</TableCell>
              <TableCell>이름</TableCell>
              <TableCell>사번</TableCell>
              <TableCell>부서</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {waiting.map((r) => (
              <TableRow key={r.enrollmentId}>
                <TableCell>{r.waitingNumber}</TableCell>
                {info(r)}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </Stack>
  );
}
