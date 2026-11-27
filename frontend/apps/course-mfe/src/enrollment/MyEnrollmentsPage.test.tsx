import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { routerFuture } from '../routerFuture';
import { MyEnrollmentsPage } from './MyEnrollmentsPage';
import { enrollmentApi } from '../api/clients';

jest.mock('../api/clients', () => ({ enrollmentApi: { myEnrollments: jest.fn(), cancel: jest.fn() } }));
const myEnrollments = enrollmentApi.myEnrollments as jest.Mock;
const cancel = enrollmentApi.cancel as jest.Mock;

const confirmed = {
  enrollmentId: 31,
  sessionId: 10,
  courseId: 3,
  courseTitle: 'JPA 실전',
  startDate: '2026-12-10',
  place: '본사 3층',
  status: 'CONFIRMED',
  enrolledAt: '2026-11-27T10:05:00+09:00',
  cancelable: true,
};
const waiting = { ...confirmed, enrollmentId: 32, courseId: 4, courseTitle: 'React 기초', status: 'WAITING', waitingNumber: 2 };
const done = { ...confirmed, enrollmentId: 33, courseTitle: '지난 과정', status: 'COMPLETED', cancelable: false };

beforeEach(() => {
  myEnrollments.mockReset();
  cancel.mockReset();
  myEnrollments.mockResolvedValue({ data: [confirmed, waiting, done] });
});

const renderPage = () =>
  render(
    <MemoryRouter future={routerFuture}>
      <MyEnrollmentsPage />
    </MemoryRouter>,
  );

test('목록을 표로 보여 준다: 링크, 시작일, 장소, 상태 칩, 신청일시', async () => {
  renderPage();

  expect(await screen.findByRole('link', { name: 'JPA 실전' })).toHaveAttribute('href', '/courses/3');
  expect(screen.getByRole('heading', { name: '내 신청 내역' })).toBeInTheDocument();
  const row = screen.getAllByRole('row')[1];
  expect(within(row).getByText('2026-12-10')).toBeInTheDocument();
  expect(within(row).getByText('본사 3층')).toBeInTheDocument();
  expect(within(row).getByText('확정')).toBeInTheDocument();
  expect(within(row).getByText('2026-11-27 10:05')).toBeInTheDocument();
  expect(screen.getByText('대기 2번')).toBeInTheDocument();
  expect(within(screen.getByRole('table')).getByText('수료')).toBeInTheDocument();
  expect(within(screen.getByRole('table')).getAllByRole('button', { name: '취소' })).toHaveLength(2); // cancelable만
  expect(myEnrollments).toHaveBeenLastCalledWith({ status: undefined });
});

test('필터를 바꾸면 status 인자로 다시 조회한다', async () => {
  renderPage();
  await screen.findByText('대기 2번');

  fireEvent.click(screen.getByRole('button', { name: '대기' }));
  await waitFor(() => expect(myEnrollments).toHaveBeenLastCalledWith({ status: 'WAITING' }));

  fireEvent.click(screen.getByRole('button', { name: '미수료' }));
  await waitFor(() => expect(myEnrollments).toHaveBeenLastCalledWith({ status: 'INCOMPLETE' }));

  fireEvent.click(screen.getByRole('button', { name: '전체' }));
  await waitFor(() => expect(myEnrollments).toHaveBeenLastCalledWith({ status: undefined }));
});

test('취소: 확인 대화상자 후 취소하고 안내한 뒤 목록을 다시 조회한다', async () => {
  cancel.mockResolvedValue({ data: {} });
  renderPage();
  await screen.findByText('대기 2번');
  const calls = myEnrollments.mock.calls.length;

  fireEvent.click(within(screen.getByRole('table')).getAllByRole('button', { name: '취소' })[0]);
  expect(await screen.findByText('JPA 실전을(를) 취소할까요?')).toBeInTheDocument();
  expect(cancel).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '신청 취소' }));

  expect(await screen.findByText('신청을 취소했습니다.')).toBeInTheDocument();
  expect(cancel).toHaveBeenCalledWith({ enrollmentId: 31 });
  await waitFor(() => expect(myEnrollments.mock.calls.length).toBeGreaterThan(calls));
});

test('대화상자에서 닫으면 취소하지 않는다', async () => {
  renderPage();
  await screen.findByText('대기 2번');

  fireEvent.click(within(screen.getByRole('table')).getAllByRole('button', { name: '취소' })[0]);
  fireEvent.click(await screen.findByRole('button', { name: '닫기' }));

  expect(cancel).not.toHaveBeenCalled();
});

test('취소가 409이면 서버 메시지를 안내하고 다시 조회한다', async () => {
  cancel.mockRejectedValue(
    Object.assign(new Error('409'), {
      response: { status: 409, data: { code: 'CANCEL_PERIOD_ENDED', message: '시작일 전날까지만 취소할 수 있습니다.' } },
    }),
  );
  renderPage();
  await screen.findByText('대기 2번');
  const calls = myEnrollments.mock.calls.length;

  fireEvent.click(within(screen.getByRole('table')).getAllByRole('button', { name: '취소' })[0]);
  fireEvent.click(await screen.findByRole('button', { name: '신청 취소' }));

  expect(await screen.findByRole('alert')).toHaveTextContent('시작일 전날까지만 취소할 수 있습니다.');
  await waitFor(() => expect(myEnrollments.mock.calls.length).toBeGreaterThan(calls));
});

test('내역이 없으면 안내 문구를 보여 준다', async () => {
  myEnrollments.mockResolvedValue({ data: [] });
  renderPage();

  expect(await screen.findByText('신청 내역이 없습니다.')).toBeInTheDocument();
  expect(screen.queryByRole('table')).not.toBeInTheDocument();
});

test('불러오기에 실패하면 오류를 보여 준다', async () => {
  myEnrollments.mockRejectedValue(new Error('500'));
  renderPage();

  expect(await screen.findByRole('alert')).toHaveTextContent('신청 내역을 불러오지 못했습니다.');
});
