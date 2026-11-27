import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { useCourse } from './hooks/useCourse';
import { App } from './App';
import { routerFuture } from './routerFuture';
import { AuthProvider } from './auth/AuthContext';

jest.mock('./hooks/useCourse');
jest.mock('./hooks/useCourses');
jest.mock('./api/clients', () => ({ authApi: { me: jest.fn(), login: jest.fn() } }));
const mockUseCourse = useCourse as jest.Mock;

const renderAt = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]} future={routerFuture}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );

beforeEach(() => mockUseCourse.mockReset());

test('/courses/:id 는 그 id로 상세 화면을 연다', () => {
  mockUseCourse.mockReturnValue({
    data: { id: 3, title: 'React 실전', category: 'FRONTEND', status: 'OPEN', sessions: [] },
    loading: false,
    error: undefined,
    notFound: false,
  });

  renderAt('/courses/3');

  expect(mockUseCourse).toHaveBeenCalledWith(3);
  expect(screen.getByRole('heading', { name: 'React 실전' })).toBeInTheDocument();
});

test('id가 숫자가 아니면 API를 부르지 않고 안내한다', () => {
  renderAt('/courses/abc');

  expect(mockUseCourse).not.toHaveBeenCalled();
  expect(screen.getByRole('alert')).toHaveTextContent('과정을 찾을 수 없습니다.');
});
