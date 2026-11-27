import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { authApi, courseApi } from './clients';
import { tokenStore } from './tokenStore';
import { registerUnauthorizedInterceptor, UNAUTHORIZED_EVENT } from './unauthorized';

let status = 200;
let lastConfig: InternalAxiosRequestConfig | undefined;
const originalAdapter = axios.defaults.adapter;

beforeAll(() => {
  registerUnauthorizedInterceptor();
  registerUnauthorizedInterceptor(); // 중복 등록해도 한 번만
  axios.defaults.adapter = async (config) => {
    lastConfig = config;
    const response = { data: {}, status, statusText: '', headers: {}, config };
    if (status >= 400) throw new AxiosError('fail', String(status), config, null, response);
    return response;
  };
});
afterAll(() => {
  axios.defaults.adapter = originalAdapter;
});

let events: number;
const onEvent = () => events++;
beforeEach(() => {
  localStorage.clear();
  status = 200;
  events = 0;
  window.addEventListener(UNAUTHORIZED_EVENT, onEvent);
});
afterEach(() => window.removeEventListener(UNAUTHORIZED_EVENT, onEvent));

test('토큰이 있으면 Authorization 헤더가 붙는다', async () => {
  tokenStore.setToken('tok-1');
  await authApi.me();
  expect(lastConfig?.headers.get('Authorization')).toBe('Bearer tok-1');
});

test('401이면 토큰·사용자를 지우고 이벤트를 한 번 보낸다', async () => {
  tokenStore.setToken('tok-1');
  tokenStore.setUser({ employeeNo: 'E1001' });
  status = 401;

  await expect(courseApi.getCourse({ id: 1 })).rejects.toBeDefined();

  expect(tokenStore.getToken()).toBeNull();
  expect(tokenStore.getUser()).toBeNull();
  expect(events).toBe(1);
});

test('로그인 요청의 401은 이벤트도 삭제도 없다', async () => {
  tokenStore.setToken('tok-1');
  status = 401;

  await expect(authApi.login({ loginRequest: { employeeNo: 'E1001', password: 'x' } })).rejects.toBeDefined();

  expect(events).toBe(0);
  expect(tokenStore.getToken()).toBe('tok-1');
});

test('403은 토큰을 유지하고 이벤트도 없다', async () => {
  tokenStore.setToken('tok-1');
  status = 403;

  await expect(authApi.me()).rejects.toBeDefined();

  expect(events).toBe(0);
  expect(tokenStore.getToken()).toBe('tok-1');
});
