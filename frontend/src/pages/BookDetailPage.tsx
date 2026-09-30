import { Link, useParams } from 'react-router-dom';

import { AuthRequiredLink } from '@/features/auth';
import {
  BookContributorList,
  BookReactionControls,
  LibraryBookCover,
  useLibraryBookQuery,
  type BookContributorResponse,
  type BookContentResponse,
} from '@/features/library';
import { isMissingResourceError } from '@/shared/api';
import { ErrorState, LoadingState } from '@/shared/components';

const completedDateFormatter = new Intl.DateTimeFormat('ko-KR', {
  month: 'short',
  day: 'numeric',
});

function sortBookContents(contents: BookContentResponse[]) {
  return [...contents].sort((left, right) => left.sequenceNo - right.sequenceNo);
}

function getContributors(contents: BookContentResponse[]) {
  const contributors = new Map<string, BookContributorResponse>();

  contents.forEach(({ contributor }) => {
    if (contributor) {
      const contributorKey =
        contributor.userId === null ? `nickname:${contributor.nickname}` : `user:${contributor.userId}`;
      contributors.set(contributorKey, contributor);
    }
  });

  return [...contributors.values()];
}

function getBookIntroduction(body: string | null, contents: BookContentResponse[]) {
  const firstParagraph = body?.split(/\n\s*\n/u).find((paragraph) => paragraph.trim());
  return firstParagraph?.trim() ?? contents[0]?.content ?? '작품 소개가 준비 중입니다.';
}

export function BookDetailPage() {
  const { bookId: bookIdParam } = useParams();
  const bookId = Number(bookIdParam);
  const libraryBookQuery = useLibraryBookQuery(bookId);

  if (!Number.isInteger(bookId) || bookId <= 0) {
    return (
      <ErrorState
        title="잘못된 공동 책 주소입니다"
        description="도서관 목록에서 다시 선택해 주세요."
      />
    );
  }

  if (libraryBookQuery.isPending) {
    return (
      <LoadingState
        title="공동 책을 불러오는 중입니다"
        description="책 정보와 수록된 문장을 확인하고 있습니다."
      />
    );
  }

  if (libraryBookQuery.isError) {
    return (
      <ErrorState
        title="공동 책 상세를 불러오지 못했습니다"
        description={
          isMissingResourceError(libraryBookQuery.error)
            ? '요청하신 대상을 찾을 수 없습니다.'
            : '잠시 후 다시 시도해 주세요.'
        }
        // 404·403이면 다시 시도해도 같은 결과라 재시도 대신 돌아갈 곳을 준다.
        onRetry={
          isMissingResourceError(libraryBookQuery.error)
            ? undefined
            : () => void libraryBookQuery.refetch()
        }
        backTo={
          isMissingResourceError(libraryBookQuery.error)
            ? { to: '/library', label: '도서관으로' }
            : undefined
        }
      />
    );
  }

  const book = libraryBookQuery.data;
  const contents = sortBookContents(book.contents);
  const contributors = getContributors(contents);
  const introduction = getBookIntroduction(book.body, contents);

  return (
    <article className="mx-auto max-w-5xl pb-6">
      <Link
        to="/library"
        className="inline-flex min-h-10 items-center gap-2 rounded-lg text-sm font-bold text-stone-700 transition hover:text-violet-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600"
      >
        <span aria-hidden="true">←</span>
        공동 도서 정보
      </Link>

      <header className="mt-5 text-center lg:mt-8">
        <div className="flex justify-center">
          <LibraryBookCover title={book.bossName} imageUrl={book.coverImageUrl} size="large" />
        </div>

        <div className="mt-5 flex flex-wrap justify-center gap-2">
          <span className="rounded-full bg-rose-50 px-3 py-1 text-xs font-bold text-rose-600">
            공동 책
          </span>
          <span className="rounded-full bg-emerald-50 px-3 py-1 text-xs font-bold text-emerald-700">
            최종 클리어
          </span>
        </div>
        <h1 className="mt-2 text-2xl font-extrabold text-stone-900">{book.bossName}</h1>
      </header>

      <AuthRequiredLink
        to={`/library/books/${book.bookId}/read`}
        className="mt-6 flex min-h-14 w-full items-center justify-center rounded-2xl bg-violet-500 px-4 py-3 font-bold text-white shadow-lg shadow-violet-100 transition hover:bg-violet-600 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600"
      >
        <span aria-hidden="true">📖</span>
        <span className="ml-2">공동 소설집 전체 읽기</span>
      </AuthRequiredLink>

      <div className="mt-5">
        <BookReactionControls
          bookId={book.bookId}
          likeCount={book.likeCount}
          liked={book.liked}
          bookmarked={book.bookmarked}
          variant="full"
        />
      </div>

      <dl className="mt-7 grid grid-cols-3 divide-x divide-stone-200 rounded-2xl border border-stone-200 bg-white px-2 py-4 text-center shadow-sm">
        <div className="px-1">
          <dt className="text-xs text-stone-500">참여 작가</dt>
          <dd className="mt-1 font-extrabold text-stone-900">{contributors.length}명</dd>
        </div>
        <div className="px-1">
          <dt className="text-xs text-stone-500">기여 문장</dt>
          <dd className="mt-1 font-extrabold text-stone-900">{contents.length}개</dd>
        </div>
        <div className="px-1">
          <dt className="text-xs text-stone-500">완성일</dt>
          <dd className="mt-1 font-extrabold text-violet-700">
            {completedDateFormatter.format(new Date(book.completedAt))}
          </dd>
        </div>
      </dl>

      <div className="mt-6 grid gap-6 lg:grid-cols-[minmax(0,1.4fr)_minmax(280px,0.6fr)]">
        <section className="rounded-2xl border border-stone-200 bg-white p-5 lg:p-7">
          <h2 className="font-extrabold text-stone-900">작품 소개</h2>
          <p className="mt-3 line-clamp-4 whitespace-pre-wrap text-sm leading-7 text-stone-600">
            {introduction}
          </p>
        </section>

        <section
          className="rounded-2xl border border-stone-200 bg-white p-5 lg:p-7"
          aria-labelledby="contributors-title"
        >
          <h2 id="contributors-title" className="font-extrabold text-stone-900">
            기여한 모험가 동료들
          </h2>
          <BookContributorList contributors={contributors} />
        </section>
      </div>
    </article>
  );
}
