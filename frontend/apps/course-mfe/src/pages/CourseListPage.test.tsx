import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { routerFuture } from '../routerFuture';
import { useCourses } from '../hooks/useCourses';
import { CourseListPage } from './CourseListPage';

// 화면 테스트는 훅을 mock한다. API 호출 방식은 useCourses.test.ts가 따로 검증한다
jest.mock('../hooks/useCourses');
const mockUseCourses = useCourses as jest.Mock;

const renderPage = () =>
  render(
    <MemoryRouter future={routerFuture}>
      <CourseListPage />
    </MemoryRouter>,
  );

test('과정 목록을 표로 보여 준다', () => {
  mockUseCourses.mockReturnValue({
    data: {
      content: [{ id: 1, title: 'Spring Boot 입문', category: 'BACKEND', status: 'OPEN', sessionCount: 2 }],
      page: 0,
      size: 10,
      totalElements: 1,
    },
    loading: false,
    error: undefined,
  });

  renderPage();

  expect(screen.getByRole('link', { name: 'Spring Boot 입문' })).toHaveAttribute('href', '/courses/1');
  expect(screen.getByRole('cell', { name: 'BACKEND' })).toBeInTheDocument();
});

test('불러오기에 실패하면 에러 메시지를 보여 준다', () => {
  mockUseCourses.mockReturnValue({ data: undefined, loading: false, error: new Error('fail') });

  renderPage();

  expect(screen.getByRole('alert')).toHaveTextContent('과정 목록을 불러오지 못했습니다.');
});
