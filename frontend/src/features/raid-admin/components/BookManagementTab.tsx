import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';

import { Button, ErrorState, LoadingState } from '@/shared/components';

import { useAdminBooksQuery } from '../queries';
import type { AdminBook, BookStatus, BookVisibility } from '../types';
import { AdminDataTable } from './AdminDataTable';
import { AdminDrawer } from './AdminDrawer';
import { AdminFilters } from './AdminFilters';
import { AdminPagination } from './AdminPagination';
import { BookEditor } from './BookEditor';
import { StatusBadge } from './StatusBadge';
import { formatAdminDateTime } from './adminTableFormat';
import { readEnumParam, usePageParam } from './usePageParam';

const statusLabel: Record<BookStatus, string> = {
  GENERATING: '생성 중',
  COMPLETED: '생성 완료',
  FAILED: '생성 실패',
};

const statusTone: Record<BookStatus, 'violet' | 'emerald' | 'red'> = {
  GENERATING: 'violet',
  COMPLETED: 'emerald',
  FAILED: 'red',
};

const visibilityLabel: Record<BookVisibility, string> = { PUBLIC: '공개', HIDDEN: '비공개' };

const bookStatuses = Object.keys(statusLabel) as BookStatus[];
const visibilities = Object.keys(visibilityLabel) as BookVisibility[];

export function BookManagementTab() {
  const [searchParams] = useSearchParams();
  const [page, changePage] = usePageParam();
  // 다른 탭이 남긴 status(보스 상태·레이드 상태)나 직접 조작한 값이 그대로 서버로 새면,
  // 셀렉트는 비어 있는데 목록만 걸러진 채로 보인다.
  const statusParam = readEnumParam(searchParams.get('status'), bookStatuses);
  const filters = {
    query: searchParams.get('query') ?? undefined,
    statuses: statusParam ? [statusParam] : undefined,
    visibility: readEnumParam(searchParams.get('visibility'), visibilities),
    page,
  };
  const booksQuery = useAdminBooksQuery(filters);
  const [editorTarget, setEditorTarget] = useState<AdminBook | null>(null);

  return (
    <div className="space-y-4">
      <AdminFilters
        searchLabel="보스 이름 검색"
        selects={[
          {
            key: 'status',
            label: '생성 상태',
            emptyLabel: '전체 생성 상태',
            options: bookStatuses.map((status) => ({
              value: status,
              label: statusLabel[status],
            })),
          },
          {
            key: 'visibility',
            label: '공개 상태',
            emptyLabel: '전체 공개 상태',
            options: [
              { value: 'PUBLIC', label: '공개' },
              { value: 'HIDDEN', label: '비공개' },
            ],
          },
        ]}
      />

      {booksQuery.isPending ? (
        <LoadingState title="책 목록을 불러오고 있습니다" />
      ) : booksQuery.isError ? (
        <ErrorState
          title="책 목록을 불러오지 못했습니다"
          description="잠시 후 다시 시도해 주세요."
          onRetry={() => void booksQuery.refetch()}
        />
      ) : (
        <>
          <p className="text-sm text-stone-500">총 {booksQuery.data.totalElements}권</p>
          <AdminDataTable
            label="책 목록"
            rows={booksQuery.data.content}
            rowKey={(book) => book.bookId}
            emptyTitle="조건에 맞는 책이 없습니다"
            emptyDescription="검색 조건을 지우면 전체 책을 볼 수 있습니다."
            columns={[
              { key: 'cover', header: '표지', render: (book) => <BookCover book={book} /> },
              {
                key: 'id',
                header: '책 ID',
                align: 'right',
                render: (book) => <span className="text-xs text-stone-500">{book.bookId}</span>,
              },
              {
                key: 'boss',
                header: '보스',
                render: (book) => <span className="font-semibold text-stone-900">{book.bossName}</span>,
              },
              {
                key: 'status',
                header: '생성 상태',
                render: (book) => (
                  <StatusBadge label={statusLabel[book.status]} tone={statusTone[book.status]} />
                ),
              },
              {
                key: 'visibility',
                header: '공개 상태',
                render: (book) => (
                  <StatusBadge
                    label={visibilityLabel[book.visibility]}
                    tone={book.visibility === 'PUBLIC' ? 'emerald' : 'stone'}
                  />
                ),
              },
              {
                key: 'completedAt',
                header: '완성일',
                align: 'right',
                render: (book) => (
                  <span className="text-xs text-stone-500">{formatAdminDateTime(book.completedAt)}</span>
                ),
              },
              {
                key: 'contents',
                header: '콘텐츠',
                align: 'right',
                render: (book) => `${book.contentCount}편`,
              },
              {
                key: 'actions',
                header: '관리',
                align: 'right',
                render: (book) => (
                  <BookRowAction book={book} onOpen={() => setEditorTarget(book)} />
                ),
              },
            ]}
            renderCard={(book) => (
              <div className="space-y-2">
                <div className="flex items-start justify-between gap-2">
                  <div className="flex min-w-0 items-center gap-2">
                    <BookCover book={book} />
                    <div className="min-w-0">
                      <p className="font-semibold text-stone-900">{book.bossName}</p>
                      <p className="text-xs text-stone-400">#{book.bookId}</p>
                    </div>
                  </div>
                  <div className="flex shrink-0 flex-col items-end gap-1">
                    <StatusBadge label={statusLabel[book.status]} tone={statusTone[book.status]} />
                    <StatusBadge
                      label={visibilityLabel[book.visibility]}
                      tone={book.visibility === 'PUBLIC' ? 'emerald' : 'stone'}
                    />
                  </div>
                </div>
                <p className="text-xs text-stone-500">
                  완성 {formatAdminDateTime(book.completedAt)} · 수록 {book.contentCount}편
                </p>
                <BookRowAction book={book} onOpen={() => setEditorTarget(book)} />
              </div>
            )}
          />
          <AdminPagination
            page={booksQuery.data.page}
            totalPages={booksQuery.data.totalPages}
            hasNext={booksQuery.data.hasNext}
            label="책"
            onChange={changePage}
          />
        </>
      )}

      <AdminDrawer open={editorTarget !== null} title="책 관리" onClose={() => setEditorTarget(null)}>
        {/* 대상이 바뀌면 패널 상태를 확실히 초기화한다. */}
        {editorTarget && (
          <BookEditor
            key={editorTarget.bookId}
            book={editorTarget}
            onClose={() => setEditorTarget(null)}
          />
        )}
      </AdminDrawer>
    </div>
  );
}

function BookCover({ book }: { book: AdminBook }) {
  return book.coverImageUrl ? (
    <img
      src={book.coverImageUrl}
      alt={`${book.bossName} 책 표지`}
      className="size-12 shrink-0 rounded-lg object-cover"
    />
  ) : (
    <div className="flex size-12 shrink-0 items-center justify-center rounded-lg bg-stone-200 text-[10px] text-stone-500">
      표지 없음
    </div>
  );
}

function BookRowAction({ book, onOpen }: { book: AdminBook; onOpen: () => void }) {
  return (
    <div className="flex justify-end">
      {/* 행마다 같은 이름의 버튼이 놓이므로 대상 이름을 접근성 이름에 넣는다. */}
      <Button
        variant="secondary"
        aria-label={`${book.bossName} 책 관리`}
        className="min-h-9 px-3 py-1 text-xs"
        onClick={onOpen}
      >
        관리
      </Button>
    </div>
  );
}
