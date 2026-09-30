import { getApiError } from '@/shared/api';

const messages: Record<string, string> = {
  VALIDATION_ERROR: '입력값을 다시 확인해 주세요.',
  CONFLICT: '현재 상태에서는 요청을 처리할 수 없습니다.',
  FORBIDDEN: '관리자 권한이 필요합니다.',
  NOT_FOUND: '대상을 찾을 수 없습니다.',
};

export function getRaidAdminErrorMessage(error: unknown, fallback: string) {
  const apiError = getApiError(error);
  return messages[apiError.code] ?? fallback;
}

/**
 * 동시 수정 충돌 판정. 관리 패널마다 "최신 데이터 다시 불러오기" 복구 경로를 같은 조건으로
 * 노출해야 하므로 한곳에 둔다.
 */
export function isConflictError(error: unknown) {
  return (
    typeof error === 'object' &&
    error !== null &&
    'response' in error &&
    (error as { response?: { status?: number } }).response?.status === 409
  );
}
