import { Link } from 'react-router-dom';

import { useBookBookmarkMutation, useBookLikeMutation } from '../queries';

import { LibraryBookCover } from './LibraryBookCover';

import type { LibraryBookSummaryResponse } from '../types';

interface LibraryBookCardProps {
  book: LibraryBookSummaryResponse;
}

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
});

export function LibraryBookCard({ book }: LibraryBookCardProps) {
  const likeMutation = useBookLikeMutation();
  const bookmarkMutation = useBookBookmarkMutation();

  return (
    <article className="overflow-hidden rounded-2xl border border-stone-200 bg-white shadow-sm">
      <div className="flex gap-4 p-4">
        <LibraryBookCover title={book.bossName} imageUrl={book.coverImageUrl} />

        <div className="min-w-0 flex-1">
          <p className="text-xs font-semibold text-violet-700">공동 책</p>

          <h2 className="mt-1 line-clamp-2 text-lg font-extrabold text-stone-900">
            {book.bossName}
          </h2>

          <dl className="mt-3 grid grid-cols-2 gap-2 text-sm">
            <div className="rounded-xl bg-stone-50 px-3 py-2">
              <dt className="text-xs text-stone-500">수록 문장</dt>
              <dd className="mt-1 font-bold text-stone-900">{book.contentCount}개</dd>
            </div>

            <div className="rounded-xl bg-stone-50 px-3 py-2">
              <dt className="text-xs text-stone-500">완성일</dt>
              <dd className="mt-1 font-bold text-stone-900">
                {dateFormatter.format(new Date(book.completedAt))}
              </dd>
            </div>
          </dl>
        </div>
      </div>

      <div className="flex items-center gap-2 border-t border-stone-100 px-4 py-2">
        <button
          type="button"
          aria-pressed={book.liked}
          aria-label={
            book.liked
              ? `좋아요 취소, 현재 ${book.likeCount}개`
              : `좋아요, 현재 ${book.likeCount}개`
          }
          disabled={likeMutation.isPending}
          onClick={() => likeMutation.mutate({ bookId: book.bookId, next: !book.liked })}
          className={[
            'flex min-h-10 items-center gap-1.5 rounded-xl px-3 text-sm font-semibold transition disabled:cursor-not-allowed disabled:opacity-50',
            book.liked ? 'bg-rose-50 text-rose-600' : 'text-stone-500 hover:bg-stone-50',
          ].join(' ')}
        >
          <span aria-hidden="true">{book.liked ? '❤️' : '🤍'}</span>
          {book.likeCount}
        </button>

        <button
          type="button"
          aria-pressed={book.bookmarked}
          aria-label={book.bookmarked ? '북마크 해제' : '북마크'}
          disabled={bookmarkMutation.isPending}
          onClick={() => bookmarkMutation.mutate({ bookId: book.bookId, next: !book.bookmarked })}
          className={[
            'ml-auto flex min-h-10 items-center gap-1.5 rounded-xl px-3 text-sm font-semibold transition disabled:cursor-not-allowed disabled:opacity-50',
            book.bookmarked ? 'bg-violet-50 text-violet-700' : 'text-stone-500 hover:bg-stone-50',
          ].join(' ')}
        >
          <span aria-hidden="true">🔖</span>
          {book.bookmarked ? '북마크됨' : '북마크'}
        </button>
      </div>

      <Link
        to={`/library/books/${book.bookId}`}
        className="flex min-h-11 items-center justify-center border-t border-stone-200 px-4 py-2 text-sm font-bold text-violet-700 transition-colors hover:bg-violet-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600"
      >
        책 자세히 보기
      </Link>
    </article>
  );
}
