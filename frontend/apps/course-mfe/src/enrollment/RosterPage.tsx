import { useState } from 'react';
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
  const [selected, setSelected] = useState<number[]>([]);
  const [result, setResult] = useState<{ severity: 'success' | 'warning' | 'error'; lines: string[] }>();

  if (loading && !data) return <CircularProgress aria-label="불러오는 중" />;
  if (error || !data) {
    return <Alert severity="error">{apiServerMessage(error, '명단을 불러오지 못했습니다.')}</Alert>;
  }

  const confirmed = data.confirmed ?? [];
  const waiting = data.waiting ?? [];
  const open = data.completionOpen === true;
  const selectableIds = confirmed
    .filter((r) => r.status === 'CONFIRMED' && r.employeeId != null)
    .map((r) => r.employeeId as number);
  const allSelected = selectableIds.length > 0 && selectableIds.every((id) => selected.includes(id));
  const canProcess = open && selected.length > 0 && !processing;

  const toggle = (id: number) =>
    setSelected((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));
  const toggleAll = () => setSelected(allSelected ? [] : selectableIds);

  const nameOf = (employeeId?: number) => {
    const found = [...confirmed, ...waiting].find((r) => r.employeeId === employeeId);
    return found?.name ?? `사원 ${employeeId}`;
  };

  const process = async (outcome: CompletionRequestOutcomeEnum) => {
    setResult(undefined);
    try {
      const res = await complete(selected, outcome);
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
      {result && (
        <Alert severity={result.severity} onClose={() => setResult(undefined)}>
          {result.lines.map((line) => (
            <div key={line}>{line}</div>
          ))}
        </Alert>
      )}
      {!open && <Alert severity="info">수료 처리는 회차 시작일부터 할 수 있습니다.</Alert>}
      <Stack direction="row" spacing={1}>
        <Button variant="contained" disabled={!canProcess} onClick={() => process('COMPLETED')}>
          선택한 {selected.length}명 수료 처리
        </Button>
        <Button variant="outlined" color="error" disabled={!canProcess} onClick={() => process('INCOMPLETE')}>
          선택한 {selected.length}명 미수료 처리
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
                  indeterminate={selected.length > 0 && !allSelected}
                  disabled={selectableIds.length === 0}
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
                    inputProps={{ 'aria-label': `${r.name ?? UNKNOWN} 선택` }}
                    checked={r.employeeId != null && selected.includes(r.employeeId)}
                    disabled={r.status !== 'CONFIRMED' || r.employeeId == null}
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
