import { useMemo } from 'react';
import { useParams } from 'react-router-dom';

import { BookReaderExperience } from '@/features/book-reader/components/BookReaderExperience';
import { useLibraryBookQuery } from '@/features/library';
import { EmptyState, ErrorState, LoadingState } from '@/shared/components';

import './BookReaderPage.css';

export function BookReaderPage() {
  const { bookId: bookIdParam } = useParams();
  const bookId = Number(bookIdParam);
  const libraryBookQuery = useLibraryBookQuery(bookId);
  const readerData = useMemo(() => {
    const book = libraryBookQuery.data;
    if (!book) return null;

    const contents = [...book.contents].sort((left, right) => left.sequenceNo - right.sequenceNo);
    const body = book.body?.trim() ?? '';
    const readableBody =
      body ||
      contents
        .map((content) => content.content.trim())
        .filter(Boolean)
        .join('\n\n');
    return { book, readableBody, contents };
  }, [libraryBookQuery.data]);

  if (!Number.isInteger(bookId) || bookId <= 0) {
    return (
      <ErrorState
        title="잘못된 공동 책 주소입니다"
        description="내 서고 목록에서 다시 선택해 주세요."
      />
    );
  }

  if (libraryBookQuery.isPending) {
    return <LoadingState title="공동 소설집을 불러오는 중입니다" />;
  }

  if (libraryBookQuery.isError) {
    return (
      <ErrorState
        title="공동 소설집을 불러오지 못했습니다"
        description="잠시 후 다시 시도해 주세요."
        onRetry={() => void libraryBookQuery.refetch()}
      />
    );
  }

  const { book, readableBody, contents } = readerData!;

  if (!readableBody) {
    return <EmptyState title="아직 읽을 수 있는 본문이 없습니다" />;
  }

  return (
    <BookReaderExperience
      title={book.bossName}
      body={readableBody}
      contents={contents}
      backTo={`/library/books/${book.bookId}`}
    />
  );
}
