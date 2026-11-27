import axios from 'axios';
import { tokenStore } from './tokenStore';

export const UNAUTHORIZED_EVENT = 'eduhub:unauthorized';
const LOGIN_PATH = '/api/v1/auth/login';

let interceptorId: number | undefined;

/**
 * 생성 클라이언트가 쓰는 기본 axios에 응답 인터셉터를 한 번만 등록한다.
 * 401이면 토큰·사용자를 지우고 이벤트를 보낸다. 로그인 요청의 401(틀린 비밀번호)과 403(권한 없음)은 건드리지 않는다.
 */
export function registerUnauthorizedInterceptor() {
  if (interceptorId !== undefined) return;
  interceptorId = axios.interceptors.response.use(undefined, (error: unknown) => {
    const config = (error as { config?: { url?: string } }).config;
    const status = (error as { response?: { status?: number } }).response?.status;
    if (status === 401 && !config?.url?.includes(LOGIN_PATH)) {
      tokenStore.clear();
      window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
    }
    return Promise.reject(error);
  });
}
