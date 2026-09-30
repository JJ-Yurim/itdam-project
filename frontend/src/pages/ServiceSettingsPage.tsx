import { Link } from 'react-router-dom';

import { useCurrentUserQuery } from '@/features/auth';
import { ErrorState, LoadingState } from '@/shared/components';

const menuLinkClass =
  'flex min-h-12 w-full items-center justify-between rounded-xl border border-stone-200 bg-white px-4 text-sm font-semibold text-stone-700 transition hover:bg-violet-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600';

// 비밀번호 변경·공개 범위·차단 사용자 등 와이어프레임의 나머지 항목은
// 관련 API가 생기면 메뉴로 추가한다.
export function ServiceSettingsPage() {
  const currentUserQuery = useCurrentUserQuery();

  return (
    <section className="space-y-6">
      <header>
        <Link
          to="/my-page"
          className="text-sm font-semibold text-stone-500 transition hover:text-stone-800"
        >
          ‹ 마이페이지
        </Link>
        <h1 className="mt-2 text-2xl font-extrabold text-stone-900">서비스 설정</h1>
      </header>

      <section aria-labelledby="account-info-title">
        <h2 id="account-info-title" className="mb-3 text-[15px] font-semibold text-stone-900">
          계정 정보
        </h2>

        {currentUserQuery.isPending && (
          <LoadingState title="계정 정보를 불러오고 있어요" description="잠시만 기다려 주세요." />
        )}

        {currentUserQuery.isError && (
          <ErrorState
            title="계정 정보를 불러오지 못했어요"
            description="잠시 후 다시 시도해 주세요."
            onRetry={() => void currentUserQuery.refetch()}
          />
        )}

        {currentUserQuery.isSuccess && !currentUserQuery.data && (
          <div className="rounded-xl border border-stone-200 bg-white p-5 text-center">
            <p className="text-sm font-semibold text-stone-700">
              로그인하면 계정 정보를 볼 수 있어요
            </p>
            <Link
              to="/login"
              className="mt-3 inline-flex min-h-11 items-center justify-center rounded-xl bg-violet-600 px-4 text-sm font-semibold text-white transition hover:bg-violet-700"
            >
              로그인 하기
            </Link>
          </div>
        )}

        {currentUserQuery.isSuccess && currentUserQuery.data && (
          <dl className="space-y-2 rounded-xl border border-stone-200 bg-white p-5">
            <div className="flex items-center justify-between gap-3">
              <dt className="text-sm text-stone-500">닉네임</dt>
              <dd className="text-sm font-semibold text-stone-900">
                {currentUserQuery.data.nickname}
              </dd>
            </div>

            <div className="flex items-center justify-between gap-3">
              <dt className="text-sm text-stone-500">이메일</dt>
              <dd className="truncate text-sm font-semibold text-stone-900">
                {currentUserQuery.data.email}
              </dd>
            </div>
          </dl>
        )}
      </section>

      <section aria-labelledby="policy-title" className="space-y-2">
        <h2 id="policy-title" className="mb-3 text-[15px] font-semibold text-stone-900">
          약관 및 정책
        </h2>

        <Link to="/terms" className={menuLinkClass}>
          이용약관
          <span aria-hidden="true" className="text-stone-400">
            ›
          </span>
        </Link>

        <Link to="/privacy" className={menuLinkClass}>
          개인정보처리방침
          <span aria-hidden="true" className="text-stone-400">
            ›
          </span>
        </Link>
      </section>
    </section>
  );
}
