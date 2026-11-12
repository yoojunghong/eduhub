import { apiErrorMessage } from './apiError';

const axiosError = (status: number, data?: unknown) => Object.assign(new Error('Request failed'), { response: { status, data } });

test('종료된 과정(409 COURSE_CLOSED)이면 수정할 수 없다고 알려 준다', () => {
  expect(apiErrorMessage(axiosError(409, { code: 'COURSE_CLOSED', message: '...' }))).toBe(
    '종료된 과정은 수정하거나 회차를 추가할 수 없습니다.',
  );
});

test('입력값 오류(400)는 서버 메시지를 함께 보여 준다', () => {
  expect(apiErrorMessage(axiosError(400, { code: 'INVALID_REQUEST', message: 'capacity: 1 이상이어야 합니다' }))).toBe(
    '입력값을 확인해 주세요. (capacity: 1 이상이어야 합니다)',
  );
});

test('본문 없는 404는 과정을 찾을 수 없다고 알려 준다', () => {
  expect(apiErrorMessage(axiosError(404))).toBe('과정을 찾을 수 없습니다.');
});

test('알 수 없는 오류는 기본 문구를 쓴다', () => {
  expect(apiErrorMessage(new Error('Network Error'), '실패')).toBe('실패');
  expect(apiErrorMessage(undefined)).toBe('요청을 처리하지 못했습니다.');
});
