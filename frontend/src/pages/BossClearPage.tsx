import { useEffect } from 'react';

import { useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';

import {
  BossClearResult,
  bossQueryKeys,
  getBossRunStatusPolicy,
  useBossRunQuery,
} from '@/features/boss';
import { BookGenerationStatusPanel } from '@/features/book-generation';
import { ErrorState, LoadingState } from '@/shared/components';

export function BossClearPage() {
  const { bossRunId: bossRunIdParam } = useParams();
  const bossRunId = Number(bossRunIdParam);

  if (!Number.isInteger(bossRunId) || bossRunId <= 0) {
    return (
      <ErrorState
        title="잘못된 원정 주소입니다"
        description="원정 목록에서 다시 선택해 주세요."
      />
    );
  }

  return <BossClearContent bossRunId={bossRunId} />;
}

interface BossClearContentProps {
  bossRunId: number;
}

function BossClearContent({ bossRunId }: BossClearContentProps) {
  const queryClient = useQueryClient();
  const bossRunQuery = useBossRunQuery(bossRunId);
  const bossRunStatus = bossRunQuery.data?.status;

  // 클리어 확인 시점에 회차 목록 캐시가 이전 상태(ACTIVE 등)를 보여주지 않도록 갱신한다.
  useEffect(() => {
    if (bossRunStatus === 'CLEARED') {
      void queryClient.invalidateQueries({ queryKey: bossQueryKeys.lists() });
    }
  }, [bossRunStatus, queryClient]);

  if (bossRunQuery.isPending) {
    return (
      <LoadingState
        title="클리어 결과를 불러오는 중입니다"
        description="원정 결과와 공동 책 생성 상태를 확인하고 있습니다."
      />
    );
  }

  if (bossRunQuery.isError) {
    return (
      <ErrorState
        title="클리어 결과를 불러오지 못했습니다"
        description="원정이 존재하지 않거나 요청에 실패했습니다."
        onRetry={() => void bossRunQuery.refetch()}
      />
    );
  }

  const bossRun = bossRunQuery.data;

  if (bossRun.status !== 'CLEARED') {
    const statusPolicy = getBossRunStatusPolicy(bossRun.status);

    return (
      <section className="mt-10 space-y-4 text-center">
        <h1 className="text-xl font-extrabold text-stone-900">아직 클리어되지 않은 원정이에요</h1>
        <p role="status" className="text-sm leading-6 text-stone-600">
          {bossRun.status === 'ACTIVE'
            ? '원정이 아직 진행 중이에요. 문장을 제출해 보스를 공략해 주세요.'
            : (statusPolicy.blockedMessage ?? '아직 클리어 결과를 볼 수 없는 원정이에요.')}
        </p>
        <Link
          to={`/boss/${bossRunId}`}
          className="mx-auto flex min-h-12 max-w-xs items-center justify-center rounded-xl border border-stone-300 bg-white px-4 py-3 font-semibold text-stone-700 transition hover:bg-stone-50"
        >
          원정 상세로 돌아가기
        </Link>
      </section>
    );
  }

  return (
    <BossClearResult
      bossName={bossRun.bossName}
      clearedAt={bossRun.clearedAt}
      accumulatedDamage={bossRun.accumulatedDamage}
      mySubmissionCount={bossRun.mySubmissionCount}
      mySubmissions={bossRun.mySubmissions}
      bookGeneration={<BookGenerationStatusPanel bossRunId={bossRunId} />}
      footerAction={
        <Link
          to="/boss"
          className="mt-8 flex min-h-12 w-full items-center justify-center rounded-xl border border-stone-300 bg-white px-4 py-3 font-semibold text-stone-700 transition hover:bg-stone-50"
        >
          원정으로 돌아가기
        </Link>
      }
    />
  );
}
