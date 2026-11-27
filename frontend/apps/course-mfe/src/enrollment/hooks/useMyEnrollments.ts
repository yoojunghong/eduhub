import { useCallback, useEffect, useRef, useState } from 'react';
import type { MyEnrollmentResponse, MyEnrollmentsStatusEnum } from '@eduhub/api-client';
import { enrollmentApi } from '../../api/clients';

/** 내 신청 내역(FR-07) 훅. status가 없으면 전체. reload()로 같은 조건으로 다시 조회한다. */
export function useMyEnrollments(status?: MyEnrollmentsStatusEnum) {
  const [data, setData] = useState<MyEnrollmentResponse[]>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>();
  const [refreshKey, setRefreshKey] = useState(0);

  const lastStatus = useRef(status);

  useEffect(() => {
    // 필터가 바뀌면 이전 필터의 행을 비운다. reload는 기존 행을 유지한다
    if (lastStatus.current !== status) {
      lastStatus.current = status;
      setData(undefined);
    }
    let ignore = false; // 필터를 빠르게 바꿀 때 늦게 도착한 이전 응답은 버린다
    setLoading(true);
    setError(undefined);
    enrollmentApi
      .myEnrollments({ status })
      .then((response) => {
        if (!ignore) setData(response.data);
      })
      .catch((e: unknown) => {
        if (!ignore) setError(e);
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [status, refreshKey]);

  const reload = useCallback(() => setRefreshKey((n) => n + 1), []);
  return { data, loading, error, reload };
}
