import { act, renderHook, waitFor } from '@testing-library/react';
import { adminSessionApi } from '../../api/clients';
import { useRoster } from './useRoster';

jest.mock('../../api/clients', () => ({ adminSessionApi: { roster: jest.fn() } }));
const roster = adminSessionApi.roster as jest.Mock;

beforeEach(() => roster.mockReset());

test('회차 명단을 조회하고 reload로 다시 조회한다', async () => {
  roster.mockResolvedValue({ data: { sessionId: 10, confirmed: [], waiting: [] } });
  const { result } = renderHook(() => useRoster(10));

  await waitFor(() => expect(result.current.data?.sessionId).toBe(10));
  expect(roster).toHaveBeenCalledWith({ sessionId: 10 });

  act(() => result.current.reload());
  await waitFor(() => expect(roster).toHaveBeenCalledTimes(2));
});

test('실패하면 error를 준다', async () => {
  roster.mockRejectedValue(new Error('500'));
  const { result } = renderHook(() => useRoster(10));

  await waitFor(() => expect(result.current.error).toBeDefined());
  expect(result.current.loading).toBe(false);
});

test('재조회가 실패해도 이전 data는 남기고 error를 준다', async () => {
  roster.mockResolvedValueOnce({ data: { sessionId: 10 } }).mockRejectedValueOnce(new Error('500'));
  const { result } = renderHook(() => useRoster(10));
  await waitFor(() => expect(result.current.data?.sessionId).toBe(10));

  act(() => result.current.reload());
  await waitFor(() => expect(result.current.error).toBeDefined());
  expect(result.current.data?.sessionId).toBe(10);
});
