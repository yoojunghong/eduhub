import { Link as RouterLink, useLocation } from 'react-router-dom';
import { Button, Chip } from '@mui/material';
import { useAuth } from '../auth/AuthContext';
import { apiErrorMessage } from '../api/apiError';
import { EnrollInProgressError, useEnroll } from './hooks/useEnroll';

type EnrollMode = 'ENROLL' | 'WAITLIST' | 'FULL';

interface SessionSeats {
  id?: number;
  status?: string;
  remainingSeats?: number;
  waitingCount?: number;
  waitlistLimit?: number;
}

/** 회차의 신청 방식. 버튼과 상태 칩이 함께 쓴다. 빈 자리가 있어도 대기자가 있으면 대기열이 먼저다 (설계 2-3). */
function enrollMode(s: SessionSeats): EnrollMode {
  const waitingCount = s.waitingCount ?? 0;
  if (waitingCount === 0 && (s.remainingSeats ?? 0) > 0) return 'ENROLL';
  return waitingCount < (s.waitlistLimit ?? 0) ? 'WAITLIST' : 'FULL';
}

/** 회차의 신청 가능 상태 칩 (종료·마감·대기 가능·신청 가능). */
export function SessionStatusChip({ session }: { session: SessionSeats }) {
  const mode = enrollMode(session);
  if (session.status === 'CLOSED') return <Chip size="small" label="종료" />;
  if (mode === 'FULL') return <Chip size="small" color="warning" label="마감" />;
  if (mode === 'WAITLIST') return <Chip size="small" color="warning" variant="outlined" label="대기 가능" />;
  return <Chip size="small" color="success" label="신청 가능" />;
}

/** 신청 결과 안내. 화면(상위)이 Alert로 보여 준다. */
export interface EnrollNotice {
  severity: 'success' | 'info' | 'error';
  message: string;
}

interface Props {
  session: SessionSeats;
  /** 과정이 종료됨 */
  courseClosed: boolean;
  /** 어느 행에서든 신청이 처리 중이면 모든 신청 버튼을 막는다 (상위가 상태를 들고 있다) */
  busy: boolean;
  onBusyChange: (busy: boolean) => void;
  /** 신청 시작 시 undefined, 끝나면 결과 안내 */
  onResult: (notice: EnrollNotice | undefined) => void;
  /** 성공·실패 뒤 상세를 다시 조회한다 */
  onEnrolled: () => void;
}

/** 회차의 신청·대기 신청·마감 버튼. 신청 호출과 결과 문구까지 맡는다. 비로그인이면 로그인 링크. */
export function EnrollButton({ session, courseClosed, busy, onBusyChange, onResult, onEnrolled }: Props) {
  const { user } = useAuth();
  const location = useLocation();
  const { enroll } = useEnroll();

  if (!user) {
    return (
      <Button size="small" component={RouterLink} to="/login" state={{ from: location }}>
        로그인 후 신청
      </Button>
    );
  }

  const apply = async () => {
    onResult(undefined);
    onBusyChange(true);
    let ignored = false;
    try {
      const enrollment = await enroll(session.id!);
      onResult(
        enrollment.status === 'WAITING'
          ? {
              severity: 'info',
              message:
                enrollment.waitingNumber != null
                  ? `대기 ${enrollment.waitingNumber}번으로 등록되었습니다.`
                  : '대기자로 등록되었습니다.',
            }
          : { severity: 'success', message: '수강 신청이 확정되었습니다.' },
      );
      onEnrolled(); // 남은 자리를 새로 보여 준다
    } catch (e) {
      // 진행 중 두 번째 클릭: 첫 요청이 처리 중이므로 알림·새로 고침 없이 무시한다
      if (e instanceof EnrollInProgressError) {
        ignored = true; // 첫 요청이 아직 처리 중이므로 busy를 풀지 않는다
        return;
      }
      onResult({ severity: 'error', message: apiErrorMessage(e, '신청하지 못했습니다.') });
      onEnrolled(); // 실패(409·503 등) 뒤에도 남은 자리·종료 상태를 새로 보여 준다
    } finally {
      if (!ignored) onBusyChange(false);
    }
  };

  const mode = enrollMode(session);
  const direct = mode === 'ENROLL';
  const waitlistFull = mode === 'FULL';
  return (
    <Button
      size="small"
      variant={direct ? 'contained' : 'outlined'}
      disabled={session.status === 'CLOSED' || waitlistFull || courseClosed || busy}
      onClick={apply}
    >
      {direct ? '신청' : waitlistFull ? '마감' : '대기 신청'}
    </Button>
  );
}
