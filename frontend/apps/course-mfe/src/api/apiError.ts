import type { ErrorResponse } from '@eduhub/api-client';

/**
 * 생성 클라이언트(Axios)가 던진 오류에서 HTTP 상태와 ErrorResponse(code, message)를 꺼낸다.
 * axios를 직접 import하지 않으려고 response 모양만 본다.
 */
function readError(error: unknown): { status?: number; body?: ErrorResponse } {
  if (typeof error !== 'object' || error === null) return {};
  const response = (error as { response?: { status?: number; data?: ErrorResponse } }).response;
  return { status: response?.status, body: response?.data };
}

/** 관리 화면에서 보여 줄 오류 문구. 백엔드 ErrorResponse.code로 분기한다. */
export function apiErrorMessage(error: unknown, fallback = '요청을 처리하지 못했습니다.'): string {
  const { status, body } = readError(error);
  switch (body?.code) {
    case 'COURSE_CLOSED':
      return '종료된 과정은 수정하거나 회차를 추가할 수 없습니다.';
    case 'COURSE_NOT_FOUND':
      return '과정을 찾을 수 없습니다.';
    case 'INVALID_REQUEST':
      return `입력값을 확인해 주세요. (${body.message ?? ''})`;
  }
  if (status === 404) return '과정을 찾을 수 없습니다.';
  return fallback;
}
