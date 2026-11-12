import { useCallback, useRef, useState } from 'react';
import type { EnrollmentResponse } from '@eduhub/api-client';
import { enrollmentApi } from '../api/courseApi';

const EMPLOYEE_ID_KEY = 'eduhub.employeeId';
const DEFAULT_EMPLOYEE_ID = 1001;

/**
 * 지금 사용자의 직원 번호. TODO(NFR-03): 로그인이 붙으면 서버가 인증 정보에서 꺼내므로 이 함수와 헤더를 없앤다.
 * 그 전까지는 브라우저 localStorage의 eduhub.employeeId(없으면 1001)를 쓴다.
 */
export function currentEmployeeId(): number {
  try {
    const stored = Number(window.localStorage.getItem(EMPLOYEE_ID_KEY));
    return Number.isInteger(stored) && stored > 0 ? stored : DEFAULT_EMPLOYEE_ID;
  } catch {
    return DEFAULT_EMPLOYEE_ID;
  }
}

/** 이미 신청을 처리하는 중이라 서버에 보내지 않고 거부했음을 나타낸다. 화면은 이 오류를 알리지 않고 무시한다. */
export class EnrollInProgressError extends Error {
  constructor() {
    super('이미 신청을 처리하는 중입니다.');
    this.name = 'EnrollInProgressError';
    Object.setPrototypeOf(this, EnrollInProgressError.prototype);
  }
}

/** 회차 수강 신청(FR-04) 훅. 실패하면 오류를 그대로 던지므로 화면이 문구를 고른다. */
export function useEnroll() {
  const [enrollingSessionId, setEnrollingSessionId] = useState<number>();
  const inFlight = useRef(false);

  const enroll = useCallback(async (sessionId: number): Promise<EnrollmentResponse> => {
    // 진행 중 두 번째 호출은 서버에 보내지 않고 거부한다 (버튼 비활성화 렌더 전의 더블 클릭 방지)
    if (inFlight.current) throw new EnrollInProgressError();
    inFlight.current = true;
    setEnrollingSessionId(sessionId);
    try {
      const response = await enrollmentApi.enroll({ sessionId, xEmployeeId: currentEmployeeId() });
      return response.data;
    } finally {
      inFlight.current = false;
      setEnrollingSessionId(undefined);
    }
  }, []);

  return { enroll, enrollingSessionId };
}
