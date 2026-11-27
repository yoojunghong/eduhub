import { useRef, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import {
  Alert,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from "@mui/material";
import type {
  MyEnrollmentResponse,
  MyEnrollmentsStatusEnum,
} from "@eduhub/api-client";
import { apiServerMessage } from "../api/apiError";
import { useMyEnrollments } from "./hooks/useMyEnrollments";
import { useCancelEnrollment } from "./hooks/useCancelEnrollment";

const FILTERS: { value: MyEnrollmentsStatusEnum | "ALL"; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: "CONFIRMED", label: "확정" },
  { value: "WAITING", label: "대기" },
  { value: "CANCELED", label: "취소" },
  { value: "COMPLETED", label: "수료" },
  { value: "INCOMPLETE", label: "미수료" },
];

/** 서버가 준 시각(예: 2026-11-27T10:00:00+09:00)을 yyyy-MM-dd HH:mm 으로 보여 준다. */
const formatDateTime = (value?: string) =>
  value ? value.slice(0, 16).replace("T", " ") : "";

function StatusChip({ e }: { e: MyEnrollmentResponse }) {
  switch (e.status) {
    case "CONFIRMED":
      return <Chip size="small" color="success" label="확정" />;
    case "WAITING":
      return (
        <Chip
          size="small"
          color="warning"
          label={e.waitingNumber != null ? `대기 ${e.waitingNumber}번` : "대기"}
        />
      );
    case "CANCELED":
      return <Chip size="small" label="취소" />;
    case "COMPLETED":
      return <Chip size="small" color="primary" label="수료" />;
    case "INCOMPLETE":
      return (
        <Chip size="small" color="error" variant="outlined" label="미수료" />
      );
    default:
      return <Chip size="small" label={e.status ?? ""} />;
  }
}

/** 내 신청 내역(FR-07)과 취소(FR-06). */
export function MyEnrollmentsPage() {
  const [filter, setFilter] = useState<MyEnrollmentsStatusEnum | "ALL">("ALL");
  const { data, loading, error, reload } = useMyEnrollments(
    filter === "ALL" ? undefined : filter,
  );
  const { cancel, cancelingId } = useCancelEnrollment();
  const [target, setTarget] = useState<MyEnrollmentResponse>();
  const [result, setResult] = useState<{
    severity: "success" | "error";
    message: string;
  }>();

  const canceling = useRef(false); // 확인 버튼 더블 클릭으로 두 번 보내지 않는다

  const confirmCancel = async () => {
    if (!target?.enrollmentId || canceling.current) return;
    canceling.current = true;
    const id = target.enrollmentId;
    setTarget(undefined);
    setResult(undefined);
    try {
      await cancel(id);
      setResult({ severity: "success", message: "신청을 취소했습니다." });
    } catch (e) {
      setResult({
        severity: "error",
        message: apiServerMessage(e, "신청을 취소하지 못했습니다."),
      });
    }
    canceling.current = false;
    reload(); // 성공(승급은 서버가 한다)·실패 모두 최신 상태로 다시 조회
  };

  const rows = data ?? [];

  return (
    <Stack spacing={2}>
      <Typography variant="h4" component="h1">
        내 신청 내역
      </Typography>
      <ToggleButtonGroup
        exclusive
        size="small"
        value={filter}
        aria-label="상태 필터"
        onChange={(_, value) => value && setFilter(value)}
      >
        {FILTERS.map((f) => (
          <ToggleButton key={f.value} value={f.value}>
            {f.label}
          </ToggleButton>
        ))}
      </ToggleButtonGroup>
      {result && (
        <Alert severity={result.severity} onClose={() => setResult(undefined)}>
          {result.message}
        </Alert>
      )}
      {loading && !data ? (
        <CircularProgress aria-label="불러오는 중" />
      ) : error ? (
        <Alert severity="error">신청 내역을 불러오지 못했습니다.</Alert>
      ) : rows.length === 0 ? (
        <Typography color="text.secondary">신청 내역이 없습니다.</Typography>
      ) : (
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>과정</TableCell>
              <TableCell>시작일</TableCell>
              <TableCell>장소</TableCell>
              <TableCell>상태</TableCell>
              <TableCell>신청일시</TableCell>
              <TableCell align="right">취소</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((e) => (
              <TableRow key={e.enrollmentId}>
                <TableCell>
                  <RouterLink to={`/courses/${e.courseId}`}>
                    {e.courseTitle}
                  </RouterLink>
                </TableCell>
                <TableCell>{e.startDate}</TableCell>
                <TableCell>{e.place}</TableCell>
                <TableCell>
                  <StatusChip e={e} />
                </TableCell>
                <TableCell>{formatDateTime(e.enrolledAt)}</TableCell>
                <TableCell align="right">
                  {e.cancelable && (
                    <Button
                      size="small"
                      color="error"
                      disabled={cancelingId !== undefined || loading}
                      aria-label={`${e.courseTitle} 신청 취소`}
                      onClick={() => setTarget(e)}
                    >
                      취소
                    </Button>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
      <Dialog
        open={target !== undefined}
        onClose={() => setTarget(undefined)}
        aria-labelledby="cancel-dialog-title"
      >
        <DialogTitle id="cancel-dialog-title">신청 취소</DialogTitle>
        <DialogContent>
          <DialogContentText>
            '{target?.courseTitle}' 신청을 취소할까요?
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setTarget(undefined)}>닫기</Button>
          <Button color="error" onClick={confirmCancel}>
            신청 취소
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
