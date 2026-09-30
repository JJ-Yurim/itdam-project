import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';

import {
  completeSocialRegistration,
  confirmEmailVerification,
  login,
  logout,
  sendEmailVerification,
  signup,
} from './api';
import { authQueryKeys } from './queryKeys';

export function useSignupMutation() {
  return useMutation({
    mutationFn: signup,
  });
}

export function useSendEmailVerificationMutation() {
  return useMutation({
    mutationFn: sendEmailVerification,
  });
}

export function useConfirmEmailVerificationMutation() {
  return useMutation({
    mutationFn: confirmEmailVerification,
  });
}

/**
 * 로그인 경계에서 캐시를 통째로 비운다.
 *
 * 사용자별 데이터의 쿼리 키에는 사용자 식별자가 없다. 그래서 계정을 바꾸면 이전
 * 사용자의 캐시가 그대로 신선하다고 판정되어 다음 사용자에게 그대로 보인다.
 * 오늘의 단어는 staleTime이 5분이라 그 안에 계정을 바꾸면 반드시 재현되고,
 * 평가 권한·서고 문장·북마크·제출 이력도 같은 구조다.
 *
 * 키마다 사용자 식별자를 넣는 방법도 있지만 사용자별 쿼리가 여러 갈래라 하나만
 * 빠뜨려도 같은 문제가 남는다. 경계에서 한 번 비우는 편이 확실하다.
 */
function useAuthenticatedCacheReset() {
  const queryClient = useQueryClient();

  return (user: Awaited<ReturnType<typeof login>> | null) => {
    queryClient.clear();
    // clear 이후에 심어야 방금 확정된 세션 정보가 함께 지워지지 않는다.
    queryClient.setQueryData(authQueryKeys.currentUser(), user);
  };
}

export function useLoginMutation() {
  const resetCache = useAuthenticatedCacheReset();

  return useMutation({
    mutationFn: login,
    onSuccess: resetCache,
  });
}

export function useCompleteSocialRegistrationMutation() {
  const resetCache = useAuthenticatedCacheReset();

  return useMutation({
    mutationFn: completeSocialRegistration,
    onSuccess: resetCache,
  });
}

export function useLogoutMutation() {
  const resetCache = useAuthenticatedCacheReset();
  const navigate = useNavigate();

  return useMutation({
    mutationFn: logout,
    onSuccess: () => {
      resetCache(null);
      // 회원 전용 화면에서 로그아웃하면 그 자리에서 곧바로 "로그인이 필요해요" 모달을
      // 만난다. 방금 스스로 로그아웃한 사람에게 로그인하라고 안내하는 셈이라, 어디서
      // 로그아웃하든 홈으로 보낸다. 호출부마다 챙기면 빠뜨린다(실제로 한 곳이 빠졌다).
      void navigate('/', { replace: true });
    },
  });
}
