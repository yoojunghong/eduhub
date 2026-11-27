import axios from 'axios';
import { tokenStore } from './tokenStore';

export const UNAUTHORIZED_EVENT = 'eduhub:unauthorized';
const LOGIN_PATH = '/api/v1/auth/login';

let registered = false;

/** 로그인 요청인지: URL 경로(쿼리 제외)가 로그인 경로로 끝나는지 본다. */
function isLoginRequest(url: string | undefined) {
  return (url ?? '').split('?')[0].endsWith(LOGIN_PATH);
}

/**
 * 생성 클라이언트가 쓰는 기본 axios에 인터셉터를 한 번만 등록한다 (중복 등록 방지).
 * - 요청: 토큰이 없어 Authorization이 'Bearer '(빈 토큰)이면 헤더를 뺀다.
 * - 응답: 401이면 토큰·사용자를 지우고 이벤트를 보낸다. 다만 그 요청이 보낸 토큰이 지금 저장된 토큰과 같을 때만
 *   (그 사이 재로그인했다면 옛 요청의 401이 새 토큰을 지우지 않게). 로그인 요청의 401과 403은 건드리지 않는다.
 */
export function registerUnauthorizedInterceptor() {
  if (registered) return;
  registered = true;

  axios.interceptors.request.use((config) => {
    const auth = config.headers?.get?.('Authorization');
    if (typeof auth === 'string' && auth.trim() === 'Bearer') config.headers.delete('Authorization');
    return config;
  });

  axios.interceptors.response.use(undefined, (error: unknown) => {
    const config = (error as { config?: { url?: string; headers?: { get?: (name: string) => unknown } } }).config;
    const status = (error as { response?: { status?: number } }).response?.status;
    if (status === 401 && !isLoginRequest(config?.url)) {
      const sent = config?.headers?.get?.('Authorization') ?? null;
      const token = tokenStore.getToken();
      const current = token ? `Bearer ${token}` : null;
      if (sent === current) {
        tokenStore.clear();
        window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
      }
    }
    return Promise.reject(error);
  });
}
