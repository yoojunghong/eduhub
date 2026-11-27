import { useEffect } from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Layout } from './Layout';
import { AuthProvider } from './auth/AuthContext';
import { authApi } from './api/clients';
import { tokenStore } from './api/tokenStore';
import { routerFuture } from './routerFuture';
import { UNAUTHORIZED_EVENT } from './api/unauthorized';

jest.mock('./api/clients', () => ({ authApi: { me: jest.fn(), login: jest.fn() } }));
const me = authApi.me as jest.Mock;

const renderAt = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]} future={routerFuture}>
      <AuthProvider>
        <Layout>
          <p>본문</p>
        </Layout>
      </AuthProvider>
    </MemoryRouter>,
  );

const loginAs = (user: { name: string; employeeNo: string; role: string }) => {
  tokenStore.setToken('tok');
  me.mockResolvedValue({ data: user });
};

beforeEach(() => {
  localStorage.clear();
  me.mockReset();
});

test('비로그인: 교육과정 탭과 로그인 버튼만 보인다', () => {
  renderAt('/');

  expect(screen.getAllByRole('tab').map((t) => t.textContent)).toEqual(['교육과정']);
  expect(screen.getByRole('link', { name: '로그인' })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '로그아웃' })).not.toBeInTheDocument();
  expect(screen.getByText('본문')).toBeInTheDocument();
});

test('사원: 내 신청 내역 탭, 이름·사번·역할, 로그아웃. 과정 관리 탭은 없다', async () => {
  loginAs({ name: '홍길동', employeeNo: 'E1001', role: 'EMPLOYEE' });
  renderAt('/my/enrollments');

  expect(await screen.findByText('홍길동 (E1001) · 사원')).toBeInTheDocument();
  expect(screen.getAllByRole('tab').map((t) => t.textContent)).toEqual(['교육과정', '내 신청 내역']);
  expect(screen.getByRole('tab', { name: '내 신청 내역' })).toHaveAttribute('aria-selected', 'true');
  expect(screen.queryByRole('link', { name: '로그인' })).not.toBeInTheDocument();
});

test('관리자: 과정 관리 탭이 보이고 /admin 에서 선택된다', async () => {
  loginAs({ name: '김관리', employeeNo: 'A0001', role: 'ADMIN' });
  renderAt('/admin/courses/3');

  expect(await screen.findByText('김관리 (A0001) · 관리자')).toBeInTheDocument();
  expect(screen.getAllByRole('tab').map((t) => t.textContent)).toEqual(['교육과정', '내 신청 내역', '과정 관리']);
  expect(screen.getByRole('tab', { name: '과정 관리' })).toHaveAttribute('aria-selected', 'true');
});

test.each([
  ['/', '교육과정'],
  ['/courses/3', '교육과정'],
])('%s 에서는 %s 탭이 선택된다', (path, tab) => {
  renderAt(path);

  expect(screen.getByRole('tab', { name: tab })).toHaveAttribute('aria-selected', 'true');
});

test('로그아웃하면 토큰이 지워지고 비로그인 상단 바가 된다', async () => {
  loginAs({ name: '홍길동', employeeNo: 'E1001', role: 'EMPLOYEE' });
  renderAt('/');

  fireEvent.click(await screen.findByRole('button', { name: '로그아웃' }));

  expect(tokenStore.getToken()).toBeNull();
  expect(screen.getByRole('link', { name: '로그인' })).toBeInTheDocument();
});

describe('공개 화면에서 토큰이 만료되면', () => {
  let mounts: number;
  function Probe() {
    useEffect(() => {
      mounts++;
    }, []);
    return <p>공개 화면</p>;
  }
  const renderProbe = () =>
    render(
      <MemoryRouter initialEntries={['/']} future={routerFuture}>
        <AuthProvider>
          <Layout>
            <Probe />
          </Layout>
        </AuthProvider>
      </MemoryRouter>,
    );
  const expire = () => act(() => void window.dispatchEvent(new Event(UNAUTHORIZED_EVENT)));

  beforeEach(() => {
    mounts = 0;
  });

  test('화면을 한 번만 다시 마운트한다 (두 번째 401은 무시)', async () => {
    loginAs({ name: '홍길동', employeeNo: 'E1001', role: 'EMPLOYEE' });
    renderProbe();
    await screen.findByText('홍길동 (E1001) · 사원');
    expect(mounts).toBe(1);

    expire();
    expect(mounts).toBe(2);
    expire();
    expect(mounts).toBe(2);
  });

  test('로그인 상태가 아니었다면 다시 마운트하지 않는다', () => {
    renderProbe();

    expire();

    expect(mounts).toBe(1);
  });
});
