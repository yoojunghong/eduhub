import { apiErrorMessage, apiServerMessage } from './apiError';

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

test('대기 인원이 가득 찬 경우(409 WAITLIST_FULL)를 알려 준다', () => {
  expect(apiErrorMessage(axiosError(409, { code: 'WAITLIST_FULL', message: '...' }))).toBe('대기 인원이 가득 찼습니다.');
});

test('본문 없는 404는 과정을 찾을 수 없다고 알려 준다', () => {
  expect(apiErrorMessage(axiosError(404))).toBe('과정을 찾을 수 없습니다.');
});

test('알 수 없는 오류는 기본 문구를 쓴다', () => {
  expect(apiErrorMessage(new Error('Network Error'), '실패')).toBe('실패');
  expect(apiErrorMessage(undefined)).toBe('요청을 처리하지 못했습니다.');
});

test.each([
  ['COMPLETION_NOT_OPEN', '수료 처리는 회차 시작일부터 할 수 있습니다.'],
  ['SESSION_NOT_FOUND', '회차를 찾을 수 없습니다.'],
  ['ENROLLMENT_NOT_FOUND', '신청 내역을 찾을 수 없습니다.'],
  ['ENROLLMENT_NOT_CANCELABLE', '이미 취소되었거나 처리된 신청입니다.'],
  ['CANCEL_PERIOD_ENDED', '취소 기간이 지났습니다. 회차 시작 전날까지 취소할 수 있습니다.'],
])('%s 는 코드 문구를 쓰고 서버 메시지(id 등)를 노출하지 않는다', (code, message) => {
  const error = axiosError(409, { code, message: '회차 id=10 ...' });
  expect(apiErrorMessage(error)).toBe(message);
  expect(apiServerMessage(error)).toBe(message);
});

test('모르는 코드는 서버 메시지, 메시지도 없으면 fallback을 쓴다', () => {
  expect(apiServerMessage(axiosError(409, { code: 'X', message: '서버 안내' }))).toBe('서버 안내');
  expect(apiServerMessage(axiosError(500), '기본')).toBe('기본');
});
