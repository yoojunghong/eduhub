import { act, renderHook } from '@testing-library/react';
import { adminSessionApi } from '../../api/clients';
import { useCompletion } from './useCompletion';

jest.mock('../../api/clients', () => ({ adminSessionApi: { complete: jest.fn() } }));
const complete = adminSessionApi.complete as jest.Mock;

beforeEach(() => complete.mockReset());

test('회차·사원 id·결과로 처리하고 응답을 돌려준다', async () => {
  complete.mockResolvedValue({ data: { processed: [1], failed: [] } });
  const { result } = renderHook(() => useCompletion(10));

  let res;
  await act(async () => {
    res = await result.current.complete([1, 2], 'COMPLETED');
  });

  expect(complete).toHaveBeenCalledWith({ sessionId: 10, completionRequest: { employeeIds: [1, 2], outcome: 'COMPLETED' } });
  expect(res).toEqual({ processed: [1], failed: [] });
  expect(result.current.processing).toBe(false);
});

test('실패하면 오류를 던지고 processing을 푼다', async () => {
  complete.mockRejectedValue(new Error('409'));
  const { result } = renderHook(() => useCompletion(10));

  await act(async () => {
    await expect(result.current.complete([1], 'INCOMPLETE')).rejects.toThrow('409');
  });
  expect(result.current.processing).toBe(false);
});
