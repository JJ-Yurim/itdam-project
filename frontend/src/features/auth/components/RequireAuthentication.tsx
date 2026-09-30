import type { ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import { ErrorState, LoadingState } from '@/shared/components';

import { LoginRequiredModal } from './LoginRequiredModal';
import { useCurrentUserQuery } from '../queries';

export function RequireAuthentication({ children }: { children: ReactNode }) {
  const currentUserQuery = useCurrentUserQuery();
  const location = useLocation();
  const navigate = useNavigate();

  if (currentUserQuery.isPending) {
    return <LoadingState title="로그인 정보를 확인하고 있어요" />;
  }

  if (currentUserQuery.isError) {
    return (
      <ErrorState
        title="로그인 정보를 확인하지 못했어요"
        description="잠시 후 다시 시도해 주세요."
        onRetry={() => void currentUserQuery.refetch()}
      />
    );
  }

  if (!currentUserQuery.data) {
    return (
      <LoginRequiredModal
        open
        returnTo={`${location.pathname}${location.search}`}
        onClose={() => void navigate('/', { replace: true })}
      />
    );
  }

  return children;
}
