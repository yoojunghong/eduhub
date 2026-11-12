import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { routerFuture } from '../../routerFuture';
import { useCourses } from '../../hooks/useCourses';
import { useCreateCourse } from '../../hooks/useCreateCourse';
import { CourseAdminPage } from './CourseAdminPage';

jest.mock('../../hooks/useCourses');
jest.mock('../../hooks/useCreateCourse');
const mockUseCourses = useCourses as jest.Mock;
const mockUseCreateCourse = useCreateCourse as jest.Mock;

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/admin']} future={routerFuture}>
      <Routes>
        <Route path="/admin" element={<CourseAdminPage />} />
        <Route path="/admin/courses/:id" element={<p>관리 상세</p>} />
      </Routes>
    </MemoryRouter>,
  );

beforeEach(() => {
  mockUseCourses.mockReturnValue({
    data: {
      content: [
        { id: 1, title: 'Spring Boot 기초', category: '개발', status: 'OPEN', sessionCount: 1 },
        { id: 2, title: '[확인용] 관리 화면 테스트 과정', category: '테스트', status: 'CLOSED', sessionCount: 0 },
      ],
      page: 0,
      size: 10,
      totalElements: 2,
    },
    loading: false,
    error: undefined,
  });
  mockUseCreateCourse.mockReturnValue({ create: jest.fn().mockResolvedValue({ id: 7 }), saving: false });
});

test('운영 중·종료 과정을 모두 표로 보여 준다', () => {
  renderPage();

  expect(mockUseCourses).toHaveBeenCalledWith(expect.objectContaining({ status: 'ALL', page: 0, size: 10 }));
  const rows = within(screen.getByRole('table', { name: '과정 목록' })).getAllByRole('row');
  expect(rows).toHaveLength(3); // 머리글 + 2
  expect(within(rows[1]).getByText('운영 중')).toBeInTheDocument();
  expect(within(rows[2]).getByText('종료')).toBeInTheDocument();
  expect(screen.getByText('1-2 / 전체 2')).toBeInTheDocument();
});

test('제목을 누르면 관리 상세로 간다', () => {
  renderPage();

  fireEvent.click(screen.getByRole('link', { name: 'Spring Boot 기초' }));

  expect(screen.getByText('관리 상세')).toBeInTheDocument();
});

test('과정을 등록하면 새 과정의 관리 상세로 간다', async () => {
  renderPage();

  fireEvent.click(screen.getByRole('button', { name: '과정 등록' }));
  const dialog = within(screen.getByRole('dialog'));
  fireEvent.change(dialog.getByLabelText(/과정명/), { target: { value: 'Kotlin 입문' } });
  fireEvent.mouseDown(dialog.getByRole('combobox', { name: /분류/ }));
  fireEvent.click(within(screen.getByRole('listbox')).getByRole('option', { name: 'BACKEND' }));
  fireEvent.click(dialog.getByRole('button', { name: /등록|저장/ }));

  await waitFor(() => expect(screen.getByText('관리 상세')).toBeInTheDocument());
});
