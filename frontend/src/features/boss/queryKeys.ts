import type {
  BossRunListParams,
  EligibleSentenceListParams,
  MyBossRunListParams,
} from './types';

export const bossQueryKeys = {
  all: ['boss-runs'] as const,

  lists: () => [...bossQueryKeys.all, 'list'] as const,

  list: (params: BossRunListParams) => [...bossQueryKeys.lists(), params] as const,

  details: () => [...bossQueryKeys.all, 'detail'] as const,

  detail: (bossRunId: number) => [...bossQueryKeys.details(), bossRunId] as const,

  leaderboard: (bossRunId: number) => [...bossQueryKeys.detail(bossRunId), 'leaderboard'] as const,

  eligibleSentenceLists: (bossRunId: number) =>
    [...bossQueryKeys.detail(bossRunId), 'eligible-sentences'] as const,

  eligibleSentences: (bossRunId: number, params: EligibleSentenceListParams) =>
    [...bossQueryKeys.eligibleSentenceLists(bossRunId), params] as const,

  myRuns: (params: MyBossRunListParams) => [...bossQueryKeys.all, 'my-runs', params] as const,
  myRunLists: () => [...bossQueryKeys.all, 'my-runs'] as const,
};
