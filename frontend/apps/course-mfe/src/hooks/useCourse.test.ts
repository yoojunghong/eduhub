import { renderHook, waitFor } from '@testing-library/react';
import { courseApi } from '../api/courseApi';
import { useCourse } from './useCourse';

jest.mock('../api/courseApi', () => ({ courseApi: { getCourse: jest.fn() } }));
const getCourse = courseApi.getCourse as jest.Mock;

const detail = {
  id: 1,
  title: 'Spring Boot 입문',
  category: 'BACKEND',
  description: '스프링 부트 기초',
  status: 'OPEN',
  sessions: [{ id: 10, startDate: '2026-12-01', place: '본관 301호', capacity: 20, remainingSeats: 5, status: 'OPEN' }],
};

beforeEach(() => getCourse.mockReset());

test('과정 상세를 불러와 data로 돌려준다', async () => {
  getCourse.mockResolvedValue({ data: detail });

  const { result } = renderHook(() => useCourse(1));

  expect(result.current.loading).toBe(true);
  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(result.current.data?.title).toBe('Spring Boot 입문');
  expect(result.current.data?.sessions).toHaveLength(1);
  expect(result.current.error).toBeUndefined();
  expect(result.current.notFound).toBe(false);
});

test('id를 그대로 API에 넘긴다', async () => {
  getCourse.mockResolvedValue({ data: detail });

  renderHook(() => useCourse(7));

  await waitFor(() => expect(getCourse).toHaveBeenCalledWith({ id: 7 }));
});

test('404면 notFound를 true로, error는 비워 둔다', async () => {
  getCourse.mockRejectedValue(Object.assign(new Error('Not Found'), { response: { status: 404 } }));

  const { result } = renderHook(() => useCourse(999));

  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(result.current.notFound).toBe(true);
  expect(result.current.error).toBeUndefined();
  expect(result.current.data).toBeUndefined();
});

test('404가 아닌 실패는 error로 돌려준다', async () => {
  getCourse.mockRejectedValue(new Error('Network Error'));

  const { result } = renderHook(() => useCourse(1));

  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(result.current.error).toBeInstanceOf(Error);
  expect(result.current.notFound).toBe(false);
});
