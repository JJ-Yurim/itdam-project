import { Link } from 'react-router-dom';

import { ErrorState, LoadingState } from '@/shared/components';

import { useBookGenerationStatusQuery } from '../queries';

import type { BookGenerationStatusResponse } from '../types';

interface BookGenerationStatusPanelProps {
  bossRunId: number;
}

function getStatusCopy(status: BookGenerationStatusResponse) {
  if (status.bookStatus === 'COMPLETED') {
    return {
      badge: '책 완성',
      title: '모두의 문장이 한 권의 책이 되었어요',
      description: '완성된 이야기는 도서관에서 언제든 다시 펼쳐볼 수 있어요.',
      badgeClassName: 'bg-emerald-100 text-emerald-700',
    };
  }

  if (status.bookStatus === 'FAILED' || status.jobStatus === 'FAILED') {
    return {
      badge: '잠시 멈춤',
      title: '이야기를 엮는 마법이 잠시 멈췄어요',
      description: '문장들은 안전하게 보관되어 있어요. 잠시 후 다시 확인해 주세요.',
      badgeClassName: 'bg-red-100 text-red-700',
    };
  }

  if (status.jobStatus === 'PENDING') {
    return {
      badge: '문장 모으는 중',
      title: '모두의 문장을 한데 모으고 있어요',
      description: '원정에서 탄생한 문장들을 차곡차곡 모아 이야기 만들 준비를 하고 있어요.',
      badgeClassName: 'bg-violet-100 text-violet-700',
    };
  }

  if (status.jobStatus === 'RETRYING') {
    return {
      badge: '다시 엮는 중',
      title: '흩어진 문장을 다시 모으고 있어요',
      description: '이야기가 자연스럽게 이어지도록 문장들을 다시 차분히 엮고 있어요.',
      badgeClassName: 'bg-amber-100 text-amber-700',
    };
  }

  if (status.jobStatus === 'SUCCESS') {
    return {
      badge: '마무리 중',
      title: '완성된 이야기를 책에 담고 있어요',
      description: '마지막 장까지 예쁘게 다듬은 뒤 도서관에 책을 꽂아둘게요.',
      badgeClassName: 'bg-violet-100 text-violet-700',
    };
  }

  return {
    badge: '이야기 엮는 중',
    title: '이야기 마법사가 모두의 문장을 엮고 있어요',
    description: '모험가들이 남긴 문장을 자연스럽게 이어 하나의 이야기로 만들고 있어요.',
    badgeClassName: 'bg-violet-100 text-violet-700',
  };
}

interface BookGenerationStatusContentProps {
  status: BookGenerationStatusResponse;
  onOpenBook?: () => void;
  openBookLabel?: string;
}

export function BookGenerationStatusContent({
  status,
  onOpenBook,
  openBookLabel = '도서관에서 보기',
}: BookGenerationStatusContentProps) {
  const copy = getStatusCopy(status);
  const isCompleted = status.bookStatus === 'COMPLETED';
  const isFailed = status.bookStatus === 'FAILED' || status.jobStatus === 'FAILED';

  return (
    <section
      aria-labelledby="book-generation-status-title"
      className="rounded-2xl border border-stone-200 bg-white p-5 shadow-sm"
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-violet-700">공동 책 생성</p>
          <h2
            id="book-generation-status-title"
            className="mt-1 text-xl font-extrabold text-stone-900"
          >
            {copy.title}
          </h2>
        </div>

        <span
          className={`shrink-0 rounded-full px-3 py-1 text-xs font-semibold ${copy.badgeClassName}`}
        >
          {copy.badge}
        </span>
      </div>

      <p className="mt-3 text-sm leading-6 text-stone-600">{copy.description}</p>

      {isCompleted &&
        (onOpenBook ? (
          <button
            type="button"
            onClick={onOpenBook}
            className="mt-5 flex min-h-11 w-full items-center justify-center rounded-lg bg-violet-600 px-4 py-2 font-semibold text-white transition-colors hover:bg-violet-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600"
          >
            {openBookLabel}
          </button>
        ) : (
          <Link
            to={`/library/books/${status.bookId}`}
            className="mt-5 flex min-h-11 items-center justify-center rounded-lg bg-violet-600 px-4 py-2 font-semibold text-white transition-colors hover:bg-violet-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600"
          >
            {openBookLabel}
          </Link>
        ))}

      {isFailed && (
        <p role="alert" className="mt-5 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
          이야기가 다시 이어지지 않으면 관리자에게 알려 주세요.
        </p>
      )}
    </section>
  );
}

export function BookGenerationStatusPanel({ bossRunId }: BookGenerationStatusPanelProps) {
  const statusQuery = useBookGenerationStatusQuery(bossRunId);

  if (statusQuery.isPending) {
    return (
      <LoadingState
        title="공동 책 생성 상태를 확인하고 있어요"
        description="보스 클리어 이후 책 생성 작업을 조회하는 중입니다."
      />
    );
  }

  if (statusQuery.isError) {
    return (
      <ErrorState
        title="공동 책 생성 상태를 불러오지 못했어요"
        description="잠시 후 다시 시도해 주세요."
        onRetry={() => void statusQuery.refetch()}
      />
    );
  }

  return <BookGenerationStatusContent status={statusQuery.data} />;
}
