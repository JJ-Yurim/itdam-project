import {
  getBookReactionErrorMessage,
  useBookBookmarkMutation,
  useBookLikeMutation,
} from '../queries';
import { useAuthGate } from '@/features/auth';
import { useToast } from '@/shared/components';

interface BookReactionControlsProps {
  bookId: number;
  likeCount: number;
  liked: boolean;
  bookmarked: boolean;
  variant?: 'compact' | 'full' | 'overlay';
}

function HeartIcon({ filled, small = false }: { filled: boolean; small?: boolean }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      className={small ? 'h-4 w-4' : 'h-[18px] w-[18px]'}
      fill={filled ? 'currentColor' : 'none'}
    >
      <path
        d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78L12 21.23l8.84-8.84a5.5 5.5 0 0 0 0-7.78Z"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function BookmarkIcon({ filled, small = false }: { filled: boolean; small?: boolean }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      className={small ? 'h-4 w-4' : 'h-5 w-5'}
      fill={filled ? 'currentColor' : 'none'}
    >
      <path
        d="M6 3.75h12a1 1 0 0 1 1 1v16l-7-4-7 4v-16a1 1 0 0 1 1-1Z"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function BookReactionControls({
  bookId,
  likeCount,
  liked,
  bookmarked,
  variant = 'compact',
}: BookReactionControlsProps) {
  const likeMutation = useBookLikeMutation();
  const bookmarkMutation = useBookBookmarkMutation();
  const { isUnauthenticated, isAuthenticationReady, requestLogin } = useAuthGate();
  const { showToast } = useToast();
  const isFull = variant === 'full';
  const isOverlay = variant === 'overlay';
  const failureMessage = likeMutation.isError
    ? getBookReactionErrorMessage(likeMutation.error, '좋아요를 반영하지 못했어요.')
    : bookmarkMutation.isError
      ? getBookReactionErrorMessage(bookmarkMutation.error, '북마크를 반영하지 못했어요.')
      : null;

  return (
    <div
      className={[
        'flex flex-wrap items-center gap-2',
        isFull ? 'justify-center' : '',
        isOverlay ? 'pointer-events-none justify-between' : '',
      ].join(' ')}
    >
      <button
        type="button"
        aria-pressed={liked}
        aria-label={liked ? `좋아요 취소, 현재 ${likeCount}개` : `좋아요, 현재 ${likeCount}개`}
        disabled={!isAuthenticationReady || likeMutation.isPending}
        onClick={() => {
          if (isUnauthenticated) {
            requestLogin();
            return;
          }
          // 반대편의 지난 실패 안내가 남아 있으면 이번 동작의 결과처럼 읽힌다.
          bookmarkMutation.reset();
          likeMutation.mutate({ bookId, next: !liked });
        }}
        className={[
          'inline-flex items-center justify-center font-semibold transition disabled:cursor-not-allowed disabled:opacity-50',
          isOverlay ? 'pointer-events-auto' : '',
          isOverlay
            ? 'min-h-8 gap-1 rounded-lg px-2 text-xs'
            : 'min-h-10 gap-1.5 rounded-xl px-2.5 text-sm',
          isOverlay
            ? liked
              ? 'bg-black/35 text-white'
              : 'bg-black/20 text-white/65 hover:bg-black/30 hover:text-white'
            : liked
              ? 'bg-rose-50 text-rose-600'
              : 'text-stone-500 hover:bg-stone-50 hover:text-rose-500',
          isFull ? 'flex-1' : '',
        ].join(' ')}
      >
        <HeartIcon filled={liked} small={isOverlay} />
        <span>{likeCount}</span>
      </button>

      <button
        type="button"
        aria-pressed={bookmarked}
        aria-label={bookmarked ? '북마크 해제' : '북마크 추가'}
        disabled={!isAuthenticationReady || bookmarkMutation.isPending}
        onClick={() => {
          if (isUnauthenticated) {
            requestLogin();
            return;
          }
          likeMutation.reset();
          bookmarkMutation.mutate(
            { bookId, next: !bookmarked },
            {
              // 담긴 곳이 이 화면에 보이지 않으므로 어디서 다시 찾을 수 있는지 알려 준다.
              onSuccess: (_, { next }) =>
                showToast(
                  next
                    ? { message: '북마크에 담았어요. 내 서고에서 볼 수 있어요.', variant: 'success' }
                    : { message: '북마크를 해제했어요.' },
                ),
            },
          );
        }}
        className={[
          'inline-flex items-center justify-center font-semibold transition disabled:cursor-not-allowed disabled:opacity-50',
          isOverlay ? 'pointer-events-auto' : '',
          isOverlay
            ? 'min-h-8 gap-1 rounded-lg px-2 text-xs'
            : 'min-h-10 gap-1.5 rounded-xl px-2.5 text-sm',
          isOverlay
            ? bookmarked
              ? 'bg-black/35 text-white'
              : 'bg-black/20 text-white/65 hover:bg-black/30 hover:text-white'
            : bookmarked
              ? 'bg-violet-50 text-violet-700'
              : 'text-stone-500 hover:bg-stone-50 hover:text-violet-600',
          isFull ? 'flex-1' : isOverlay ? '' : 'ml-auto',
        ].join(' ')}
      >
        <BookmarkIcon filled={bookmarked} small={isOverlay} />
      </button>

      {failureMessage && (
        // 낙관적 갱신이 없어 실패하면 화면이 그대로다. 안내가 없으면 눌렀는데 아무 일도
        // 일어나지 않은 상태로 남아 서버 문제인지 버튼 고장인지 구분할 수 없다.
        // 표지 위에 겹치는 overlay 변형에서는 자리가 없어 생략한다.
        !isOverlay && (
          <p role="alert" className="w-full text-xs font-semibold text-red-600">
            {failureMessage}
          </p>
        )
      )}
    </div>
  );
}
