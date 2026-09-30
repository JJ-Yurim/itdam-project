import { Link } from 'react-router-dom';

import { BookReactionControls } from './BookReactionControls';
import { LibraryBookCover } from './LibraryBookCover';

import type { LibraryBookSummaryResponse } from '../types';

interface LibraryBookGridCardProps {
  book: LibraryBookSummaryResponse;
}

export function LibraryBookGridCard({ book }: LibraryBookGridCardProps) {
  return (
    <article className="min-w-0">
      <div className="relative">
        <Link
          to={`/library/books/${book.bookId}`}
          aria-label={`${book.bossName} 표지에서 상세 보기`}
          className="group block rounded-2xl transition-transform hover:-translate-y-0.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600"
        >
          <LibraryBookCover title={book.bossName} imageUrl={book.coverImageUrl} variant="grid" />
        </Link>
        <div className="pointer-events-none absolute inset-x-2 top-2 z-10">
          <BookReactionControls
            bookId={book.bookId}
            likeCount={book.likeCount}
            liked={book.liked}
            bookmarked={book.bookmarked}
            variant="overlay"
          />
        </div>
      </div>
      <Link
        to={`/library/books/${book.bookId}`}
        aria-label={`${book.bossName} 상세 보기`}
        className="group block rounded-lg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600"
      >
        <h3 className="mt-2.5 line-clamp-2 min-h-10 text-sm font-extrabold leading-5 text-stone-900 transition-colors group-hover:text-violet-700">
          {book.bossName}
        </h3>
      </Link>
    </article>
  );
}
