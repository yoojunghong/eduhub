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

test('미수료 처리는 INCOMPLETE로 부르고 409는 코드 문구를 안내한다', async () => {
  complete.mockRejectedValue({
    response: { status: 409, data: { code: 'COMPLETION_NOT_OPEN', message: '아직 시작 전입니다.' } },
  });
  renderPage();

  await screen.findByText('확정자 (4명)');
  fireEvent.click(screen.getByRole('checkbox', { name: '사원1 선택' }));
  fireEvent.click(screen.getByRole('button', { name: '선택한 1명 미수료 처리' }));

  expect(await screen.findByText('수료 처리는 회차 시작일부터 할 수 있습니다.')).toBeInTheDocument();
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

test('101명을 선택하면 처리 버튼을 막고 안내한다', async () => {
  const many = Array.from({ length: 101 }, (_, i) => entry(i + 1));
  roster.mockResolvedValue({ data: { ...base, confirmed: many, waiting: [] } });
  renderPage();

  await screen.findByText('확정자 (101명)');
  fireEvent.click(screen.getByRole('checkbox', { name: '전체 선택' }));
  expect(screen.getByText('한 번에 100명까지 처리할 수 있습니다.')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '선택한 101명 수료 처리' })).toBeDisabled();
  fireEvent.click(screen.getByRole('checkbox', { name: '사원101 선택' }));
  expect(screen.getByRole('button', { name: '선택한 100명 수료 처리' })).toBeEnabled();
});

test('처리 후 재조회가 실패해도 명단과 결과 안내는 남고 오류는 따로 보인다', async () => {
  complete.mockResolvedValue({ data: { processed: [1], failed: [{ employeeId: 99, reason: 'NOT_ENROLLED' }] } });
  roster.mockResolvedValueOnce({ data: base }).mockRejectedValueOnce(new Error('500'));
  renderPage();

  await screen.findByText('확정자 (4명)');
  fireEvent.click(screen.getByRole('checkbox', { name: '사원1 선택' }));
  fireEvent.click(screen.getByRole('button', { name: '선택한 1명 수료 처리' }));

  expect(await screen.findByText('명단을 다시 불러오지 못했습니다.')).toBeInTheDocument();
  expect(screen.getByText('확정자 (4명)')).toBeInTheDocument();
  expect(screen.getByText('1명 처리했습니다.')).toBeInTheDocument();
  expect(screen.getByText('사원 99: 이 회차 신청 내역 없음')).toBeInTheDocument(); // 이름 없으면 사원 ID
});

test('재조회 중에는 체크박스와 버튼을 막고, 결과 안내는 재조회 뒤에도 남는다', async () => {
  complete.mockResolvedValue({ data: { processed: [1], failed: [{ employeeId: 2, reason: 'NOT_CONFIRMED' }] } });
  let finish: (v: unknown) => void = () => {};
  roster.mockResolvedValueOnce({ data: base }).mockReturnValueOnce(new Promise((r) => (finish = r)));
  renderPage();

  await screen.findByText('확정자 (4명)');
  fireEvent.click(screen.getByRole('checkbox', { name: '사원1 선택' }));
  fireEvent.click(screen.getByRole('button', { name: '선택한 1명 수료 처리' }));

  expect(await screen.findByText('사원2: 확정 상태가 아님')).toBeInTheDocument();
  expect(screen.getByRole('checkbox', { name: '사원2 선택' })).toBeDisabled(); // 재조회 중
  finish({ data: base });
  await waitFor(() => expect(screen.getByRole('checkbox', { name: '사원2 선택' })).toBeEnabled());
  expect(screen.getByText('1명 처리했습니다.')).toBeInTheDocument();
});

test('처리 중 이중 클릭해도 complete는 한 번만 부른다', async () => {
  let finish: (v: unknown) => void = () => {};
  complete.mockReturnValue(new Promise((r) => (finish = r)));
  renderPage();

  await screen.findByText('확정자 (4명)');
  fireEvent.click(screen.getByRole('checkbox', { name: '사원1 선택' }));
  const button = screen.getByRole('button', { name: '선택한 1명 수료 처리' });
  fireEvent.click(button);
  fireEvent.click(button);

  expect(complete).toHaveBeenCalledTimes(1);
  finish({ data: { processed: [1], failed: [] } });
  expect(await screen.findByText('1명 처리했습니다.')).toBeInTheDocument();
});

test('이름이 없는 사원의 체크박스 라벨에 "사원 {id}"가 들어간다', async () => {
  renderPage();

  expect(await screen.findByRole('checkbox', { name: '사원 4 선택' })).toBeInTheDocument();
});
