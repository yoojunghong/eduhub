import { act, renderHook, waitFor } from '@testing-library/react';
import { enrollmentApi } from '../../api/clients';
import { useMyEnrollments } from './useMyEnrollments';

jest.mock('../../api/clients', () => ({ enrollmentApi: { myEnrollments: jest.fn() } }));
const myEnrollments = enrollmentApi.myEnrollments as jest.Mock;

beforeEach(() => {
  myEnrollments.mockReset();
  myEnrollments.mockResolvedValue({ data: [{ enrollmentId: 1 }] });
});

test('status를 인자로 넘겨 조회하고, 바뀌면 다시 조회한다', async () => {
  const { result, rerender } = renderHook(({ s }) => useMyEnrollments(s), {
    initialProps: { s: undefined as 'WAITING' | undefined },
  });
  await waitFor(() => expect(result.current.data).toEqual([{ enrollmentId: 1 }]));
  expect(myEnrollments).toHaveBeenLastCalledWith({ status: undefined });

  rerender({ s: 'WAITING' });
  await waitFor(() => expect(myEnrollments).toHaveBeenLastCalledWith({ status: 'WAITING' }));
});

test('reload는 같은 조건으로 다시 조회한다', async () => {
  const { result } = renderHook(() => useMyEnrollments('CONFIRMED'));
  await waitFor(() => expect(result.current.loading).toBe(false));

  act(() => result.current.reload());
  await waitFor(() => expect(myEnrollments).toHaveBeenCalledTimes(2));
});

test('실패하면 error를 돌려준다', async () => {
  myEnrollments.mockRejectedValue(new Error('500'));
  const { result } = renderHook(() => useMyEnrollments());
  await waitFor(() => expect(result.current.error).toBeInstanceOf(Error));
});
