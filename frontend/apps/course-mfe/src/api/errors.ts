/**
 * 생성 클라이언트(Axios)가 던진 오류가 HTTP 404인지 확인한다.
 * axios를 직접 import하지 않으려고 응답 상태 코드만 본다.
 */
export function isNotFound(error: unknown): boolean {
  if (typeof error !== 'object' || error === null) return false;
  const response = (error as { response?: { status?: unknown } }).response;
  return response?.status === 404;
}
