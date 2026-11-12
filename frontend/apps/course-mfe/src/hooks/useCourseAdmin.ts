import { useCallback, useEffect, useState } from 'react';
import type {
  CourseDetailResponse,
  CourseUpdateRequest,
  SessionCreateRequest,
} from '@eduhub/api-client';
import { courseApi } from '../api/courseApi';

/**
 * 관리 화면(FR-03)에서 과정 하나를 불러오고 수정·회차 추가·종료하는 훅.
 * id가 없으면 아무것도 불러오지 않는다. 변경 함수는 실패하면 오류를 그대로 던지므로 화면이 문구를 고른다.
 */
export function useCourseAdmin(id: number | undefined) {
  const [data, setData] = useState<CourseDetailResponse>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<unknown>();
  const [saving, setSaving] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    setData(undefined);
    setError(undefined);
    if (id === undefined) {
      setLoading(false);
      return;
    }
    let ignore = false; // 다른 과정을 빠르게 고를 때, 늦게 도착한 이전 응답은 버린다
    setLoading(true);

    courseApi
      .getCourse({ id })
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
  }, [id, reloadKey]);

  /** 변경 요청을 보내는 동안 saving을 켜 두어 버튼을 중복으로 누르지 못하게 한다. */
  const run = useCallback(async <T,>(request: () => Promise<T>): Promise<T> => {
    setSaving(true);
    try {
      return await request();
    } finally {
      setSaving(false);
    }
  }, []);

  const update = useCallback(
    (courseUpdateRequest: CourseUpdateRequest) =>
      run(async () => {
        if (id === undefined) throw new Error('과정이 선택되지 않았습니다.');
        const response = await courseApi.updateCourse({ id, courseUpdateRequest });
        setData(response.data);
      }),
    [id, run],
  );

  const addSession = useCallback(
    (sessionCreateRequest: SessionCreateRequest) =>
      run(async () => {
        if (id === undefined) throw new Error('과정이 선택되지 않았습니다.');
        await courseApi.addSession({ id, sessionCreateRequest });
        setReloadKey((k) => k + 1); // 회차 추가는 회차만 돌려주므로 과정을 다시 불러온다
      }),
    [id, run],
  );

  const close = useCallback(
    () =>
      run(async () => {
        if (id === undefined) throw new Error('과정이 선택되지 않았습니다.');
        const response = await courseApi.closeCourse({ id });
        setData(response.data);
      }),
    [id, run],
  );

  return { data, loading, error, saving, update, addSession, close };
}
