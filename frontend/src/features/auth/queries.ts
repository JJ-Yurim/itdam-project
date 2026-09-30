import { useQuery } from '@tanstack/react-query';

import { getCurrentCopyrightAgreement, getCurrentUser, getSocialRegistrationPending } from './api';
import { authQueryKeys } from './queryKeys';

export function useCurrentUserQuery() {
  return useQuery({
    queryKey: authQueryKeys.currentUser(),
    queryFn: getCurrentUser,
    retry: false,
  });
}

export function useCurrentCopyrightAgreementQuery() {
  return useQuery({
    queryKey: authQueryKeys.currentCopyrightAgreement(),
    queryFn: getCurrentCopyrightAgreement,
    retry: false,
  });
}

export function useSocialRegistrationPendingQuery() {
  return useQuery({
    queryKey: authQueryKeys.socialRegistrationPending(),
    queryFn: getSocialRegistrationPending,
    retry: false,
  });
}
