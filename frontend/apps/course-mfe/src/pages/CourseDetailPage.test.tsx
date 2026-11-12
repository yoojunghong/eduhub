import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { routerFuture } from '../routerFuture';
import { useCourse } from '../hooks/useCourse';
import { CourseDetailPage } from './CourseDetailPage';

// 화면 테스트는 훅을 mock한다. API 호출 방식은 useCourse.test.ts가 따로 검증한다
jest.mock('../hooks/useCourse');
const mockUseCourse = useCourse as jest.Mock;

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
    course([{ id: 10, startDate: '2026-12-01', place: '본관 301호', capacity: 20, remainingSeats: 5, status: 'OPEN' }]),
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
    course([{ id: 10, startDate: '2026-12-01', place: '본관 301호', capacity: 20, remainingSeats: 0, status: 'OPEN' }]),
  );

  renderPage();

  expect(screen.getByText('마감')).toBeInTheDocument();
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
