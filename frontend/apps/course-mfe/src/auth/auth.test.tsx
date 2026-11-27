import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { AuthProvider } from './AuthContext';
import { RequireAuth } from './RequireAuth';
import { LoginPage } from './LoginPage';
import { authApi } from '../api/clients';
import { tokenStore } from '../api/tokenStore';
import { UNAUTHORIZED_EVENT } from '../api/unauthorized';
import { routerFuture } from '../routerFuture';

jest.mock('../api/clients', () => ({ authApi: { me: jest.fn(), login: jest.fn() } }));
declare const process: { env: Record<string, string | undefined> };
const me = authApi.me as jest.Mock;
const login = authApi.login as jest.Mock;

function Where() {
  const { pathname, state } = useLocation();
  return (
    <p data-testid="where">
      {pathname}|{(state as { from?: { pathname: string } } | null)?.from?.pathname ?? ''}
    </p>
  );
}

const renderAt = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]} future={routerFuture}>
      <AuthProvider>
        <Where />
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<p>목록 화면</p>} />
          <Route
            path="/my/enrollments"
            element={
              <RequireAuth>
                <p>내 신청 화면</p>
              </RequireAuth>
            }
          />
          <Route
            path="/admin"
            element={
              <RequireAuth admin>
                <p>관리 화면</p>
              </RequireAuth>
            }
          />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );

const employee = { employeeNo: 'E1001', name: '홍길동', role: 'EMPLOYEE' };
const fireUnauthorized = () => act(() => void window.dispatchEvent(new Event(UNAUTHORIZED_EVENT)));

beforeEach(() => {
  localStorage.clear();
  me.mockReset();
  login.mockReset();
});

describe('RequireAuth', () => {
  test('비로그인이면 /login 으로 보내고 원래 경로를 보관한다', async () => {
    renderAt('/my/enrollments');

    expect(await screen.findByRole('heading', { name: '로그인' })).toBeInTheDocument();
    expect(screen.getByTestId('where')).toHaveTextContent('/login|/my/enrollments');
  });

  test('새로고침 시 토큰이 있으면 /me 로 확인하고 화면을 보여 준다', async () => {
    tokenStore.setToken('tok');
    me.mockResolvedValue({ data: employee });

    renderAt('/my/enrollments');

    expect(await screen.findByText('내 신청 화면')).toBeInTheDocument();
    expect(me).toHaveBeenCalledTimes(1);
  });

  test('/me 가 401이면 비로그인으로 /login 에 간다', async () => {
    tokenStore.setToken('tok');
    me.mockImplementation(async () => {
      tokenStore.clear(); // 실제로는 인터셉터가 지운다
      throw { response: { status: 401 } };
    });

    renderAt('/my/enrollments');

    expect(await screen.findByRole('heading', { name: '로그인' })).toBeInTheDocument();
  });

  test('사원이 관리자 화면을 열면 권한 없음 안내, 로그아웃되지 않는다', async () => {
    tokenStore.setToken('tok');
    me.mockResolvedValue({ data: employee });

    renderAt('/admin');

    expect(await screen.findByText(/권한이 없습니다/)).toBeInTheDocument();
    expect(screen.queryByText('관리 화면')).not.toBeInTheDocument();
    expect(tokenStore.getToken()).toBe('tok');
    expect(screen.getByTestId('where')).toHaveTextContent('/admin|');
  });

  test('관리자는 관리자 화면을 본다', async () => {
    tokenStore.setToken('tok');
    me.mockResolvedValue({ data: { ...employee, role: 'ADMIN' } });

    renderAt('/admin');

    expect(await screen.findByText('관리 화면')).toBeInTheDocument();
  });
});

describe('401 이벤트', () => {
  test('보호 화면에서 받으면 /login 으로 보내고 만료 안내, 두 번 와도 원래 경로를 유지한다', async () => {
    tokenStore.setToken('tok');
    me.mockResolvedValue({ data: employee });
    renderAt('/my/enrollments');
    await screen.findByText('내 신청 화면');

    fireUnauthorized();
    fireUnauthorized();

    expect(await screen.findByText('다시 로그인해 주세요.')).toBeInTheDocument();
    expect(screen.getByTestId('where')).toHaveTextContent('/login|/my/enrollments');
  });

  test('공개 화면에서 받으면 그 자리에 머문다', async () => {
    tokenStore.setToken('tok');
    me.mockResolvedValue({ data: employee });
    renderAt('/');
    await screen.findByText('목록 화면');

    fireUnauthorized();

    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/|'));
    expect(screen.getByText('목록 화면')).toBeInTheDocument();
  });
});

describe('LoginPage', () => {
  const submit = (no: string, pw: string) => {
    fireEvent.change(screen.getByLabelText(/사번/), { target: { value: no } });
    fireEvent.change(screen.getByLabelText(/비밀번호/), { target: { value: pw } });
    fireEvent.click(screen.getByRole('button', { name: '로그인' }));
  };

  test('성공하면 토큰을 저장하고 원래 가려던 곳으로 간다', async () => {
    login.mockResolvedValue({ data: { accessToken: 'new-tok', user: employee } });
    renderAt('/my/enrollments'); // 비로그인 → /login(from=/my/enrollments)
    await screen.findByRole('heading', { name: '로그인' });

    submit('E1001', 'dev-password');

    expect(await screen.findByText('내 신청 화면')).toBeInTheDocument();
    expect(login).toHaveBeenCalledWith({ loginRequest: { employeeNo: 'E1001', password: 'dev-password' } });
    expect(tokenStore.getToken()).toBe('new-tok');
  });

  test('원래 경로가 없으면 / 로 간다', async () => {
    login.mockResolvedValue({ data: { accessToken: 'new-tok', user: employee } });
    renderAt('/login');

    submit('E1001', 'dev-password');

    expect(await screen.findByText('목록 화면')).toBeInTheDocument();
  });

  test('실패하면 안내 문구를 보여 주고 로그인 화면에 머문다', async () => {
    login.mockRejectedValue({ response: { status: 401 } });
    renderAt('/login');

    submit('E1001', 'wrong');

    expect(await screen.findByText('사번 또는 비밀번호가 올바르지 않습니다.')).toBeInTheDocument();
    expect(tokenStore.getToken()).toBeNull();
  });

  describe('개발용 계정 안내', () => {
    const original = process.env.NODE_ENV;
    afterEach(() => {
      process.env.NODE_ENV = original;
    });

    test('개발 모드에서만 보인다', () => {
      renderAt('/login');
      expect(screen.queryByText(/개발용 계정/)).not.toBeInTheDocument();

      process.env.NODE_ENV = 'development';
      renderAt('/login');
      expect(screen.getByText(/개발용 계정: E1001·E1002·A0001/)).toBeInTheDocument();
    });
  });
});
