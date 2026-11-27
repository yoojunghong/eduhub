import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import type { CurrentUser } from '@eduhub/api-client';
import { authApi } from '../api/clients';
import { tokenStore } from '../api/tokenStore';
import { UNAUTHORIZED_EVENT } from '../api/unauthorized';

interface AuthValue {
  user: CurrentUser | null;
  /** 시작할 때 저장된 토큰을 서버에 확인하는 중 */
  initializing: boolean;
  /** 토큰이 만료되어 로그인 화면으로 보내졌다 */
  expired: boolean;
  /** 공개 화면에서 만료되면 올라간다. 화면을 이 값으로 다시 마운트하면 토큰 없이 다시 불러온다 */
  epoch: number;
  login: (employeeNo: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthValue | undefined>(undefined);

/** 로그인해야 볼 수 있는 경로 */
export function isProtectedPath(pathname: string) {
  return pathname === '/my' || pathname.startsWith('/my/') || pathname === '/admin' || pathname.startsWith('/admin/');
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [initializing, setInitializing] = useState(() => tokenStore.getToken() !== null);
  const [expired, setExpired] = useState(false);
  const [epoch, setEpoch] = useState(0);
  const location = useLocation();
  const navigate = useNavigate();

  const locationRef = useRef(location);
  locationRef.current = location;
  // 직전까지 로그인 상태였는가. 만료 이벤트는 한 번의 만료에 한 번만 처리한다(다음 로그인 전까지 추가 이벤트 무시).
  const sessionActive = useRef(tokenStore.getToken() !== null);

  // 로그인 화면을 벗어나면 만료 안내를 끈다
  useEffect(() => {
    if (location.pathname !== '/login') setExpired(false);
  }, [location.pathname]);

  // 새로고침 시 토큰이 있으면 /me 로 확인한다. 401이면 인터셉터가 토큰을 지우고 비로그인이 된다
  useEffect(() => {
    if (tokenStore.getToken() === null) return;
    let ignore = false;
    authApi
      .me()
      .then((response) => {
        if (ignore) return;
        tokenStore.setUser(response.data);
        setUser(response.data);
      })
      .catch(() => {
        // 401이면 인터셉터가 토큰을 지웠다. 네트워크 오류·5xx면 토큰은 두고 user만 비운다
        if (!ignore) setUser(null);
      })
      .finally(() => {
        if (!ignore) setInitializing(false);
      });
    return () => {
      ignore = true;
    };
  }, []);

  useEffect(() => {
    const onUnauthorized = () => {
      setUser(null);
      if (!sessionActive.current) return; // 로그인 상태가 아니었거나 이미 처리한 만료: 원래 경로 유지, 재마운트 없음
      sessionActive.current = false;
      const current = locationRef.current;
      if (current.pathname === '/login') return;
      if (isProtectedPath(current.pathname)) {
        setExpired(true);
        navigate('/login', { state: { from: current, expired: true }, replace: true });
      } else {
        setEpoch((n) => n + 1); // 공개 화면: 그대로 두고 다시 불러오게 한다
      }
    };
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
  }, [navigate]);

  const login = useCallback(async (employeeNo: string, password: string) => {
    const response = await authApi.login({ loginRequest: { employeeNo, password } });
    const { accessToken, user: me } = response.data;
    if (!accessToken || !me) throw new Error('로그인 응답이 올바르지 않습니다.');
    tokenStore.setToken(accessToken);
    tokenStore.setUser(me);
    setUser(me);
    sessionActive.current = true;
    setExpired(false);
    setInitializing(false);
  }, []);

  const logout = useCallback(() => {
    sessionActive.current = false;
    tokenStore.clear();
    setUser(null);
    setExpired(false);
  }, []);

  const value = useMemo(
    () => ({ user, initializing, expired, epoch, login, logout }),
    [user, initializing, expired, epoch, login, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error('AuthProvider 안에서만 useAuth를 쓸 수 있습니다.');
  return value;
}

export const isAdmin = (user: CurrentUser | null) => user?.role === 'ADMIN';
