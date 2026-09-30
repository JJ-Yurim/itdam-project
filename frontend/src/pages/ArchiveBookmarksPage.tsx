import { useState } from 'react';

import { Link } from 'react-router-dom';

import { LibraryBookCard, useMyBookmarksQuery } from '@/features/library';
import {
  ButtonLink,
  EmptyState,
  ErrorState,
  Pagination,
  Skeleton,
  SkeletonSection,
} from '@/shared/components';

const PAGE_SIZE = 10;

export function ArchiveBookmarksPage() {
  const [page, setPage] = useState(0);

  const bookmarksQuery = useMyBookmarksQuery({ page, size: PAGE_SIZE });

  return (
    <section className="space-y-6">
      <header>
        <Link
          to="/archive"
          className="text-sm font-semibold text-stone-500 transition hover:text-stone-800"
        >
          ‹ 내 서고
        </Link>
        <h1 className="mt-2 text-2xl font-extrabold text-stone-900">북마크</h1>
        <p className="mt-2 text-sm text-stone-600">북마크해 둔 책을 모아볼 수 있어요.</p>
      </header>

      {/* 책 카드 목록과 같은 골격을 보여준다. */}
      {bookmarksQuery.isPending && (
        <SkeletonSection label="북마크한 책을 불러오고 있어요">
          <div className="space-y-4">
            {Array.from({ length: 3 }, (_, index) => (
              <div key={index} className="rounded-card border border-stone-200 bg-white p-5">
                <div className="flex gap-4">
                  <Skeleton className="h-24 w-18 shrink-0 rounded-control" />
                  <div className="min-w-0 flex-1">
                    <Skeleton className="h-5 w-1/2" />
                    <Skeleton className="mt-2 h-4 w-1/3" />
                  </div>
                </div>
              </div>
            ))}
          </div>
        </SkeletonSection>
      )}

      {bookmarksQuery.isError && (
        <ErrorState
          title="북마크 목록을 불러오지 못했어요"
          description="잠시 후 다시 시도해 주세요."
          onRetry={() => void bookmarksQuery.refetch()}
        />
      )}

      {bookmarksQuery.isSuccess && (
        <>
          {bookmarksQuery.data.content.length === 0 ? (
            <EmptyState
              title="아직 북마크한 책이 없어요"
              description="도서관에서 마음에 드는 책을 북마크하면 이곳에 모여요."
              icon={
                <svg
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.8"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  className="size-6"
                >
                  <path d="M6 3.75h12a1 1 0 0 1 1 1v16l-7-4-7 4v-16a1 1 0 0 1 1-1Z" />
                </svg>
              }
              action={
                <ButtonLink to="/library" variant="secondary" className="text-sm">
                  도서관 둘러보기
                </ButtonLink>
              }
            />
          ) : (
            <>
              <p className="text-sm font-semibold text-stone-700">
                총 {bookmarksQuery.data.totalElements}권
              </p>

              <div className="space-y-4">
                {bookmarksQuery.data.content.map((book) => (
                  <LibraryBookCard key={book.bookId} book={book} />
                ))}
              </div>
            </>
          )}

          <Pagination
            ariaLabel="북마크 목록 페이지"
            page={bookmarksQuery.data.page}
            totalPages={bookmarksQuery.data.totalPages}
            hasNext={bookmarksQuery.data.hasNext}
            onPrev={() => setPage((current) => current - 1)}
            onNext={() => setPage((current) => current + 1)}
          />
        </>
      )}
    </section>
  );
}
