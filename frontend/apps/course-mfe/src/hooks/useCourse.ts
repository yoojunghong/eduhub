import { useCallback, useEffect, useState } from 'react';
import type { CourseDetailResponse } from '@eduhub/api-client';
import { courseApi } from '../api/courseApi';
import { isNotFound } from '../api/errors';

/**
 * 과정 상세와 회차(FR-02)를 불러오는 훅.
 * 없는 과정(404)이면 notFound가 true이고, 그 밖의 실패는 error에 담는다.
 */
export function useCourse(id: number) {
  const [data, setData] = useState<CourseDetailResponse>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>();
  const [notFound, setNotFound] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let ignore = false; // 다른 과정으로 빠르게 옮겨 갈 때, 늦게 도착한 이전 응답은 버린다
    setData(undefined);
    setLoading(true);
    setError(undefined);
    setNotFound(false);

    courseApi
      .getCourse({ id })
      .then((response) => {
        if (!ignore) setData(response.data);
      })
      .catch((e: unknown) => {
        if (ignore) return;
        if (isNotFound(e)) setNotFound(true);
        else setError(e);
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, [id, reloadKey]);

  /** 신청 뒤 남은 자리를 새로 보려고 같은 과정을 다시 불러온다. */
  const reload = useCallback(() => setReloadKey((k) => k + 1), []);

  return { data, loading, error, notFound, reload };
}
