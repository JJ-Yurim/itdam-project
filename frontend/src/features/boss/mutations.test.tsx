import type { PropsWithChildren } from 'react';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { useBossSubmissionMutation } from './mutations';
import { bossQueryKeys } from './queryKeys';

import type { BossRunResponse } from './types';

const BOSS_RUN_ID = 7;

function detailFixture(): BossRunResponse {
  return {
    bossRunId: BOSS_RUN_ID,
    bossId: 3,
    bossName: '새벽의 수호자',
    bossDescription: '',
    status: 'ACTIVE',
    startedAt: '2026-07-31T00:00:00Z',
    endsAt: '2026-08-01T00:00:00Z',
    clearedAt: null,
    accumulatedDamage: 380,
    currentPhase: {
      phaseId: 21,
      phaseNo: 1,
      maxHp: 1_000,
      remainingHp: 620,
      progressPercent: 38,
      hardMinScore: 20,
      recommendedScore: 35,
      damageMultiplier: 1.25,
      imageUrl: null,
      status: 'ACTIVE',
    },
    moods: [],
    mySubmissionCount: 1,
    remainingSubmissionCount: 2,
    mySubmissions: [],
  };
}

describe('useBossSubmissionMutation', () => {
  it('제출 응답의 남은 HP를 서버와 같은 공략 진행률로 반영한다', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    queryClient.setQueryData(bossQueryKeys.detail(BOSS_RUN_ID), detailFixture());

    const wrapper = ({ children }: PropsWithChildren) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
    const { result } = renderHook(() => useBossSubmissionMutation(BOSS_RUN_ID), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({
        sentenceVersionId: 84,
        submissionConsentAccepted: true,
      });
    });

    const detail = queryClient.getQueryData<BossRunResponse>(bossQueryKeys.detail(BOSS_RUN_ID));
    expect(detail?.currentPhase.remainingHp).toBe(565);
    expect(detail?.currentPhase.progressPercent).toBe(43.5);
  });
});
