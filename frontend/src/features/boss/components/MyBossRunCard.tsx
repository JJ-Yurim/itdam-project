import { Link } from 'react-router-dom';

import { formatDateTime } from '@/shared/datetime';

import { getBossRunStatusPolicy } from '../statusPolicy';
import type { MyBossRunResponse } from '../types';

interface MyBossRunCardProps {
  myBossRun: MyBossRunResponse;
}

// 도서관은 COMPLETED 책만 열람할 수 있으므로 그 외 상태는 안내 문구로 대신한다.
function getBookNotice(myBossRun: MyBossRunResponse): string | null {
  if (myBossRun.bookStatus === 'GENERATING') {
    return '공동 책을 준비하고 있어요.';
  }

  if (myBossRun.bookStatus === 'FAILED') {
    return '공동 책 생성에 실패했어요.';
  }

  if (myBossRun.status === 'ENDED_UNCLEARED') {
    return '클리어하지 못해 공동 책이 만들어지지 않았어요.';
  }

  if (myBossRun.status === 'CLEARED') {
    return '공동 책을 준비하고 있어요.';
  }

  // ACTIVE·SCHEDULED·CLOSED 등은 별도 안내 없이 비워 둔다.
  return null;
}

export function MyBossRunCard({ myBossRun }: MyBossRunCardProps) {
  const statusPolicy = getBossRunStatusPolicy(myBossRun.status);
  const isBookReadable = myBossRun.bookStatus === 'COMPLETED' && myBossRun.bookId !== null;
  const bookNotice = isBookReadable ? null : getBookNotice(myBossRun);

  return (
    <article className="rounded-2xl border border-stone-200 bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-base font-bold text-stone-900">{myBossRun.bossName}</h3>
          <p className="mt-1 text-xs text-stone-500">
            Phase {myBossRun.currentPhaseNo} · 마지막 제출{' '}
            {formatDateTime(myBossRun.lastSubmittedAt)}
          </p>
        </div>

        <span
          className={[
            'shrink-0 rounded-full px-3 py-1 text-xs font-semibold',
            statusPolicy.badgeClassName,
          ].join(' ')}
        >
          {statusPolicy.label}
        </span>
      </div>

      {myBossRun.clearedAt && (
        <p className="mt-3 text-xs text-emerald-700">
          {formatDateTime(myBossRun.clearedAt)} 클리어
        </p>
      )}

      <div className="mt-4 flex flex-wrap items-center gap-2">
        <Link
          to={`/boss/${myBossRun.bossRunId}`}
          className="flex min-h-10 items-center justify-center rounded-xl border border-stone-300 bg-white px-3 text-sm font-semibold text-stone-700 transition hover:bg-stone-50"
        >
          원정 보기
        </Link>

        {isBookReadable && (
          <Link
            to={`/library/books/${myBossRun.bookId}`}
            className="flex min-h-10 items-center justify-center rounded-xl bg-emerald-600 px-3 text-sm font-semibold text-white transition hover:bg-emerald-700"
          >
            📖 공동 책 보기
          </Link>
        )}

        {bookNotice && <span className="text-xs text-stone-500">{bookNotice}</span>}
      </div>
    </article>
  );
}
