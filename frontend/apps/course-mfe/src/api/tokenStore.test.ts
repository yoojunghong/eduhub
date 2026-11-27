import { tokenStore } from './tokenStore';

beforeEach(() => localStorage.clear());

test('토큰과 사용자를 저장하고 읽고 지운다', () => {
  expect(tokenStore.getToken()).toBeNull();
  expect(tokenStore.getUser()).toBeNull();

  tokenStore.setToken('abc');
  tokenStore.setUser({ employeeNo: 'E1001', name: '홍길동', role: 'EMPLOYEE' });

  expect(localStorage.getItem('eduhub.accessToken')).toBe('abc');
  expect(tokenStore.getToken()).toBe('abc');
  expect(tokenStore.getUser()).toEqual({ employeeNo: 'E1001', name: '홍길동', role: 'EMPLOYEE' });

  tokenStore.clear();
  expect(tokenStore.getToken()).toBeNull();
  expect(tokenStore.getUser()).toBeNull();
});

test('사용자 JSON이 깨져 있으면 null', () => {
  localStorage.setItem('eduhub.user', '{broken');
  expect(tokenStore.getUser()).toBeNull();
});
