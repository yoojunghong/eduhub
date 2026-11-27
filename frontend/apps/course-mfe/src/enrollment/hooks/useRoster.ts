import { useCallback, useEffect, useState } from 'react';
import type { RosterResponse } from '@eduhub/api-client';
import { adminSessionApi } from '../../api/clients';

/** 회차 명단(FR-08) 훅. reload()로 같은 회차를 다시 조회한다. */
export function useRoster(sessionId: number) {
  const [data, setData] = useState<RosterResponse>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>();
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    let ignore = false;
    setLoading(true);
    setError(undefined);
    adminSessionApi
      .roster({ sessionId })
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
  }, [sessionId, refreshKey]);

  const reload = useCallback(() => setRefreshKey((n) => n + 1), []);
  return { data, loading, error, reload };
}
