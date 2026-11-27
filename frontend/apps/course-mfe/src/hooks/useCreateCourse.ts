import { useCallback, useState } from 'react';
import type { CourseCreateRequest, CourseDetailResponse } from '@eduhub/api-client';
import { courseApi } from '../api/clients';

/** 과정 등록(FR-03) 훅. 실패하면 오류를 그대로 던지므로 화면이 문구를 고른다. */
export function useCreateCourse() {
  const [saving, setSaving] = useState(false);

  const create = useCallback(
    async (courseCreateRequest: CourseCreateRequest): Promise<CourseDetailResponse> => {
      setSaving(true);
      try {
        const response = await courseApi.createCourse({ courseCreateRequest });
        return response.data;
      } finally {
        setSaving(false);
      }
    },
    [],
  );

  return { create, saving };
}
