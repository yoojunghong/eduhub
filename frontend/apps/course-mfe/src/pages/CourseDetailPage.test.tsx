import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { routerFuture } from '../routerFuture';
import { useCourse } from '../hooks/useCourse';
import { EnrollInProgressError, useEnroll } from '../hooks/useEnroll';
import { CourseDetailPage } from './CourseDetailPage';

// 화면 테스트는 훅을 mock한다. API 호출 방식은 useCourse.test.ts, useEnroll.test.ts가 따로 검증한다
jest.mock('../hooks/useCourse');
jest.mock('../hooks/useEnroll');
const mockUseCourse = useCourse as jest.Mock;
const mockUseEnroll = useEnroll as jest.Mock;
const enroll = jest.fn();
const reload = jest.fn();

beforeEach(() => {
  enroll.mockReset();
  reload.mockReset();
  mockUseEnroll.mockReturnValue({ enroll, enrollingSessionId: undefined });
});

const openSession = (remainingSeats: number) =>
  ({ id: 10, startDate: '2026-12-01', place: '본관 301호', capacity: 20, remainingSeats, waitingCount: 0, waitlistLimit: 10, status: 'OPEN' });

test('자리가 있으면 신청하고, 확정되면 안내한 뒤 다시 불러온다', async () => {
  mockUseCourse.mockReturnValue({ ...course([openSession(5)]), reload });
  enroll.mockResolvedValue({ id: 1, sessionId: 10, status: 'CONFIRMED' });
  renderPage();

  fireEvent.click(screen.getByRole('button', { name: '신청' }));

  expect(await screen.findByText('수강 신청이 확정되었습니다.')).toBeInTheDocument();
  expect(enroll).toHaveBeenCalledWith(10);
  expect(reload).toHaveBeenCalled();
});

test('남은 자리가 없으면 대기 신청 버튼이고, 대기 등록을 안내한다', async () => {
  mockUseCourse.mockReturnValue({ ...course([openSession(0)]), reload });
  enroll.mockResolvedValue({ id: 2, sessionId: 10, status: 'WAITING' });
  renderPage();

  fireEvent.click(screen.getByRole('button', { name: '대기 신청' }));

  expect(await screen.findByText('대기자로 등록되었습니다.')).toBeInTheDocument();
});

test('대기 결과에 순번이 있으면 대기 N번으로 안내한다', async () => {
  mockUseCourse.mockReturnValue({ ...course([openSession(0)]), reload });
  enroll.mockResolvedValue({ id: 2, sessionId: 10, status: 'WAITING', waitingNumber: 3 });
  renderPage();

  fireEvent.click(screen.getByRole('button', { name: '대기 신청' }));

  expect(await screen.findByText('대기 3번으로 등록되었습니다.')).toBeInTheDocument();
});

test('대기 인원이 가득 차면(WAITLIST_FULL) 이유를 보여 준다', async () => {
  mockUseCourse.mockReturnValue({ ...course([openSession(0)]), reload });
  enroll.mockRejectedValue(
    Object.assign(new Error('409'), { response: { status: 409, data: { code: 'WAITLIST_FULL' } } }),
  );
  renderPage();

  fireEvent.click(screen.getByRole('button', { name: '대기 신청' }));

  expect(await screen.findByRole('alert')).toHaveTextContent('대기 인원이 가득 찼습니다.');
});

test('빈 자리가 있어도 대기자가 있으면 대기 신청 버튼이다', () => {
  mockUseCourse.mockReturnValue({ ...course([{ ...openSession(2), waitingCount: 1 }]), reload });
  renderPage();

  expect(screen.getByRole('button', { name: '대기 신청' })).toBeEnabled();
  expect(screen.getByText('대기 가능')).toBeInTheDocument();
  expect(screen.queryByText('신청 가능')).not.toBeInTheDocument();
});

test('대기 상한에 도달하면 마감 버튼이 비활성이고 칩이 마감이다', () => {
  mockUseCourse.mockReturnValue({ ...course([{ ...openSession(0), waitingCount: 10 }]), reload });
  renderPage();

  expect(within(screen.getAllByRole('row')[1]).getAllByText('마감')).toHaveLength(2); // 칩 + 버튼
  expect(screen.getByRole('button', { name: '마감' })).toBeDisabled();
});

test('정원 1(상한 0)이고 정원이 차면 마감 버튼이 비활성이다', () => {
  mockUseCourse.mockReturnValue({ ...course([{ ...openSession(0), capacity: 1, waitlistLimit: 0 }]), reload });
  renderPage();

  expect(screen.getByRole('button', { name: '마감' })).toBeDisabled();
});

test('중복 신청(409)이면 이유를 보여 준다', async () => {
  mockUseCourse.mockReturnValue({ ...course([openSession(5)]), reload });
  enroll.mockRejectedValue(
    Object.assign(new Error('409'), { response: { status: 409, data: { code: 'DUPLICATE_ENROLLMENT' } } }),
  );
  renderPage();

  fireEvent.click(screen.getByRole('button', { name: '신청' }));

  expect(await screen.findByRole('alert')).toHaveTextContent('이미 이 과정에 신청(확정·대기)한 내역이 있습니다.');
  expect(reload).toHaveBeenCalled(); // 실패 뒤에도 최신 상태로 갱신
});

test('진행 중 두 번째 클릭은 오류 알림을 띄우지 않는다', async () => {
  mockUseCourse.mockReturnValue({ ...course([openSession(5)]), reload });
  enroll.mockRejectedValue(new EnrollInProgressError());
  renderPage();

  fireEvent.click(screen.getByRole('button', { name: '신청' }));

  await waitFor(() => expect(enroll).toHaveBeenCalledWith(10));
  await act(async () => {});
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  expect(reload).not.toHaveBeenCalled();
});

test('종료된 회차는 신청 버튼을 막는다', () => {
  mockUseCourse.mockReturnValue({ ...course([{ ...openSession(5), status: 'CLOSED' }]), reload });
  renderPage();

  expect(screen.getByRole('button', { name: '신청' })).toBeDisabled();
});

const renderPage = () =>
  render(
    <MemoryRouter future={routerFuture}>
      <CourseDetailPage id={1} />
    </MemoryRouter>,
  );

const course = (sessions: object[]) => ({
  data: { id: 1, title: 'Spring Boot 입문', category: 'BACKEND', description: '스프링 부트 기초', status: 'OPEN', sessions },
  loading: false,
  error: undefined,
  notFound: false,
});

test('과정 정보와 회차(일정, 장소, 정원, 남은 자리)를 보여 준다', () => {
  mockUseCourse.mockReturnValue(
    course([{ id: 10, startDate: '2026-12-01', place: '본관 301호', capacity: 20, remainingSeats: 5, waitingCount: 0, waitlistLimit: 10, status: 'OPEN' }]),
  );

  renderPage();

  expect(screen.getByRole('heading', { name: 'Spring Boot 입문' })).toBeInTheDocument();
  expect(screen.getByText('스프링 부트 기초')).toBeInTheDocument();
  const row = screen.getAllByRole('row')[1];
  expect(within(row).getByRole('cell', { name: '2026-12-01' })).toBeInTheDocument();
  expect(within(row).getByRole('cell', { name: '본관 301호' })).toBeInTheDocument();
  expect(within(row).getByRole('cell', { name: '20' })).toBeInTheDocument();
  expect(within(row).getByRole('cell', { name: '5' })).toBeInTheDocument();
  expect(within(row).getByText('신청 가능')).toBeInTheDocument();
});

test('남은 자리가 0이면 마감으로 표시한다', () => {
  mockUseCourse.mockReturnValue(
    course([{ id: 10, startDate: '2026-12-01', place: '본관 301호', capacity: 20, remainingSeats: 0, waitingCount: 0, waitlistLimit: 0, status: 'OPEN' }]),
  );

  renderPage();

  expect(screen.getAllByText('마감')).toHaveLength(2); // 칩 + 버튼
});

test('회차가 없으면 안내 문구를 보여 준다', () => {
  mockUseCourse.mockReturnValue(course([]));

  renderPage();

  expect(screen.getByText('등록된 회차가 없습니다.')).toBeInTheDocument();
  expect(screen.queryByRole('table')).not.toBeInTheDocument();
});

test('없는 과정(404)이면 안내와 목록 링크를 보여 준다', () => {
  mockUseCourse.mockReturnValue({ data: undefined, loading: false, error: undefined, notFound: true });

  renderPage();

  expect(screen.getByRole('alert')).toHaveTextContent('과정을 찾을 수 없습니다.');
  expect(screen.getByRole('link', { name: '← 목록으로' })).toHaveAttribute('href', '/');
});

test('불러오기에 실패하면 에러 메시지를 보여 준다', () => {
  mockUseCourse.mockReturnValue({ data: undefined, loading: false, error: new Error('fail'), notFound: false });

  renderPage();

  expect(screen.getByRole('alert')).toHaveTextContent('과정 정보를 불러오지 못했습니다.');
});
