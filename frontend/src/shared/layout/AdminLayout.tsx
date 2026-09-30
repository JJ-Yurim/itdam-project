import { Link, Navigate, Outlet } from 'react-router-dom';

import { AuthSessionControl, useCurrentUserQuery } from '@/features/auth';
import { ErrorState, LoadingState } from '@/shared/components';

/**
 * 관리자 전용 셸. 운영자는 표를 나란히 비교하며 일하므로 모바일 앱 프레임(최대 480px)과
 * 하단 네비게이션을 쓰지 않고 넓은 폭과 서비스 복귀 링크만 제공한다.
 * 권한 판정을 셸에서 하면 하위 관리 화면마다 같은 게이트를 반복하지 않아도 된다.
 */
export function AdminLayout() {
  const currentUserQuery = useCurrentUserQuery();

  if (currentUserQuery.isPending) {
    return (
      <div className="min-h-dvh bg-stone-50">
        <LoadingState title="관리자 권한을 확인하고 있습니다" />
      </div>
    );
  }

  if (currentUserQuery.isError) {
    return (
      <div className="min-h-dvh bg-stone-50">
        <ErrorState
          title="관리자 권한을 확인하지 못했습니다"
          description="잠시 후 다시 시도해 주세요."
          onRetry={() => void currentUserQuery.refetch()}
        />
      </div>
    );
  }

  if (!currentUserQuery.data) {
    return <Navigate to="/login" replace />;
  }

  if (currentUserQuery.data.role !== 'ADMIN') {
    return <Navigate to="/" replace />;
  }

  return (
    <div className="min-h-dvh bg-stone-50">
      <header className="sticky top-0 z-30 border-b border-stone-200 bg-white">
        <div className="mx-auto flex w-full max-w-[1440px] flex-wrap items-center justify-between gap-3 px-4 py-3 md:px-6">
          {/* 콘솔 이름은 브랜드 표기이고 h1은 각 관리 화면이 가진다(문서마다 h1 하나). */}
          <div className="flex min-w-0 items-center gap-3">
            <img src="/homepage_logo.png" alt="" aria-hidden="true" className="h-7 w-auto" />
            <p className="truncate text-lg font-extrabold text-violet-800">잇담 운영 콘솔</p>
          </div>

          <div className="flex items-center gap-3">
            <Link
              to="/"
              className="rounded-full border border-stone-200 px-3 py-1.5 text-sm font-medium text-stone-600 transition hover:bg-stone-50 hover:text-violet-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600"
            >
              서비스로 돌아가기
            </Link>
            <AuthSessionControl />
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-[1440px] px-4 py-6 md:px-6 md:py-8">
        <Outlet />
      </main>
    </div>
  );
}
