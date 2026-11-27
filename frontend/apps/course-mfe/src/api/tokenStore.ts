import type { CurrentUser } from '@eduhub/api-client';

const TOKEN_KEY = 'eduhub.accessToken';
const USER_KEY = 'eduhub.user';

/** 로그인 토큰과 사용자 정보를 localStorage에 보관한다. */
export const tokenStore = {
  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  },
  setToken(token: string) {
    localStorage.setItem(TOKEN_KEY, token);
  },
  getUser(): CurrentUser | null {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as CurrentUser;
    } catch {
      return null; // 깨진 값은 없는 것으로 본다
    }
  },
  setUser(user: CurrentUser) {
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  },
  clear() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  },
};
