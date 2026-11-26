import { act, renderHook } from '@testing-library/react';
import { enrollmentApi } from '../api/courseApi';
import { EnrollInProgressError, useEnroll } from './useEnroll';

jest.mock('../api/courseApi', () => ({ enrollmentApi: { enroll: jest.fn() } }));
const enroll = enrollmentApi.enroll as jest.Mock;

beforeEach(() => {
  enroll.mockReset();
});

test('회차 id로 신청하고 결과를 돌려준다', async () => {
  enroll.mockResolvedValue({ data: { id: 1, sessionId: 10, status: 'CONFIRMED' } });
  const { result } = renderHook(() => useEnroll());

  let response: unknown;
  await act(async () => {
    response = await result.current.enroll(10);
  });

  expect(enroll).toHaveBeenCalledWith({ sessionId: 10 });
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

test('진행 중 두 번째 호출은 서버에 보내지 않고 거부한다', async () => {
  let resolve!: (v: unknown) => void;
  enroll.mockReturnValue(new Promise((r) => (resolve = r)));
  const { result } = renderHook(() => useEnroll());

  let first!: Promise<unknown>;
  await act(async () => {
    first = result.current.enroll(10);
    await expect(result.current.enroll(11)).rejects.toBeInstanceOf(EnrollInProgressError);
  });
  expect(enroll).toHaveBeenCalledTimes(1);

  await act(async () => {
    resolve({ data: { id: 1 } });
    await first;
  });
  // 끝난 뒤에는 다시 신청할 수 있다
  enroll.mockResolvedValue({ data: { id: 2 } });
  await act(async () => {
    await result.current.enroll(12);
  });
  expect(enroll).toHaveBeenCalledTimes(2);
});

test('401로 실패하면 오류를 던지고 신청 중 상태를 해제한다', async () => {
  const unauthorized = Object.assign(new Error('401'), { response: { status: 401 } });
  enroll.mockRejectedValue(unauthorized);
  const { result } = renderHook(() => useEnroll());

  await act(async () => {
    await expect(result.current.enroll(10)).rejects.toBe(unauthorized);
  });
  expect(result.current.enrollingSessionId).toBeUndefined();
});
