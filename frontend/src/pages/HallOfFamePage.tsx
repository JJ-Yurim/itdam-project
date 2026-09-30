import { Link } from 'react-router-dom';

import {
  BEST_SENTENCE_LIMIT,
  formatWeekLabel,
  rankBestSentences,
  useWeeklySentenceFeedQuery,
} from '@/features/feed';
import { EmptyState, ErrorState, LoadingState } from '@/shared/components';

import type { RankedSentence } from '@/features/feed';

/** 상위 3위는 하단 내비게이션의 선택 상태와 같은 보라 톤으로 강조한다. */
const PODIUM_RANK_LIMIT = 3;

const podiumStyle = {
  card: 'border-violet-200 bg-violet-50',
  badge: 'bg-violet-100 text-violet-800',
  rank: 'text-violet-800',
};

const defaultStyle = {
  card: 'border-stone-200 bg-white',
  badge: 'bg-stone-100 text-stone-600',
  rank: 'text-stone-400',
};

function BestSentenceCard({ rank, item }: RankedSentence) {
  const isPodium = rank <= PODIUM_RANK_LIMIT;
  const style = isPodium ? podiumStyle : defaultStyle;

  return (
    <article className={['flex gap-3 rounded-2xl border p-4 shadow-sm', style.card].join(' ')}>
      {/* 순위는 오른쪽 뱃지가 글로 전달하므로 큰 숫자는 장식으로 둔다. */}
      <span
        aria-hidden="true"
        className={['w-5 shrink-0 text-lg font-extrabold', style.rank].join(' ')}
      >
        {rank}
      </span>

      <div className="min-w-0 flex-1">
        <p className="whitespace-pre-wrap break-words text-base font-bold leading-7 text-stone-900">
          “{item.content}”
        </p>

        <p className="mt-2 text-xs text-stone-500">
          {item.authorNickname ?? '탈퇴한 작가'} · {item.bossName}
        </p>

        <Link
          to={`/library/books/${item.bookId}`}
          className="mt-2 inline-block text-sm font-semibold text-violet-700 underline underline-offset-2 transition hover:text-violet-900"
        >
          책 보러 가기
        </Link>
      </div>

      <div className="flex shrink-0 flex-col items-end gap-1.5">
        <span
          className={['rounded-full px-2 py-0.5 text-[11px] font-semibold', style.badge].join(' ')}
        >
          {isPodium && <span aria-hidden="true">🏆 </span>}주간 {rank}위
        </span>

        {/* 좋아요는 이 화면에서 누를 수 없고 개수만 보여준다. */}
        <p className="flex items-center gap-1 text-sm font-semibold text-rose-600">
          <span aria-hidden="true">♥</span>
          <span className="sr-only">좋아요</span>
          {item.likeCount}
        </p>
      </div>
    </article>
  );
}

export function HallOfFamePage() {
  const feedQuery = useWeeklySentenceFeedQuery();
  const rankedSentences = feedQuery.data ? rankBestSentences(feedQuery.data.items) : [];

  return (
    <section className="space-y-6">
      <header>
        <h1 className="text-2xl font-extrabold text-violet-800">이야기의 전당</h1>
        <p className="mt-2 text-sm text-stone-600">
          {feedQuery.data
            ? `${formatWeekLabel(feedQuery.data.weekStartDate)} 좋아요를 가장 많이 받은 문장 ${BEST_SENTENCE_LIMIT}개예요.`
            : '이번 주 좋아요를 가장 많이 받은 문장을 모아 보여드려요.'}
        </p>
      </header>

      {feedQuery.isPending && (
        <LoadingState
          title="이야기의 전당을 불러오고 있어요"
          description="이번 주의 문장을 모으는 중입니다."
        />
      )}

      {feedQuery.isError && (
        <ErrorState
          title="이야기의 전당을 불러오지 못했어요"
          description="잠시 후 다시 시도해 주세요."
          onRetry={() => void feedQuery.refetch()}
        />
      )}

      {feedQuery.isSuccess &&
        (rankedSentences.length === 0 ? (
          <EmptyState
            title="이번 주 전당에 오른 문장이 아직 없어요"
            description="문장이 선정되면 이곳에 전시됩니다."
          />
        ) : (
          <ol aria-label="이번 주 전당에 오른 문장" className="space-y-4">
            {rankedSentences.map((ranked) => (
              <li key={ranked.item.bookContentId}>
                <BestSentenceCard {...ranked} />
              </li>
            ))}
          </ol>
        ))}
    </section>
  );
}
