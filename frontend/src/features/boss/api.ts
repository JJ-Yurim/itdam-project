import { httpClient } from '@/shared/api';

import type { PageResponse } from '@/shared/api';

import type {
  BossRunLeaderboardResponse,
  BossRunListParams,
  BossRunPageResponse,
  BossRunResponse,
  BossRunSummaryResponse,
  BossSubmissionRequest,
  BossSubmissionResponse,
  EligibleSentenceListParams,
  EligibleSentenceResponse,
  MyBossRunListParams,
  MyBossRunResponse,
} from './types';

export async function submitBossSentence(
  bossRunId: number,
  request: BossSubmissionRequest,
): Promise<BossSubmissionResponse> {
  const { data } = await httpClient.post<BossSubmissionResponse>(
    `/v1/boss-runs/${bossRunId}/submissions`,
    request,
  );

  return data;
}

export async function getBossRuns(
  params: BossRunListParams = {},
): Promise<BossRunPageResponse<BossRunSummaryResponse>> {
  const { data } = await httpClient.get<BossRunPageResponse<BossRunSummaryResponse>>(
    '/v1/boss-runs',
    { params },
  );

  return data;
}

export async function getBossRun(bossRunId: number): Promise<BossRunResponse> {
  const { data } = await httpClient.get<BossRunResponse>(`/v1/boss-runs/${bossRunId}`);

  return { ...data, mySubmissions: data.mySubmissions ?? [] };
}

export async function getBossRunLeaderboard(bossRunId: number): Promise<BossRunLeaderboardResponse> {
  const { data } = await httpClient.get<BossRunLeaderboardResponse>(
    `/v1/boss-runs/${bossRunId}/leaderboard`,
  );

  return { ...data, entries: data.entries ?? [] };
}

export async function getMyBossRuns(
  params: MyBossRunListParams = {},
): Promise<PageResponse<MyBossRunResponse>> {
  const { data } = await httpClient.get<PageResponse<MyBossRunResponse>>('/v1/me/boss-runs', {
    params,
  });

  return { ...data, content: data.content ?? [] };
}

export async function getEligibleSentences(
  bossRunId: number,
  params: EligibleSentenceListParams = {},
): Promise<PageResponse<EligibleSentenceResponse>> {
  const { data } = await httpClient.get<PageResponse<EligibleSentenceResponse>>(
    `/v1/boss-runs/${bossRunId}/eligible-sentences`,
    {
      params,
    },
  );

  return data;
}
