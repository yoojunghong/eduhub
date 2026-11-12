import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { routerFuture } from '../../routerFuture';
import { useCourseAdmin } from '../../hooks/useCourseAdmin';
import { CourseAdminDetailPage } from './CourseAdminDetailPage';

// 화면 테스트는 훅을 mock한다. API 호출 방식은 각 훅의 테스트가 따로 검증한다
jest.mock('../../hooks/useCourseAdmin');
const mockUseCourseAdmin = useCourseAdmin as jest.Mock;

const course = (status = 'OPEN') => ({
  id: 1,
  title: 'Spring Boot 입문',
  category: 'BACKEND',
  description: '스프링 부트 기초',
  status,
  sessions: [{ id: 10, startDate: '2026-12-01', place: '본사 3층', capacity: 20, remainingSeats: 20, status }],
});

let admin: Record<string, unknown>;

beforeEach(() => {
  admin = {
    data: undefined,
    loading: false,
    error: undefined,
    saving: false,
    update: jest.fn().mockResolvedValue(undefined),
    addSession: jest.fn().mockResolvedValue(undefined),
    close: jest.fn().mockResolvedValue(undefined),
  };
  mockUseCourseAdmin.mockReturnValue(admin);
});

const renderPage = () =>
  render(
    <MemoryRouter future={routerFuture}>
      <CourseAdminDetailPage id={1} />
    </MemoryRouter>,
  );

test('과정 상세와 회차를 보여 준다', () => {
  admin.data = course();
  renderPage();

  expect(mockUseCourseAdmin).toHaveBeenCalledWith(1);
  expect(screen.getByRole('heading', { name: 'Spring Boot 입문' })).toBeInTheDocument();
  expect(within(screen.getByRole('table', { name: '회차 목록' })).getByText('본사 3층')).toBeInTheDocument();
});

test('종료된 과정은 수정·회차 추가·종료 버튼을 막는다', () => {
  admin.data = course('CLOSED');
  renderPage();

  expect(screen.getByRole('button', { name: '수정' })).toBeDisabled();
  expect(screen.getByRole('button', { name: '회차 추가' })).toBeDisabled();
  expect(screen.getByRole('button', { name: '과정 종료' })).toBeDisabled();
});

test('종료를 확인하면 close를 부른다', async () => {
  admin.data = course();
  renderPage();

  fireEvent.click(screen.getByRole('button', { name: '과정 종료' }));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '종료' }));

  await waitFor(() => expect(admin.close).toHaveBeenCalled());
});

test('회차 정원이 1보다 작으면 추가 버튼을 막고, 올바르면 addSession을 부른다', async () => {
  admin.data = course();
  renderPage();
  fireEvent.click(screen.getByRole('button', { name: '회차 추가' }));
  const dialog = within(screen.getByRole('dialog'));

  fireEvent.change(dialog.getByLabelText(/시작일/), { target: { value: '2026-12-15' } });
  fireEvent.change(dialog.getByLabelText(/장소/), { target: { value: '본사 5층' } });
  fireEvent.change(dialog.getByLabelText(/정원/), { target: { value: '0' } });
  expect(dialog.getByRole('button', { name: '추가' })).toBeDisabled();
  expect(dialog.getByText('정원은 1 이상이어야 합니다.')).toBeInTheDocument();

  fireEvent.change(dialog.getByLabelText(/정원/), { target: { value: '15' } });
  fireEvent.click(dialog.getByRole('button', { name: '추가' }));

  await waitFor(() =>
    expect(admin.addSession).toHaveBeenCalledWith({ startDate: '2026-12-15', place: '본사 5층', capacity: 15 }),
  );
});

test('수정이 409로 실패하면 다이얼로그에 이유를 보여 준다', async () => {
  admin.data = course();
  admin.update = jest
    .fn()
    .mockRejectedValue(Object.assign(new Error('409'), { response: { status: 409, data: { code: 'COURSE_CLOSED' } } }));
  renderPage();

  fireEvent.click(screen.getByRole('button', { name: '수정' }));
  const dialog = within(screen.getByRole('dialog'));
  expect(dialog.getByLabelText(/과정명/)).toHaveValue('Spring Boot 입문');
  fireEvent.click(dialog.getByRole('button', { name: '저장' }));

  expect(await dialog.findByRole('alert')).toHaveTextContent('종료된 과정은 수정하거나 회차를 추가할 수 없습니다.');
});
