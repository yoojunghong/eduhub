import { Link as RouterLink, useLocation } from 'react-router-dom';
import { Button } from '@mui/material';
import { useAuth } from '../auth/AuthContext';

export type EnrollMode = 'ENROLL' | 'WAITLIST' | 'FULL';

interface SessionSeats {
  remainingSeats?: number;
  waitingCount?: number;
  waitlistLimit?: number;
}

/** 회차의 신청 방식. 버튼과 상태 칩이 함께 쓴다. 빈 자리가 있어도 대기자가 있으면 대기열이 먼저다 (설계 2-3). */
export function enrollMode(s: SessionSeats): EnrollMode {
  const waitingCount = s.waitingCount ?? 0;
  if (waitingCount === 0 && (s.remainingSeats ?? 0) > 0) return 'ENROLL';
  return waitingCount < (s.waitlistLimit ?? 0) ? 'WAITLIST' : 'FULL';
}

interface Props {
  session: SessionSeats & { id?: number; status?: string };
  /** 과정이 종료됨 */
  courseClosed: boolean;
  /** 신청을 처리하는 중이라 모든 신청 버튼을 막는다 */
  busy: boolean;
  onEnroll: (sessionId: number) => void;
}

/** 회차의 신청·대기 신청·마감 버튼. 비로그인이면 로그인 링크를 보여 준다. */
export function EnrollButton({ session, courseClosed, busy, onEnroll }: Props) {
  const { user } = useAuth();
  const location = useLocation();

  if (!user) {
    return (
      <Button size="small" component={RouterLink} to="/login" state={{ from: location }}>
        로그인 후 신청
      </Button>
    );
  }

  const mode = enrollMode(session);
  const direct = mode === 'ENROLL';
  const waitlistFull = mode === 'FULL';
  return (
    <Button
      size="small"
      variant={direct ? 'contained' : 'outlined'}
      disabled={session.status === 'CLOSED' || waitlistFull || courseClosed || busy}
      onClick={() => onEnroll(session.id!)}
    >
      {direct ? '신청' : waitlistFull ? '마감' : '대기 신청'}
    </Button>
  );
}
