import axios from 'axios';

export interface FieldError {
  field: string;
  reason: string;
}

export interface ApiError {
  occurredAt: string;
  code: string;
  message: string;
  fieldErrors: FieldError[];
}

export function getApiError(error: unknown): ApiError {
  if (axios.isAxiosError<ApiError>(error) && error.response?.data) {
    return error.response.data;
  }

  return {
    occurredAt: new Date().toISOString(),
    code: 'UNKNOWN_ERROR',
    message: '알 수 없는 오류가 발생했습니다.',
    fieldErrors: [],
  };
}

/** 응답 상태 코드. 네트워크 실패처럼 응답 자체가 없으면 undefined다. */
export function getResponseStatus(error: unknown): number | undefined {
  return axios.isAxiosError(error) ? error.response?.status : undefined;
}

/**
 * 재시도해도 결과가 달라지지 않는 오류인지 본다.
 *
 * 404·403 같은 4xx는 같은 요청을 다시 보내도 같은 답이 온다. 재시도하면 사용자만
 * 두 배로 기다리고 서버는 쓸모없는 요청을 두 번 받는다.
 */
export function isClientError(error: unknown): boolean {
  const status = getResponseStatus(error);
  return status !== undefined && status >= 400 && status < 500;
}

/** 대상이 없거나 접근할 수 없어서 이 화면에 머무를 이유가 없는 상태. */
export function isMissingResourceError(error: unknown): boolean {
  const status = getResponseStatus(error);
  return status === 404 || status === 403;
}
