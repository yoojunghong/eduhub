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
  mockUseCourses.mockReset();
  mockUseCourses.mockReturnValue({
    data: {
      content: [
        { id: 1, title: 'Spring Boot 기초', category: '개발', status: 'OPEN', sessionCount: 1 },
        { id: 2, title: '[FR-03 확인] 관리자 흐름', category: '테스트', status: 'CLOSED', sessionCount: 0 },
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

test('처음에는 전체 상태로 불러와 제목·분류·상태 표로 보여 준다', () => {
  renderPage();

  expect(mockUseCourses).toHaveBeenLastCalledWith(
    expect.objectContaining({ keyword: '', status: 'ALL', page: 0, size: 10 }),
  );
  const table = screen.getByRole('table', { name: '과정 목록' });
  expect(within(table).getAllByRole('columnheader').map((h) => h.textContent)).toEqual(['제목', '분류', '상태']);
  const rows = within(table).getAllByRole('row');
  expect(within(rows[1]).getByText('운영 중')).toBeInTheDocument();
  expect(within(rows[2]).getByText('종료')).toBeInTheDocument();
  expect(screen.getByText('페이지당 행 수')).toBeInTheDocument();
});

test('검색어와 상태는 검색 버튼을 눌러야 적용된다', () => {
  renderPage();

  fireEvent.change(screen.getByRole('textbox', { name: '검색어' }), { target: { value: ' 리더십 ' } });
  fireEvent.mouseDown(screen.getByRole('combobox', { name: '상태' }));
  fireEvent.click(within(screen.getByRole('listbox')).getByRole('option', { name: '종료' }));
  expect(mockUseCourses).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '', status: 'ALL' }));

  fireEvent.click(screen.getByRole('button', { name: '검색' }));

  expect(mockUseCourses).toHaveBeenLastCalledWith(
    expect.objectContaining({ keyword: '리더십', status: 'CLOSED', page: 0 }),
  );
});

test('검색어 입력에서 Enter를 눌러도 검색한다', () => {
  renderPage();

  const input = screen.getByRole('textbox', { name: '검색어' });
  fireEvent.change(input, { target: { value: 'SQL' } });
  fireEvent.submit(input.closest('form')!);

  expect(mockUseCourses).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: 'SQL', status: 'ALL' }));
});

test('행을 누르면 관리 상세로 간다', () => {
  renderPage();

  fireEvent.click(screen.getByText('Spring Boot 기초'));

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
