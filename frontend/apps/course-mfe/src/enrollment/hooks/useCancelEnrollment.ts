import { useCallback, useState } from 'react';
import { enrollmentApi } from '../../api/clients';

/** 신청 취소(FR-06) 훅. 실패하면 오류를 그대로 던지므로 화면이 문구를 고른다. */
export function useCancelEnrollment() {
  const [cancelingId, setCancelingId] = useState<number>();

  const cancel = useCallback(async (enrollmentId: number) => {
    setCancelingId(enrollmentId);
    try {
      await enrollmentApi.cancel({ enrollmentId });
    } finally {
      setCancelingId(undefined);
    }
  }, []);

  return { cancel, cancelingId };
}
