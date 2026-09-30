import { Link } from 'react-router-dom';

import { useArchiveQuery, useMyContributedBooksQuery, useUnevaluatedArchiveQuery } from '@/features/archive';
import { ProfileAvatar, useCurrentUserQuery, useLogoutMutation } from '@/features/auth';
import { useMyBossRunsQuery } from '@/features/boss';
import { ErrorState, LoadingState } from '@/shared/components';

// 가입일은 와이어프레임처럼 연·월 단위로만 표시한다.
const joinedAtFormatter = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: 'long',
});

const settingsLinkClass =
  'flex min-h-12 w-full items-center justify-between rounded-xl border border-stone-200 bg-white px-4 text-sm font-semibold text-stone-700 transition hover:bg-violet-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600';

function formatCount(count: number | undefined): string {
  return count !== undefined ? String(count) : '-';
}

export function MyPage() {
  const currentUserQuery = useCurrentUserQuery();
  const logoutMutation = useLogoutMutation();

  const isMember = currentUserQuery.data != null;

  // 활동 요약은 별도 집계 API가 없어 각 목록을 최소 크기로 조회해 totalElements만 쓴다.
  const evaluatedQuery = useArchiveQuery(0, 1, isMember);
  const unevaluatedQuery = useUnevaluatedArchiveQuery(0, 1, isMember);
  const bossRunsQuery = useMyBossRunsQuery({ page: 0, size: 1 }, isMember);
  const booksQuery = useMyContributedBooksQuery(0, 1, isMember);

  const sentenceCount =
    evaluatedQuery.data && unevaluatedQuery.data
      ? evaluatedQuery.data.totalElements + unevaluatedQuery.data.totalElements
      : undefined;

  if (currentUserQuery.isPending) {
    return <LoadingState title="내 정보를 불러오고 있어요" description="잠시만 기다려 주세요." />;
  }

  if (currentUserQuery.isError) {
    return (
      <ErrorState
        title="내 정보를 불러오지 못했어요"
        description="잠시 후 다시 시도해 주세요."
        onRetry={() => void currentUserQuery.refetch()}
      />
    );
  }

  if (!currentUserQuery.data) {
    return (
      <section className="py-12 text-center">
        <p className="font-semibold text-stone-700">로그인하면 마이페이지를 볼 수 있어요</p>
        <Link
          to="/login"
          className="mt-4 inline-flex min-h-11 items-center justify-center rounded-xl bg-violet-600 px-4 text-sm font-semibold text-white transition hover:bg-violet-700"
        >
          로그인 하기
        </Link>
      </section>
    );
  }

  const user = currentUserQuery.data;

  return (
    <div className="space-y-8">
      <header>
        <h1 className="text-2xl font-extrabold text-violet-800">마이페이지</h1>
      </header>

      <section
        aria-label="프로필 정보"
        className="flex items-center gap-4 rounded-2xl border border-stone-200 bg-white p-5 shadow-sm"
      >
        <ProfileAvatar nickname={user.nickname} className="h-16 w-16 text-3xl" />

        <div className="min-w-0">
          <p className="text-lg font-bold text-stone-900">{user.nickname}</p>
          <p className="mt-0.5 truncate text-sm text-stone-500">{user.email}</p>
          <p className="mt-0.5 text-xs text-stone-400">
            가입일: {joinedAtFormatter.format(new Date(user.createdAt))}
          </p>
        </div>
      </section>

      <section aria-labelledby="activity-summary-title">
        <h2 id="activity-summary-title" className="mb-3 text-[15px] font-semibold text-stone-900">
          창작 활동 요약
        </h2>

        <dl className="grid grid-cols-3 gap-3">
          <div className="rounded-2xl border border-stone-200 bg-white p-4 text-center shadow-sm">
            <dt className="text-xs text-stone-500">✍️ 문장</dt>
            <dd className="mt-2 text-xl font-extrabold text-stone-900">
              {formatCount(sentenceCount)}
            </dd>
          </div>

          <div className="rounded-2xl border border-stone-200 bg-white p-4 text-center shadow-sm">
            <dt className="text-xs text-stone-500">⚔️ 원정</dt>
            <dd className="mt-2 text-xl font-extrabold text-stone-900">
              {formatCount(bossRunsQuery.data?.totalElements)}
            </dd>
          </div>

          <div className="rounded-2xl border border-stone-200 bg-white p-4 text-center shadow-sm">
            <dt className="text-xs text-stone-500">📖 기여한 책</dt>
            <dd className="mt-2 text-xl font-extrabold text-stone-900">
              {formatCount(booksQuery.data?.totalElements)}
            </dd>
          </div>
        </dl>

        <Link
          to="/archive"
          className="mt-3 inline-block text-sm font-semibold text-violet-700 underline underline-offset-2 transition hover:text-violet-900"
        >
          내 서고에서 자세히 보기
        </Link>
      </section>

      <section aria-labelledby="settings-title" className="space-y-2">
        <h2 id="settings-title" className="mb-3 text-[15px] font-semibold text-stone-900">
          설정
        </h2>

        <Link to="/settings/notifications" className={settingsLinkClass}>
          알림 설정
          <span aria-hidden="true" className="text-stone-400">
            ›
          </span>
        </Link>

        <Link to="/settings/service" className={settingsLinkClass}>
          서비스 설정
          <span aria-hidden="true" className="text-stone-400">
            ›
          </span>
        </Link>
      </section>

      <button
        type="button"
        disabled={logoutMutation.isPending}
        onClick={() =>
          logoutMutation.mutate()
        }
        className="flex min-h-12 w-full items-center justify-center rounded-xl border border-violet-300 bg-white text-sm font-semibold text-violet-700 transition hover:bg-violet-50 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {logoutMutation.isPending ? '로그아웃 중…' : '로그아웃'}
      </button>

      {logoutMutation.isError && (
        <p role="alert" className="text-sm font-semibold text-red-700">
          로그아웃에 실패했어요. 잠시 후 다시 시도해 주세요.
        </p>
      )}
    </div>
  );
}
