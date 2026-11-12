import { act, renderHook } from '@testing-library/react';
import { enrollmentApi } from '../api/courseApi';
import { currentEmployeeId, useEnroll } from './useEnroll';

jest.mock('../api/courseApi', () => ({ enrollmentApi: { enroll: jest.fn() } }));
const enroll = enrollmentApi.enroll as jest.Mock;

beforeEach(() => {
  enroll.mockReset();
  window.localStorage.clear();
});

test('회차 id와 직원 번호 헤더로 신청하고 결과를 돌려준다', async () => {
  window.localStorage.setItem('eduhub.employeeId', '2002');
  enroll.mockResolvedValue({ data: { id: 1, sessionId: 10, status: 'CONFIRMED' } });
  const { result } = renderHook(() => useEnroll());

  let response: unknown;
  await act(async () => {
    response = await result.current.enroll(10);
  });

  expect(enroll).toHaveBeenCalledWith({ sessionId: 10, xEmployeeId: 2002 });
  expect(response).toEqual({ id: 1, sessionId: 10, status: 'CONFIRMED' });
  expect(result.current.enrollingSessionId).toBeUndefined();
});

test('실패하면 오류를 그대로 던진다', async () => {
  enroll.mockRejectedValue(new Error('409'));
  const { result } = renderHook(() => useEnroll());

  await act(async () => {
    await expect(result.current.enroll(10)).rejects.toThrow('409');
  });
});

test('직원 번호가 없거나 잘못되면 1001을 쓴다', () => {
  expect(currentEmployeeId()).toBe(1001);
  window.localStorage.setItem('eduhub.employeeId', 'abc');
  expect(currentEmployeeId()).toBe(1001);
});
