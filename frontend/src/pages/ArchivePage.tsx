import { useRef, useState } from 'react';

import { useNavigate, useSearchParams } from 'react-router-dom';

import { useQueryClient } from '@tanstack/react-query';

import {
  ArchiveSentenceCard,
  UnevaluatedSentenceCard,
  useArchiveQuery,
  useMyContributedBooksQuery,
  useUnevaluatedArchiveQuery,
} from '@/features/archive';
import type { MyContributedBook, UnevaluatedArchiveSentence } from '@/features/archive';
import { MyBossRunCard, useMyBossRunsQuery } from '@/features/boss';
import { useMyBookmarksQuery } from '@/features/library';
import {
  discardRejectedSentenceNow,
  isDiscardableRejectedSentence,
  SentenceEvaluationResult,
} from '@/features/sentences';
import type { SentenceEvaluationResponse } from '@/features/sentences';
import {
  Button,
  ButtonLink,
  EmptyState,
  ErrorState,
  Modal,
  Pagination,
  Skeleton,
  SkeletonSection,
} from '@/shared/components';

const PAGE_SIZE = 10;

type ArchiveTab = 'sentences' | 'bossRuns' | 'books' | 'personal';
type SentenceView = 'evaluated' | 'unevaluated';

const tabItems: { value: ArchiveTab; label: string }[] = [
  { value: 'sentences', label: '내 문장' },
  { value: 'bossRuns', label: '참여한 원정' },
  { value: 'books', label: '기여한 책' },
  { value: 'personal', label: '개인 책' },
];

const sentenceViewItems: { value: SentenceView; label: string }[] = [
  { value: 'evaluated', label: '평가된 문장' },
  { value: 'unevaluated', label: '미평가된 문장' },
];

function parseTab(value: string | null): ArchiveTab {
  if (value === 'bossRuns' || value === 'books' || value === 'personal') {
    return value;
  }

  return 'sentences';
}

interface EvaluationResultState {
  sentence: UnevaluatedArchiveSentence;
  evaluation: SentenceEvaluationResponse;
}

/** 빈 상태용 연필 선화 아이콘. */
function PenIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-6"
    >
      <path d="m4 20 4.5-1 10-10a2.1 2.1 0 0 0-3-3l-10 10L4 20Z" />
      <path d="m14 7 3 3" />
    </svg>
  );
}

function ContributedBookCard({ book }: { book: MyContributedBook }) {
  return (
    <article className="rounded-2xl border border-stone-200 bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-base font-bold text-stone-900">{book.bossName}의 공동 책</h3>
          <p className="mt-1 text-xs text-stone-500">
            {new Date(book.completedAt).toLocaleDateString('ko-KR')} 완성 · 전체{' '}
            {book.contentCount}개 중 내 문장 {book.myIncludedContentCount}개 수록
          </p>
        </div>
        <span aria-hidden="true" className="text-2xl">
          📖
        </span>
      </div>

      <ButtonLink
        to={`/library/books/${book.bookId}`}
        variant="success"
        className="mt-4 w-full text-sm"
      >
        책 보러 가기
      </ButtonLink>
    </article>
  );
}

export function ArchivePage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const tabRefs = useRef<Partial<Record<ArchiveTab, HTMLButtonElement | null>>>({});
  const [evaluationResult, setEvaluationResult] = useState<EvaluationResultState | null>(null);
  const queryClient = useQueryClient();

  // 거부 결과 팝업을 수정 없이 닫으면 거부 문장을 서비스에 남기지 않고 삭제한다.
  // 판정과 삭제·캐시 정리는 rejectedSentenceCleanup 모듈이 화면 공통으로 담당한다.
  const closeEvaluationResult = (isRevising: boolean) => {
    if (
      !isRevising &&
      evaluationResult &&
      isDiscardableRejectedSentence({
        evaluationStatus: evaluationResult.evaluation.evaluationStatus,
        versionNo: evaluationResult.sentence.versionNo,
      })
    ) {
      discardRejectedSentenceNow(
        {
          sentenceId: evaluationResult.sentence.sentenceId,
          sentenceVersionId: evaluationResult.sentence.sentenceVersionId,
        },
        queryClient,
      );
    }

    setEvaluationResult(null);
  };

  const tab = parseTab(searchParams.get('tab'));
  const sentenceView: SentenceView =
    searchParams.get('view') === 'unevaluated' ? 'unevaluated' : 'evaluated';
  const requestedPage = Number(searchParams.get('page') ?? 1);
  const page = Number.isInteger(requestedPage) && requestedPage > 0 ? requestedPage - 1 : 0;

  const evaluatedQuery = useArchiveQuery(
    page,
    PAGE_SIZE,
    tab === 'sentences' && sentenceView === 'evaluated',
  );
  const unevaluatedQuery = useUnevaluatedArchiveQuery(
    page,
    PAGE_SIZE,
    tab === 'sentences' && sentenceView === 'unevaluated',
  );
  const bossRunsQuery = useMyBossRunsQuery({ page, size: PAGE_SIZE }, tab === 'bossRuns');
  const booksQuery = useMyContributedBooksQuery(page, PAGE_SIZE, tab === 'books');

  // 진입 카드용 개수 조회. 목록 데이터는 필요 없어 최소 크기로 요청한다.
  const draftCountQuery = useUnevaluatedArchiveQuery(0, 1, true);
  const draftCount = draftCountQuery.data?.totalElements;
  const bookmarkCountQuery = useMyBookmarksQuery({ page: 0, size: 1 });
  const bookmarkCount = bookmarkCountQuery.data?.totalElements;

  const activeQuery =
    tab === 'sentences'
      ? sentenceView === 'evaluated'
        ? evaluatedQuery
        : unevaluatedQuery
      : tab === 'bossRuns'
        ? bossRunsQuery
        : booksQuery;

  // 탭과 데이터를 판별 유니온으로 묶어 목록 렌더에서 옵셔널 접근 없이 좁힌다.
  const archiveView =
    tab === 'sentences'
      ? sentenceView === 'evaluated'
        ? evaluatedQuery.data && ({ kind: 'evaluated', data: evaluatedQuery.data } as const)
        : unevaluatedQuery.data && ({ kind: 'unevaluated', data: unevaluatedQuery.data } as const)
      : tab === 'bossRuns'
        ? bossRunsQuery.data && ({ kind: 'bossRuns', data: bossRunsQuery.data } as const)
        : tab === 'books'
          ? booksQuery.data && ({ kind: 'books', data: booksQuery.data } as const)
          : undefined;

  const isPageOutOfRange =
    archiveView !== undefined &&
    archiveView.data.totalElements > 0 &&
    archiveView.data.content.length === 0;

  // page 외의 쿼리 파라미터(탭 등)를 보존하며 페이지만 바꾼다.
  const goToPage = (pageNumber: number) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set('page', String(pageNumber));
      return next;
    });
  };

  const changeTab = (nextTab: ArchiveTab) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set('tab', nextTab);
      next.delete('page');
      next.delete('view');
      return next;
    });
  };

  const changeSentenceView = (nextView: SentenceView) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set('view', nextView);
      next.delete('page');
      return next;
    });
  };

  // 임시 저장 카드는 어느 탭에서든 미평가 문장 목록으로 바로 이동한다.
  const goToDrafts = () => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set('tab', 'sentences');
      next.set('view', 'unevaluated');
      next.delete('page');
      return next;
    });
  };

  // 좌우 화살표 키로 이웃 탭으로 순환 이동하고 포커스도 함께 옮긴다.
  const handleTabKeyDown = (event: React.KeyboardEvent) => {
    if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') {
      return;
    }

    event.preventDefault();
    const currentIndex = tabItems.findIndex(({ value }) => value === tab);
    const offset = event.key === 'ArrowRight' ? 1 : -1;
    const nextTab = tabItems[(currentIndex + offset + tabItems.length) % tabItems.length].value;
    changeTab(nextTab);
    tabRefs.current[nextTab]?.focus();
  };

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-extrabold text-violet-800">내 서고</h1>
        <p className="mt-2 text-sm text-stone-600">
          내가 만든 문장과 원정·공동 책 기록을 확인할 수 있어요.
        </p>
      </header>

      {/* 바로가기 카드 영역. 북마크 카드가 추가되면 두 번째 칸을 채운다. */}
      <div className="grid grid-cols-2 gap-3">
        <button
          type="button"
          onClick={goToDrafts}
          className="flex items-center gap-3 rounded-2xl border border-stone-200 bg-white p-4 text-left shadow-sm transition hover:bg-violet-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600"
        >
          <span
            aria-hidden="true"
            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-violet-100 text-lg"
          >
            📄
          </span>
          <span className="min-w-0 flex-1">
            <span className="block text-sm font-bold text-stone-900">임시 저장</span>
            <span className="block text-xs text-stone-500">
              {draftCount !== undefined ? `${draftCount}개` : ' '}
            </span>
          </span>
          <span aria-hidden="true" className="shrink-0 text-stone-400">
            ›
          </span>
        </button>

        <button
          type="button"
          onClick={() => void navigate('/archive/bookmarks')}
          className="flex items-center gap-3 rounded-2xl border border-stone-200 bg-white p-4 text-left shadow-sm transition hover:bg-violet-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600"
        >
          <span
            aria-hidden="true"
            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-violet-100 text-lg"
          >
            🔖
          </span>
          <span className="min-w-0 flex-1">
            <span className="block text-sm font-bold text-stone-900">북마크</span>
            <span className="block text-xs text-stone-500">
              {bookmarkCount !== undefined ? `${bookmarkCount}개` : ' '}
            </span>
          </span>
          <span aria-hidden="true" className="shrink-0 text-stone-400">
            ›
          </span>
        </button>
      </div>

      <div
        role="tablist"
        aria-label="내 서고 목록 구분"
        className="grid grid-cols-4 rounded-xl bg-stone-100 p-1 text-center text-xs font-semibold sm:text-sm"
      >
        {tabItems.map(({ value, label }) => (
          <button
            key={value}
            ref={(element) => {
              tabRefs.current[value] = element;
            }}
            type="button"
            role="tab"
            id={`archive-tab-${value}`}
            aria-selected={tab === value}
            aria-controls="archive-tabpanel"
            tabIndex={tab === value ? 0 : -1}
            onClick={() => changeTab(value)}
            onKeyDown={handleTabKeyDown}
            className={[
              'min-h-10 rounded-lg px-1 py-2 transition',
              tab === value ? 'bg-white text-violet-700 shadow-sm' : 'text-stone-500',
            ].join(' ')}
          >
            {label}
          </button>
        ))}
      </div>

      <div
        role="tabpanel"
        id="archive-tabpanel"
        aria-labelledby={`archive-tab-${tab}`}
        className="space-y-6"
      >
        {tab === 'sentences' && (
          <div role="group" aria-label="문장 평가 상태 구분" className="flex gap-2">
            {sentenceViewItems.map(({ value, label }) => (
              <button
                key={value}
                type="button"
                aria-pressed={sentenceView === value}
                onClick={() => changeSentenceView(value)}
                className={[
                  'min-h-10 flex-1 rounded-xl border px-3 text-sm font-semibold transition',
                  sentenceView === value
                    ? 'border-violet-300 bg-violet-50 text-violet-800'
                    : 'border-stone-200 bg-white text-stone-500 hover:bg-stone-50',
                ].join(' ')}
              >
                {label}
              </button>
            ))}
          </div>
        )}

        {tab === 'personal' ? (
          <EmptyState
            title="개인 책은 준비 중이에요"
            description="나만의 책을 엮는 기능이 곧 열릴 예정이에요."
          />
        ) : (
          <>
            {/* 문장 카드 목록과 같은 골격을 보여준다. */}
            {activeQuery.isPending && (
              <SkeletonSection label="내 서고를 불러오고 있어요">
                <div className="space-y-4">
                  {Array.from({ length: 3 }, (_, index) => (
                    <div key={index} className="rounded-card border border-stone-200 bg-white p-5">
                      <div className="flex gap-2">
                        <Skeleton className="h-6 w-16 rounded-full" />
                        <Skeleton className="h-6 w-20 rounded-full" />
                      </div>
                      <Skeleton className="mt-4 h-4 w-full" />
                      <Skeleton className="mt-2 h-4 w-2/3" />
                    </div>
                  ))}
                </div>
              </SkeletonSection>
            )}

            {/* 초기 조회 실패만 전체 오류로 처리하고, 갱신 실패는 기존 목록 위 배너로 알린다. */}
            {activeQuery.isError && !archiveView && (
              <ErrorState
                title="내 서고를 불러오지 못했어요"
                description="잠시 후 다시 시도해 주세요."
                onRetry={() => void activeQuery.refetch()}
              />
            )}

            {activeQuery.isError && archiveView && (
              <div
                role="alert"
                className="flex items-center justify-between gap-3 rounded-xl bg-red-50 px-4 py-3"
              >
                <span className="text-sm text-red-700">목록을 새로 불러오지 못했어요.</span>
                <button
                  type="button"
                  onClick={() => void activeQuery.refetch()}
                  className="shrink-0 text-sm font-semibold text-red-700 underline underline-offset-2"
                >
                  다시 시도
                </button>
              </div>
            )}

            {archiveView && (
              <>
                <div className="flex items-center justify-between gap-3">
                  <p className="text-sm font-semibold text-stone-700">
                    총 {archiveView.data.totalElements}
                    {archiveView.kind === 'bossRuns'
                      ? '개의 원정'
                      : archiveView.kind === 'books'
                        ? '권의 책'
                        : '개의 문장'}
                  </p>
                  <p className="text-xs text-stone-500">페이지 {archiveView.data.page + 1}</p>
                </div>

                {isPageOutOfRange ? (
                  <div className="space-y-4">
                    <EmptyState
                      title="해당 페이지에 항목이 없어요"
                      description="페이지 범위를 벗어났어요. 첫 페이지에서 다시 확인해 주세요."
                    />
                    <div className="flex justify-center">
                      <Button variant="secondary" className="text-sm" onClick={() => goToPage(1)}>
                        첫 페이지로 이동
                      </Button>
                    </div>
                  </div>
                ) : archiveView.kind === 'evaluated' ? (
                  archiveView.data.content.length === 0 ? (
                    <EmptyState
                      title="아직 평가를 완료한 문장이 없어요"
                      description="미평가된 문장에 AI 평가를 요청하면 이곳에 모여요."
                      icon={<PenIcon />}
                      action={
                        <Button variant="secondary" className="text-sm" onClick={goToDrafts}>
                          미평가 문장 보러 가기
                        </Button>
                      }
                    />
                  ) : (
                    <section aria-label="평가된 문장 목록" className="space-y-4">
                      {archiveView.data.content.map((sentence) => (
                        <ArchiveSentenceCard key={sentence.sentenceVersionId} sentence={sentence} />
                      ))}
                    </section>
                  )
                ) : archiveView.kind === 'unevaluated' ? (
                  archiveView.data.content.length === 0 ? (
                    <EmptyState
                      title="아직 저장한 문장이 없어요"
                      description="오늘의 단어로 문장을 만들고 서고에 저장해 보세요."
                      icon={<PenIcon />}
                      action={
                        <ButtonLink to="/sentences/new" className="text-sm">
                          문장 만들러 가기
                        </ButtonLink>
                      }
                    />
                  ) : (
                    <section aria-label="미평가된 문장 목록" className="space-y-4">
                      {archiveView.data.content.map((sentence) => (
                        <UnevaluatedSentenceCard
                          key={sentence.sentenceVersionId}
                          sentence={sentence}
                          onEvaluated={(evaluatedSentence, evaluation) =>
                            setEvaluationResult({ sentence: evaluatedSentence, evaluation })
                          }
                        />
                      ))}
                    </section>
                  )
                ) : archiveView.kind === 'bossRuns' ? (
                  archiveView.data.content.length === 0 ? (
                    <EmptyState
                      title="아직 참여한 원정이 없어요"
                      description="평가받은 문장으로 원정에 참여하면 이력이 이곳에 모여요."
                    />
                  ) : (
                    <section aria-label="참여한 원정 목록" className="space-y-4">
                      {archiveView.data.content.map((myBossRun) => (
                        <MyBossRunCard key={myBossRun.bossRunId} myBossRun={myBossRun} />
                      ))}
                    </section>
                  )
                ) : archiveView.data.content.length === 0 ? (
                  <EmptyState
                    title="아직 기여한 공동 책이 없어요"
                    description="원정을 클리어하면 내 문장이 담긴 공동 책이 이곳에 모여요."
                  />
                ) : (
                  <section aria-label="기여한 공동 책 목록" className="space-y-4">
                    {archiveView.data.content.map((book) => (
                      <ContributedBookCard key={book.bookId} book={book} />
                    ))}
                  </section>
                )}

                <Pagination
                  ariaLabel="내 서고 페이지"
                  page={archiveView.data.page}
                  totalPages={archiveView.data.totalPages}
                  hasNext={archiveView.data.hasNext}
                  onPrev={() => goToPage(archiveView.data.page)}
                  onNext={() => goToPage(archiveView.data.page + 2)}
                />
              </>
            )}
          </>
        )}
      </div>

      {/* 평가 결과 팝업: 목록 갱신으로 카드가 사라져도 유지되도록 페이지에서 렌더링한다. */}
      {evaluationResult && (
        <Modal
          open
          title="AI 평가 결과"
          onClose={() => closeEvaluationResult(false)}
          footer={
            <>
              {evaluationResult.evaluation.evaluationStatus === 'REJECTED' && (
                <Button
                  onClick={() => {
                    closeEvaluationResult(true);
                    void navigate(
                      `/sentences/new?versionId=${evaluationResult.sentence.sentenceVersionId}`,
                    );
                  }}
                >
                  수정하러 가기
                </Button>
              )}
              <Button variant="secondary" onClick={() => closeEvaluationResult(false)}>
                닫기
              </Button>
            </>
          }
        >
          <SentenceEvaluationResult evaluation={evaluationResult.evaluation} />

          {evaluationResult.evaluation.evaluationStatus === 'REJECTED' ? (
            <p className="mt-3 text-sm leading-6 text-red-700">
              거부된 문장은 목록에 보관되지 않아요. 수정하러 가면 새 초안으로 이어서 고칠 수
              있습니다.
            </p>
          ) : (
            <p className="mt-3 text-sm leading-6 text-stone-600">
              평가가 완료된 문장은 평가된 문장 목록으로 이동해요.
            </p>
          )}
        </Modal>
      )}
    </div>
  );
}

