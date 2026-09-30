import { useMemo, useState } from 'react';

import { useCurrentUserQuery } from '@/features/auth';
import {
  BossRunCard,
  useBossRunsQuery,
  useMyBossRunsQuery,
  type BossRunStatus,
} from '@/features/boss';
import {
  EmptyState,
  ErrorState,
  Pagination,
  Skeleton,
  SkeletonSection,
} from '@/shared/components';

type BossRunFilter = 'SCHEDULED' | 'ACTIVE' | 'ENDED';
type EndedResultFilter = 'CLEARED' | 'CLOSED';

const PAGE_SIZE = 10;

// 참여 회차는 한 사용자가 제출한 회차 수만큼이라 한 번에 받아 두고 대조한다.
// 서버가 허용하는 최대 크기이며, 이보다 많이 참여한 회차는 뱃지가 붙지 않는다.
const PARTICIPATION_PAGE_SIZE = 100;

// 탭은 사용자가 찾는 것을 기준으로 묶는다. 클리어와 미클리어는 결과의 차이일 뿐 찾는
// 대상이 다르지 않고, CLOSED는 그 둘이 나중에 넘어가는 후속 상태라 따로 떼면 지난 회차가
// 탭 사이를 옮겨 다닌다. 결과는 카드 뱃지로 구분한다.
const statusOptions: {
  value: BossRunFilter;
  label: string;
  statuses: BossRunStatus[];
}[] = [
  { value: 'SCHEDULED', label: '예정', statuses: ['SCHEDULED'] },
  { value: 'ACTIVE', label: '진행 중', statuses: ['ACTIVE'] },
  {
    value: 'ENDED',
    label: '종료',
    statuses: ['CLEARED', 'ENDED_UNCLEARED', 'CLOSED'],
  },
];

// 카드에서 상태 뱃지를 없앴으므로 종료 회차의 결과는 이 하위 필터로 구분한다.
// 미클리어 종료는 마감에 함께 담는다. 클리어하지 못하고 끝난 회차라는 점에서 사용자가
// 찾는 대상이 같고, 라벨 두 개만 두면서 따로 떼면 어느 필터에도 담기지 않아 사라진다.
const endedResultOptions: {
  value: EndedResultFilter;
  label: string;
  statuses: BossRunStatus[];
}[] = [
  { value: 'CLEARED', label: '클리어', statuses: ['CLEARED'] },
  { value: 'CLOSED', label: '마감', statuses: ['ENDED_UNCLEARED', 'CLOSED'] },
];

export function BossListPage() {
  const [filter, setFilter] = useState<BossRunFilter>('ACTIVE');
  const [endedResultFilter, setEndedResultFilter] = useState<EndedResultFilter>('CLEARED');
  const [page, setPage] = useState(0);

  const selectedStatuses =
    filter === 'ENDED'
      ? (endedResultOptions.find((option) => option.value === endedResultFilter)?.statuses ?? [])
      : (statusOptions.find((option) => option.value === filter)?.statuses ?? []);
  const bossRunsQuery = useBossRunsQuery({
    status: selectedStatuses,
    page,
    size: PAGE_SIZE,
  });

  // 참여 판정은 제출 이력이 있는 회차 목록으로 한다. 목록 응답의 mySubmissionCount는
  // "오늘" 제출 수라서, 어제까지만 제출한 회차를 참여하지 않은 것으로 잘못 표시한다.
  const currentUserQuery = useCurrentUserQuery();
  const myBossRunsQuery = useMyBossRunsQuery(
    { page: 0, size: PARTICIPATION_PAGE_SIZE },
    currentUserQuery.data != null,
  );
  const participatingBossRunIds = useMemo(
    () => new Set(myBossRunsQuery.data?.content.map((bossRun) => bossRun.bossRunId) ?? []),
    [myBossRunsQuery.data],
  );

  if (bossRunsQuery.isPending) {
    // 실제 카드 그리드와 같은 골격. 보스 이미지 원형과 이름 줄을 흉내 낸다.
    return (
      <SkeletonSection label="원정을 불러오는 중입니다">
        <div className="mt-5 grid grid-cols-2 gap-4 lg:grid-cols-4 lg:gap-5">
          {Array.from({ length: 4 }, (_, index) => (
            <div key={index} className="rounded-card bg-white p-4 shadow-sm">
              <Skeleton className="mx-auto mt-6 size-24 rounded-full" />
              <Skeleton className="mt-4 h-5 w-2/3" />
              <Skeleton className="mt-3 h-3 w-full rounded-full" />
            </div>
          ))}
        </div>
      </SkeletonSection>
    );
  }

  if (bossRunsQuery.isError) {
    return (
      <ErrorState
        title="원정을 불러오지 못했습니다"
        description="잠시 후 다시 시도해 주세요."
        onRetry={() => void bossRunsQuery.refetch()}
      />
    );
  }

  const pageContent = bossRunsQuery.data.content;
  const totalPages = bossRunsQuery.data.totalPages;

  return (
    <section>
      <header>
        <h1 className="text-2xl font-extrabold text-violet-800">이야기 원정</h1>
        <p className="mt-2 text-sm text-stone-600">함께 쓰는 이야기 원정</p>
      </header>

      <div
        role="group"
        aria-label="회차 상태"
        className="mt-5 grid grid-cols-3 gap-1 rounded-2xl border border-stone-200 bg-white p-1 text-center text-sm font-semibold"
      >
        {statusOptions.map((option) => {
          const isSelected = filter === option.value;

          return (
            <button
              key={option.value}
              type="button"
              aria-pressed={isSelected}
              onClick={() => {
                setFilter(option.value);
                // 종료 탭을 다시 열 때 이전 결과 필터가 남아 있지 않게 초기화한다.
                setEndedResultFilter('CLEARED');
                setPage(0);
              }}
              className={[
                'min-h-11 rounded-xl px-2 transition',
                isSelected
                  ? 'bg-violet-100 text-violet-700'
                  : 'text-stone-500 hover:bg-stone-50',
              ].join(' ')}
            >
              {option.label}
            </button>
          );
        })}
      </div>

      {filter === 'ENDED' && (
        <div
          role="group"
          aria-label="종료 결과"
          className="mt-3 flex gap-2 overflow-x-auto pb-1"
        >
          {endedResultOptions.map((option) => {
            const isSelected = endedResultFilter === option.value;

            return (
              <button
                key={option.value}
                type="button"
                aria-pressed={isSelected}
                onClick={() => {
                  setEndedResultFilter(option.value);
                  setPage(0);
                }}
                className={[
                  'min-h-10 shrink-0 rounded-full border px-4 text-sm font-semibold transition',
                  isSelected
                    ? 'border-violet-200 bg-violet-100 text-violet-800'
                    : 'border-stone-200 bg-white text-stone-600 hover:bg-stone-50',
                ].join(' ')}
              >
                {option.label}
              </button>
            );
          })}
        </div>
      )}

      {pageContent.length === 0 ? (
        <EmptyState
          title="표시할 원정이 없습니다"
          description="다른 회차 상태를 선택해 보세요."
        />
      ) : (
        // 열 수는 앱 셸이 넓어지는 lg에서만 늘린다. LibraryPage 책 그리드와 같은 이유다.
        <div className="mt-5 grid grid-cols-2 gap-4 lg:grid-cols-4 lg:gap-5">
          {pageContent.map((bossRun) => (
            <BossRunCard
              key={bossRun.bossRunId}
              bossRun={bossRun}
              isParticipating={participatingBossRunIds.has(bossRun.bossRunId)}
            />
          ))}
        </div>
      )}

      <Pagination
        className="mt-6"
        ariaLabel="보스전 목록 페이지"
        page={page}
        totalPages={totalPages}
        onPrev={() => setPage((current) => current - 1)}
        onNext={() => setPage((current) => current + 1)}
      />
    </section>
  );
}
