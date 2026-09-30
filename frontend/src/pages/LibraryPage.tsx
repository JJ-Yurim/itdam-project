import { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

import {
  formatWeekLabel,
  useBookContentLikeMutation,
  useWeeklySentenceFeedQuery,
  type WeeklySentenceFeedItem,
} from '@/features/feed';
import { LibraryBookGridCard, useLibraryBooksQuery } from '@/features/library';
import { ErrorState, LoadingState, Pagination, Skeleton, SkeletonSection } from '@/shared/components';

// 'best'는 URL 쿼리에 이미 쓰이는 식별자라 그대로 두고 표시 이름만 바꾼다.
// 좋아요 순위를 다루는 이야기의 전당과 별개인 도서관 주간 선정 탭이다.
type LibraryTab = 'personal' | 'shared' | 'incomplete' | 'best';

const BOOKS_FETCH_SIZE = 100;
const BOOKS_PAGE_SIZE = 4;
const tabs: { value: LibraryTab; label: string }[] = [
  { value: 'personal', label: '개인 책' },
  { value: 'shared', label: '공동 책' },
  { value: 'incomplete', label: '미완성 책' },
  { value: 'best', label: '이주의 문장' },
];

function isLibraryTab(value: string | null): value is LibraryTab {
  return tabs.some((tab) => tab.value === value);
}

function BestSentenceCard({ item }: { item: WeeklySentenceFeedItem }) {
  const likeMutation = useBookContentLikeMutation();

  return (
    <article className="rounded-2xl border-2 border-violet-400 bg-white p-4 shadow-sm">
      <p className="whitespace-pre-wrap break-words text-sm font-semibold leading-6 text-stone-900">
        “{item.content}”
      </p>

      <div className="mt-3 flex min-w-0 items-end justify-between gap-4 border-t border-stone-200 pt-3">
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold text-stone-700">
            {item.authorNickname ?? '잇담 작가'}
          </p>
          <Link
            to={`/library/books/${item.bookId}`}
            className="mt-1 block truncate text-xs text-stone-500 transition hover:text-violet-700"
            title={item.bossName}
          >
            출처 · {item.bossName}
          </Link>
        </div>
        <button
          type="button"
          aria-pressed={item.liked}
          aria-label={
            item.liked
              ? `좋아요 취소, 현재 ${item.likeCount}개`
              : `좋아요, 현재 ${item.likeCount}개`
          }
          disabled={likeMutation.isPending}
          onClick={() =>
            likeMutation.mutate({ bookContentId: item.bookContentId, next: !item.liked })
          }
          className={[
            'inline-flex min-h-8 shrink-0 items-center gap-1 rounded-lg px-1.5 pb-0.5 text-sm font-semibold transition disabled:opacity-50',
            item.liked ? 'text-rose-500' : 'text-stone-400 hover:text-rose-400',
          ].join(' ')}
        >
          <span aria-hidden="true" className="text-xl leading-none">
            {item.liked ? '♥' : '♡'}
          </span>
          {item.likeCount}
        </button>
      </div>
    </article>
  );
}

function LibraryNotice({ title, description }: { title: string; description?: string }) {
  return (
    <section className="mt-8 rounded-2xl border border-violet-100 bg-violet-50/50 px-5 py-7 text-center">
      <p className="font-semibold text-stone-800">{title}</p>
      {description && <p className="mt-2 text-sm text-stone-500">{description}</p>}
    </section>
  );
}

export function LibraryPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const initialTab = searchParams.get('tab');
  const activeTab: LibraryTab = isLibraryTab(initialTab) ? initialTab : 'shared';
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);

  const libraryBooksQuery = useLibraryBooksQuery(
    { page: 0, size: BOOKS_FETCH_SIZE },
    activeTab === 'shared',
  );
  const feedQuery = useWeeklySentenceFeedQuery(activeTab === 'best');

  const filteredBooks = useMemo(() => {
    const normalizedQuery = query.trim().toLocaleLowerCase('ko-KR');
    if (!normalizedQuery || !libraryBooksQuery.data) {
      return libraryBooksQuery.data?.content ?? [];
    }

    return libraryBooksQuery.data.content.filter((book) =>
      book.bossName.toLocaleLowerCase('ko-KR').includes(normalizedQuery),
    );
  }, [libraryBooksQuery.data, query]);

  const totalPages = Math.ceil(filteredBooks.length / BOOKS_PAGE_SIZE);
  const pageBooks = filteredBooks.slice(page * BOOKS_PAGE_SIZE, (page + 1) * BOOKS_PAGE_SIZE);

  const selectTab = (tab: LibraryTab) => {
    setSearchParams(tab === 'shared' ? {} : { tab });
    setPage(0);
    setQuery('');
  };

  return (
    <section>
      <header>
        <h1 className="text-2xl font-extrabold text-violet-800">도서관</h1>
      </header>

      <div
        role="tablist"
        aria-label="도서관 분류"
        className="mt-6 grid grid-cols-4 rounded-xl border border-stone-200 bg-white p-1 text-center text-sm font-medium text-stone-600"
      >
        {tabs.map((tab) => {
          const isSelected = activeTab === tab.value;
          return (
            <button
              key={tab.value}
              type="button"
              role="tab"
              aria-selected={isSelected}
              onClick={() => selectTab(tab.value)}
              className={[
                'min-h-10 rounded-lg px-1 transition-colors focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600',
                isSelected ? 'bg-violet-100 font-bold text-violet-600' : 'hover:bg-stone-50',
              ].join(' ')}
            >
              {tab.label}
            </button>
          );
        })}
      </div>

      {activeTab === 'shared' && (
        <div className="mt-5">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-lg font-extrabold text-stone-900">✨ 공동 책</h2>
            {libraryBooksQuery.isSuccess && (
              <span className="text-xs font-semibold text-stone-500">{filteredBooks.length}권</span>
            )}
          </div>

          <label className="mt-4 flex min-h-12 items-center gap-2 rounded-xl border border-stone-200 bg-white px-4 focus-within:border-violet-400 focus-within:ring-2 focus-within:ring-violet-100">
            <svg
              aria-hidden="true"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              className="size-4 shrink-0 text-stone-400"
            >
              <circle cx="11" cy="11" r="6.5" />
              <path d="m16 16 4.5 4.5" />
            </svg>
            <span className="sr-only">책 제목 검색</span>
            <input
              type="search"
              value={query}
              placeholder="책 제목을 입력하세요"
              onChange={(event) => {
                setQuery(event.target.value);
                setPage(0);
              }}
              className="min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-stone-400"
            />
            {query && (
              <button
                type="button"
                aria-label="검색어 지우기"
                title="검색어 지우기"
                onClick={() => {
                  setQuery('');
                  setPage(0);
                }}
                className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-lg text-stone-400 transition hover:bg-stone-100 hover:text-stone-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600"
              >
                ×
              </button>
            )}
          </label>

          {/* 실제 표지 그리드와 같은 골격을 보여줘 로딩→콘텐츠 전환에서 화면이 점프하지 않는다. */}
          {libraryBooksQuery.isPending && (
            <SkeletonSection label="도서관을 불러오는 중입니다">
              <div className="mt-6 grid grid-cols-2 gap-x-5 gap-y-7 lg:grid-cols-4 lg:gap-x-6 lg:gap-y-8">
                {Array.from({ length: 4 }, (_, index) => (
                  <div key={index}>
                    <Skeleton className="aspect-[3/4] w-full rounded-card" />
                    <Skeleton className="mt-2 h-4 w-3/4" />
                  </div>
                ))}
              </div>
            </SkeletonSection>
          )}
          {libraryBooksQuery.isError && (
            <ErrorState
              title="도서관을 불러오지 못했습니다"
              description="잠시 후 다시 시도해 주세요."
              onRetry={() => void libraryBooksQuery.refetch()}
            />
          )}
          {libraryBooksQuery.isSuccess &&
            (pageBooks.length === 0 ? (
              <LibraryNotice
                title={query.trim() ? '검색 결과가 없습니다' : '아직 완성된 공동 책이 없습니다'}
                description={
                  query.trim()
                    ? '다른 책 제목을 검색해 보세요.'
                    : '원정이 클리어되면 공동 책이 표시됩니다.'
                }
              />
            ) : (
              // 앱 셸은 lg부터 넓어지므로 열 수도 lg에서만 늘린다. md에서 3열로 만들면
              // 컨테이너는 그대로 440px인 채 열만 늘어나 표지가 오히려 작아진다.
              <div className="mt-6 grid grid-cols-2 gap-x-5 gap-y-7 lg:grid-cols-4 lg:gap-x-6 lg:gap-y-8">
                {pageBooks.map((book) => (
                  <LibraryBookGridCard key={book.bookId} book={book} />
                ))}
              </div>
            ))}

          <Pagination
            className="mt-6"
            ariaLabel="공동 책 목록 페이지"
            page={page}
            totalPages={totalPages}
            onPrev={() => setPage((current) => current - 1)}
            onNext={() => setPage((current) => current + 1)}
          />
        </div>
      )}

      {activeTab === 'best' && (
        <div className="mt-5">
          <h2 className="text-lg font-extrabold text-stone-900">✨ 이주의 문장</h2>
          {feedQuery.data && feedQuery.data.items.length > 0 && (
            <p className="mt-1 text-xs text-stone-500">
              {formatWeekLabel(feedQuery.data.weekStartDate)} 문장
            </p>
          )}
          {feedQuery.isPending && (
            <LoadingState
              title="문장을 불러오는 중입니다"
              description="이번 주 문장을 확인하고 있습니다."
            />
          )}
          {feedQuery.isError && (
            <ErrorState
              title="문장을 불러오지 못했습니다"
              description="잠시 후 다시 시도해 주세요."
              onRetry={() => void feedQuery.refetch()}
            />
          )}
          {feedQuery.isSuccess &&
            (feedQuery.data.items.length === 0 ? (
              <LibraryNotice title="아직 모아 볼 문장이 없어요" />
            ) : (
              <div className="mt-4 grid gap-4 lg:grid-cols-2">
                {feedQuery.data.items.map((item) => (
                  <BestSentenceCard key={item.bookContentId} item={item} />
                ))}
              </div>
            ))}
        </div>
      )}

      {(activeTab === 'personal' || activeTab === 'incomplete') && (
        <LibraryNotice
          title={activeTab === 'personal' ? '개인 책은 준비 중이에요' : '미완성 책은 준비 중이에요'}
        />
      )}
    </section>
  );
}
