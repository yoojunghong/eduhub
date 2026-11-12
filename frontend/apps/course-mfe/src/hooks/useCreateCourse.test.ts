import { act, renderHook } from '@testing-library/react';
import { courseApi } from '../api/courseApi';
import { useCreateCourse } from './useCreateCourse';

jest.mock('../api/courseApi', () => ({ courseApi: { createCourse: jest.fn() } }));
const createCourse = courseApi.createCourse as jest.Mock;

beforeEach(() => createCourse.mockReset());

test('등록 요청을 보내고 등록된 과정을 돌려준다', async () => {
  createCourse.mockResolvedValue({ data: { id: 7, title: '새 과정', status: 'OPEN', sessions: [] } });
  const { result } = renderHook(() => useCreateCourse());

  let created: unknown;
  await act(async () => {
    created = await result.current.create({ title: '새 과정', category: 'BACKEND' });
  });

  expect(createCourse).toHaveBeenCalledWith({ courseCreateRequest: { title: '새 과정', category: 'BACKEND' } });
  expect(created).toMatchObject({ id: 7 });
  expect(result.current.saving).toBe(false);
});

test('실패하면 오류를 던지고 saving을 끈다', async () => {
  createCourse.mockRejectedValue(new Error('400'));
  const { result } = renderHook(() => useCreateCourse());

  await act(async () => {
    await expect(result.current.create({ title: '', category: 'BACKEND' })).rejects.toThrow('400');
  });

  expect(result.current.saving).toBe(false);
});
