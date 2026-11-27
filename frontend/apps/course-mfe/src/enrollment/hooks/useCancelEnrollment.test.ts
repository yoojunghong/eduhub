import { act, renderHook } from '@testing-library/react';
import { enrollmentApi } from '../../api/clients';
import { useCancelEnrollment } from './useCancelEnrollment';

jest.mock('../../api/clients', () => ({ enrollmentApi: { cancel: jest.fn() } }));
const cancel = enrollmentApi.cancel as jest.Mock;

beforeEach(() => cancel.mockReset());

test('신청 id로 취소한다', async () => {
  cancel.mockResolvedValue({ data: {} });
  const { result } = renderHook(() => useCancelEnrollment());

  await act(async () => {
    await result.current.cancel(31);
  });

  expect(cancel).toHaveBeenCalledWith({ enrollmentId: 31 });
  expect(result.current.cancelingId).toBeUndefined();
});

test('실패하면 오류를 그대로 던진다', async () => {
  cancel.mockRejectedValue(new Error('409'));
  const { result } = renderHook(() => useCancelEnrollment());

  await act(async () => {
    await expect(result.current.cancel(31)).rejects.toThrow('409');
  });
  expect(result.current.cancelingId).toBeUndefined();
});
