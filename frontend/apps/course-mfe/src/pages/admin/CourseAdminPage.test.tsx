import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { useCourses } from '../../hooks/useCourses';
import { useCourseAdmin } from '../../hooks/useCourseAdmin';
import { useCreateCourse } from '../../hooks/useCreateCourse';
import { CourseAdminPage } from './CourseAdminPage';

// 화면 테스트는 훅을 mock한다. API 호출 방식은 각 훅의 테스트가 따로 검증한다
jest.mock('../../hooks/useCourses');
jest.mock('../../hooks/useCourseAdmin');
jest.mock('../../hooks/useCreateCourse');
const mockUseCourses = useCourses as jest.Mock;
const mockUseCourseAdmin = useCourseAdmin as jest.Mock;
const mockUseCreateCourse = useCreateCourse as jest.Mock;

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
  mockUseCourses.mockReturnValue({
    data: {
      content: [{ id: 1, title: 'Spring Boot 입문', category: 'BACKEND', status: 'OPEN', sessionCount: 1 }],
      page: 0,
      size: 10,
      totalElements: 1,
    },
    loading: false,
    error: undefined,
  });
  admin = {
    data: undefined,
    loading: false,
    error: undefined,
    saving: false,
    update: jest.fn().mockResolvedValue(undefined),
    addSession: jest.fn().mockResolvedValue(undefined),
    close: jest.fn().mockResolvedValue(undefined),
  };
  mockUseCourseAdmin.mockImplementation((id?: number) => (id === undefined ? { ...admin, data: undefined } : admin));
  mockUseCreateCourse.mockReturnValue({ create: jest.fn(), saving: false });
});

const selectCourse = () => fireEvent.click(screen.getByRole('button', { name: /Spring Boot 입문/ }));

test('과정을 고르면 상세와 회차를 보여 준다', () => {
  admin.data = course();
  render(<CourseAdminPage />);
  expect(screen.getByText('왼쪽에서 과정을 고르거나 새 과정을 등록하세요.')).toBeInTheDocument();

  selectCourse();

  expect(mockUseCourseAdmin).toHaveBeenLastCalledWith(1);
  expect(screen.getByRole('heading', { name: 'Spring Boot 입문' })).toBeInTheDocument();
  expect(within(screen.getByRole('table', { name: '회차 목록' })).getByText('본사 3층')).toBeInTheDocument();
});

test('종료된 과정은 수정·회차 추가·종료 버튼을 막는다', () => {
  admin.data = course('CLOSED');
  render(<CourseAdminPage />);
  selectCourse();

  expect(screen.getByRole('button', { name: '수정' })).toBeDisabled();
  expect(screen.getByRole('button', { name: '회차 추가' })).toBeDisabled();
  expect(screen.getByRole('button', { name: '과정 종료' })).toBeDisabled();
});

test('종료를 확인하면 close를 부른다', async () => {
  admin.data = course();
  render(<CourseAdminPage />);
  selectCourse();

  fireEvent.click(screen.getByRole('button', { name: '과정 종료' }));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '종료' }));

  await waitFor(() => expect(admin.close).toHaveBeenCalled());
});

test('회차 정원이 1보다 작으면 추가 버튼을 막고, 올바르면 addSession을 부른다', async () => {
  admin.data = course();
  render(<CourseAdminPage />);
  selectCourse();
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
  render(<CourseAdminPage />);
  selectCourse();

  fireEvent.click(screen.getByRole('button', { name: '수정' }));
  const dialog = within(screen.getByRole('dialog'));
  expect(dialog.getByLabelText(/과정명/)).toHaveValue('Spring Boot 입문');
  fireEvent.click(dialog.getByRole('button', { name: '저장' }));

  expect(await dialog.findByRole('alert')).toHaveTextContent('종료된 과정은 수정하거나 회차를 추가할 수 없습니다.');
});
