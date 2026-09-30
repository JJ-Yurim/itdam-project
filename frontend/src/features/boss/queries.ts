import { keepPreviousData, useQuery } from '@tanstack/react-query';

import {
  getBossRun,
  getBossRunLeaderboard,
  getBossRuns,
  getEligibleSentences,
  getMyBossRuns,
} from './api';
import { bossQueryKeys } from './queryKeys';

import type { BossRunListParams, EligibleSentenceListParams, MyBossRunListParams } from './types';

export function useBossRunsQuery(params: BossRunListParams = {}, enabled = true) {
  return useQuery({
    queryKey: bossQueryKeys.list(params),
    queryFn: () => getBossRuns(params),
    enabled,
    // 탭과 페이지를 옮길 때 이전 목록을 유지한다. 없으면 매번 전체 화면 로딩으로 되돌아가
    // 목록이 사라졌다가 다시 나타난다.
    placeholderData: keepPreviousData,
  });
}

export function useEligibleSentencesQuery(
  bossRunId: number,
  params: EligibleSentenceListParams = {},
  options: { enabled?: boolean } = {},
) {
  const { enabled = true } = options;

  return useQuery({
    queryKey: bossQueryKeys.eligibleSentences(bossRunId, params),
    queryFn: () => getEligibleSentences(bossRunId, params),
    enabled: enabled && bossRunId > 0,
    placeholderData: keepPreviousData,
  });
}

export function useBossRunQuery(
  bossRunId: number,
  options: { refetchInterval?: number | false } = {},
) {
  return useQuery({
    queryKey: bossQueryKeys.detail(bossRunId),
    queryFn: () => getBossRun(bossRunId),
    enabled: bossRunId > 0,
    // SSE가 반복 실패해 폴링으로 강등된 화면이 주기 갱신에 쓴다. 평상시에는 끈다.
    refetchInterval: options.refetchInterval ?? false,
  });
}

export function useBossRunLeaderboardQuery(
  bossRunId: number,
  options: { refetchInterval?: number | false } = {},
) {
  return useQuery({
    queryKey: bossQueryKeys.leaderboard(bossRunId),
    queryFn: () => getBossRunLeaderboard(bossRunId),
    enabled: bossRunId > 0,
    // 실시간(SSE)일 때는 leaderboard 이벤트가 캐시를 대체하므로 주기 갱신을 끈다.
    // 폴링 강등 시에만 호출자가 주기를 켠다.
    refetchInterval: options.refetchInterval ?? false,
  });
}

// 내가 문장을 제출해 참여한 보스런 이력을 조회한다.
export function useMyBossRunsQuery(params: MyBossRunListParams = {}, enabled = true) {
  return useQuery({
    queryKey: bossQueryKeys.myRuns(params),
    queryFn: () => getMyBossRuns(params),
    enabled,
  });
}
