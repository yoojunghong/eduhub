import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { routerFuture } from '../routerFuture';
import { RosterPage } from './RosterPage';
import { adminSessionApi } from '../api/clients';

jest.mock('../api/clients', () => ({ adminSessionApi: { roster: jest.fn(), complete: jest.fn() } }));
const roster = adminSessionApi.roster as jest.Mock;
const complete = adminSessionApi.complete as jest.Mock;

const entry = (n: number, extra = {}) => ({
  enrollmentId: 100 + n,
  employeeId: n,
  employeeNo: `E00${n}`,
  name: `사원${n}`,
  department: '개발팀',
  status: 'CONFIRMED',
  ...extra,
});
const base = {
  sessionId: 10,
  courseId: 3,
  courseTitle: 'JPA 실전',
  startDate: '2026-12-10',
  place: '본사 3층',
  capacity: 20,
  completionOpen: true,
  confirmed: [
    entry(1),
    entry(2),
    entry(3, { status: 'COMPLETED' }),
    entry(4, { employeeNo: null, name: null, department: null }),
  ],
  waiting: [entry(5, { status: 'WAITING', waitingNumber: 1 })],
};

beforeEach(() => {
  roster.mockReset();
  complete.mockReset();
  roster.mockResolvedValue({ data: base });
});

const renderPage = () =>
  render(
    <MemoryRouter future={routerFuture}>
      <RosterPage sessionId={10} />
    </MemoryRouter>,
  );

test('확정·대기 명단과 알 수 없는 사원을 보여 준다', async () => {
  renderPage();

  expect(await screen.findByRole('heading', { name: 'JPA 실전 명단' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '← 과정 관리로' })).toHaveAttribute('href', '/admin/courses/3');
  expect(screen.getByText(/정원 20명/)).toBeInTheDocument();
  expect(screen.getByText('확정자 (4명)')).toBeInTheDocument();
  expect(screen.getByText('대기자 (1명)')).toBeInTheDocument();
  expect(screen.getAllByText('알 수 없음').length).toBeGreaterThanOrEqual(3);
  expect(screen.getByText('수료')).toBeInTheDocument();
  expect(within(screen.getByRole('table', { name: '대기자 명단' })).getByText('사원5')).toBeInTheDocument();
  expect(screen.getByRole('checkbox', { name: '사원3 선택' })).toBeDisabled(); // CONFIRMED만 선택 가능
});

test('completionOpen이 false면 버튼을 막고 안내한다', async () => {
  roster.mockResolvedValue({ data: { ...base, completionOpen: false } });
  renderPage();

  expect(await screen.findByText('수료 처리는 회차 시작일부터 할 수 있습니다.')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('checkbox', { name: '사원1 선택' }));
  expect(screen.getByRole('button', { name: '선택한 1명 수료 처리' })).toBeDisabled();
  expect(screen.getByRole('button', { name: '선택한 1명 미수료 처리' })).toBeDisabled();
});

test('선택 전에는 버튼이 비활성이고, 머리글 체크박스는 선택 가능한 행 전체를 고른다', async () => {
  renderPage();

  await screen.findByText('확정자 (4명)');
  expect(screen.getByRole('button', { name: '선택한 0명 수료 처리' })).toBeDisabled();
  fireEvent.click(screen.getByRole('checkbox', { name: '전체 선택' }));
  expect(screen.getByRole('button', { name: '선택한 3명 수료 처리' })).toBeEnabled(); // 사원1,2,4
});

test('선택 후 처리하면 complete를 부르고 결과(실패 사유)를 안내하며 다시 조회한다', async () => {
  complete.mockResolvedValue({ data: { processed: [1], failed: [{ employeeId: 2, reason: 'ALREADY_PROCESSED' }] } });
  renderPage();

  await screen.findByText('확정자 (4명)');
  fireEvent.click(screen.getByRole('checkbox', { name: '사원1 선택' }));
  fireEvent.click(screen.getByRole('checkbox', { name: '사원2 선택' }));
  fireEvent.click(screen.getByRole('button', { name: '선택한 2명 수료 처리' }));

  expect(await screen.findByText('1명 처리했습니다.')).toBeInTheDocument();
  expect(screen.getByText('사원2: 이미 처리됨')).toBeInTheDocument();
  expect(complete).toHaveBeenCalledWith({
    sessionId: 10,
    completionRequest: { employeeIds: [1, 2], outcome: 'COMPLETED' },
  });
  await waitFor(() => expect(roster).toHaveBeenCalledTimes(2));
  expect(screen.getByRole('button', { name: '선택한 0명 수료 처리' })).toBeDisabled(); // 선택 초기화
});

test('미수료 처리는 INCOMPLETE로 부르고 409는 서버 메시지를 안내한다', async () => {
  complete.mockRejectedValue({
    response: { status: 409, data: { code: 'COMPLETION_NOT_OPEN', message: '아직 시작 전입니다.' } },
  });
  renderPage();

  await screen.findByText('확정자 (4명)');
  fireEvent.click(screen.getByRole('checkbox', { name: '사원1 선택' }));
  fireEvent.click(screen.getByRole('button', { name: '선택한 1명 미수료 처리' }));

  expect(await screen.findByText('아직 시작 전입니다.')).toBeInTheDocument();
  expect(complete).toHaveBeenCalledWith({
    sessionId: 10,
    completionRequest: { employeeIds: [1], outcome: 'INCOMPLETE' },
  });
});

test('대기자가 없으면 안내 문구를 보여 준다', async () => {
  roster.mockResolvedValue({ data: { ...base, waiting: [] } });
  renderPage();

  expect(await screen.findByText('대기자가 없습니다.')).toBeInTheDocument();
});
