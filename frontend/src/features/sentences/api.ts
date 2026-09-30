import axios from 'axios';

import { httpClient } from '@/shared/api';

import type {
  EvaluationUsage,
  SentenceDraftRequest,
  SentenceEvaluationResponse,
  SentenceVersionResponse,
} from './types';

export async function getTodayEvaluationUsage(): Promise<EvaluationUsage> {
  const { data } = await httpClient.get<EvaluationUsage>('/v1/evaluation-usage/today');

  return data;
}

export async function createSentenceDraft(content: string): Promise<SentenceVersionResponse> {
  const { data } = await httpClient.post<SentenceVersionResponse>('/v1/sentences', {
    content,
  } satisfies SentenceDraftRequest);

  return data;
}

export async function createSentenceVersion(
  sentenceId: number,
  content: string,
): Promise<SentenceVersionResponse> {
  const { data } = await httpClient.post<SentenceVersionResponse>(
    `/v1/sentences/${sentenceId}/versions`,
    { content } satisfies SentenceDraftRequest,
  );

  return data;
}

export async function updateSentenceDraft(
  versionId: number,
  content: string,
): Promise<SentenceVersionResponse> {
  const { data } = await httpClient.patch<SentenceVersionResponse>(
    `/v1/sentence-versions/${versionId}`,
    { content } satisfies SentenceDraftRequest,
  );

  return data;
}

export async function getSentenceVersion(versionId: number): Promise<SentenceVersionResponse> {
  const { data } = await httpClient.get<SentenceVersionResponse>(
    `/v1/sentence-versions/${versionId}`,
  );

  return data;
}

export async function getSentenceEvaluation(
  versionId: number,
): Promise<SentenceEvaluationResponse> {
  const { data } = await httpClient.get<SentenceEvaluationResponse>(
    `/v1/sentence-versions/${versionId}/evaluation`,
  );

  return data;
}

export async function deleteSentence(sentenceId: number): Promise<void> {
  await httpClient.delete(`/v1/sentences/${sentenceId}`);
}

export async function evaluateSentenceVersion(
  versionId: number,
): Promise<SentenceEvaluationResponse> {
  try {
    const { data } = await httpClient.post<SentenceEvaluationResponse>(
      `/v1/sentence-versions/${versionId}/evaluations`,
    );

    return data;
  } catch (error) {
    if (axios.isAxiosError(error) && [409, 422].includes(error.response?.status ?? 0)) {
      return getSentenceEvaluation(versionId);
    }

    throw error;
  }
}
