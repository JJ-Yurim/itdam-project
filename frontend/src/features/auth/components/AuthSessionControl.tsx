import { useState } from 'react';
import { Link } from 'react-router-dom';
import { getApiError } from '@/shared/api';
import { useCurrentUserQuery } from '../queries';
import { ProfileAvatar } from './ProfileAvatar';
import { ProfileDrawer } from './ProfileDrawer';

export function AuthSessionControl() {
  const currentUserQuery = useCurrentUserQuery();
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);

  if (currentUserQuery.isPending) {
    return (
      <span className="text-sm text-stone-500" aria-live="polite">
        인증 확인 중...
      </span>
    );
  }

  if (currentUserQuery.isError) {
    const apiError = getApiError(currentUserQuery.error);

    return (
      <div className="flex items-center gap-2">
        <span className="text-sm text-red-600" role="alert">
          {apiError.message || '사용자 확인에 실패했습니다.'}
        </span>

        <button
          type="button"
          className="text-sm font-medium text-stone-600 hover:text-violet-700"
          onClick={() => void currentUserQuery.refetch()}
        >
          다시 시도
        </button>

        <Link to="/login" className="text-sm font-medium text-stone-600 hover:text-violet-700">
          로그인
        </Link>
      </div>
    );
  }
  if (!currentUserQuery.data) {
    return (
      <Link to="/login" className="text-sm font-medium text-stone-600 hover:text-violet-700">
        로그인
      </Link>
    );
  }
  return (
    <>
      <button
        type="button"
        aria-label={`${currentUserQuery.data.nickname} 프로필 메뉴 열기`}
        aria-haspopup="dialog"
        onClick={() => setIsDrawerOpen(true)}
        className="flex min-w-0 items-center gap-2 rounded-full py-1 pl-3 transition hover:bg-violet-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600"
      >
        <span className="max-w-24 truncate text-sm font-semibold text-stone-700">
          {currentUserQuery.data.nickname}
        </span>
        <ProfileAvatar nickname={currentUserQuery.data.nickname} className="h-9 w-9 text-base" />
      </button>

      {isDrawerOpen && (
        <ProfileDrawer user={currentUserQuery.data} onClose={() => setIsDrawerOpen(false)} />
      )}
    </>
  );
}
