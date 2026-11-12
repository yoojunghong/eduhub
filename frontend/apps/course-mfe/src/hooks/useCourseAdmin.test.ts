import { act, renderHook, waitFor } from '@testing-library/react';
import { courseApi } from '../api/courseApi';
import { useCourseAdmin } from './useCourseAdmin';

jest.mock('../api/courseApi', () => ({
  courseApi: { getCourse: jest.fn(), updateCourse: jest.fn(), addSession: jest.fn(), closeCourse: jest.fn() },
}));
const api = courseApi as unknown as Record<'getCourse' | 'updateCourse' | 'addSession' | 'closeCourse', jest.Mock>;

const detail = (overrides = {}) => ({
  data: { id: 1, title: 'Spring Boot 입문', category: 'BACKEND', status: 'OPEN', sessions: [], ...overrides },
});

beforeEach(() => Object.values(api).forEach((m) => m.mockReset()));

test('id가 없으면 아무것도 불러오지 않는다', () => {
  const { result } = renderHook(() => useCourseAdmin(undefined));

  expect(result.current.loading).toBe(false);
  expect(result.current.data).toBeUndefined();
  expect(api.getCourse).not.toHaveBeenCalled();
});

test('id가 있으면 과정 상세를 불러온다', async () => {
  api.getCourse.mockResolvedValue(detail());

  const { result } = renderHook(() => useCourseAdmin(1));

  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(api.getCourse).toHaveBeenCalledWith({ id: 1 });
  expect(result.current.data?.title).toBe('Spring Boot 입문');
});

test('수정하면 응답으로 data를 바꾼다', async () => {
  api.getCourse.mockResolvedValue(detail());
  api.updateCourse.mockResolvedValue(detail({ title: 'JPA 실전' }));
  const { result } = renderHook(() => useCourseAdmin(1));
  await waitFor(() => expect(result.current.loading).toBe(false));

  await act(() => result.current.update({ title: 'JPA 실전', category: 'BACKEND' }));

  expect(api.updateCourse).toHaveBeenCalledWith({
    id: 1,
    courseUpdateRequest: { title: 'JPA 실전', category: 'BACKEND' },
  });
  expect(result.current.data?.title).toBe('JPA 실전');
  expect(result.current.saving).toBe(false);
});

test('회차를 추가하면 과정을 다시 불러온다', async () => {
  api.getCourse.mockResolvedValueOnce(detail());
  api.getCourse.mockResolvedValueOnce(
    detail({ sessions: [{ id: 10, startDate: '2026-12-01', place: '본사 3층', capacity: 20, status: 'OPEN' }] }),
  );
  api.addSession.mockResolvedValue({ data: { id: 10 } });
  const { result } = renderHook(() => useCourseAdmin(1));
  await waitFor(() => expect(result.current.loading).toBe(false));

  await act(() => result.current.addSession({ startDate: '2026-12-01', place: '본사 3층', capacity: 20 }));

  await waitFor(() => expect(result.current.data?.sessions).toHaveLength(1));
  expect(api.getCourse).toHaveBeenCalledTimes(2);
});

test('종료하면 CLOSED 상태로 바뀐다', async () => {
  api.getCourse.mockResolvedValue(detail());
  api.closeCourse.mockResolvedValue(detail({ status: 'CLOSED' }));
  const { result } = renderHook(() => useCourseAdmin(1));
  await waitFor(() => expect(result.current.loading).toBe(false));

  await act(() => result.current.close());

  expect(api.closeCourse).toHaveBeenCalledWith({ id: 1 });
  expect(result.current.data?.status).toBe('CLOSED');
});

test('변경이 실패하면 오류를 던지고 saving을 끈다', async () => {
  api.getCourse.mockResolvedValue(detail());
  const conflict = Object.assign(new Error('409'), { response: { status: 409, data: { code: 'COURSE_CLOSED' } } });
  api.updateCourse.mockRejectedValue(conflict);
  const { result } = renderHook(() => useCourseAdmin(1));
  await waitFor(() => expect(result.current.loading).toBe(false));

  await act(async () => {
    await expect(result.current.update({ title: 'x', category: 'BACKEND' })).rejects.toBe(conflict);
  });

  expect(result.current.saving).toBe(false);
  expect(result.current.data?.title).toBe('Spring Boot 입문');
});
