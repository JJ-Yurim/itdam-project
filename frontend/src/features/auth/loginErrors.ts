import { getApiError } from '@/shared/api';

const SOCIAL_LOGIN_ERROR_MESSAGES: Record<string, string> = {
  social_email_not_provided: '소셜 계정에서 이메일 정보를 제공받지 못했습니다.',
  social_email_already_registered: '이미 다른 로그인 방식으로 가입된 이메일입니다.',
  social_account_inactive: '현재 이용할 수 없는 계정입니다.',
  social_profile_incomplete: '소셜 계정 정보를 확인하지 못했습니다.',
  social_signup_expired: '가입 정보가 만료되었습니다. 소셜 로그인을 다시 시작해 주세요.',
  social_login_failed: '소셜 로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.',
};

export function getLoginErrorMessage(error: unknown) {
  const apiError = getApiError(error);

  switch (apiError.code) {
    case 'UNAUTHORIZED':
      return '이메일 또는 비밀번호가 일치하지 않습니다.';
    case 'VALIDATION_ERROR':
    case 'INVALID_REQUEST':
      return '입력한 이메일과 비밀번호를 확인해 주세요.';
    case 'UNKNOWN_ERROR':
      return '로그인 처리 중 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.';
    default:
      return '로그인에 실패했습니다. 입력 정보를 확인해 주세요.';
  }
}

export function getSocialLoginErrorMessage(code: string) {
  if (Object.hasOwn(SOCIAL_LOGIN_ERROR_MESSAGES, code)) {
    return SOCIAL_LOGIN_ERROR_MESSAGES[code];
  }

  return '소셜 로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.';
}
