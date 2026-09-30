import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { archiveKeys } from '@/features/archive';
import { ALWAYS_REFRESH_ON_MOUNT } from '@/shared/api';

import {
  createSentenceDraft,
  createSentenceVersion,
  evaluateSentenceVersion,
  getSentenceEvaluation,
  getSentenceVersion,
  getTodayEvaluationUsage,
  updateSentenceDraft,
} from './api';

export const evaluationUsageKeys = {
  all: ['evaluation-usage'] as const,
  today: () => [...evaluationUsageKeys.all, 'today'] as const,
};

export const sentenceKeys = {
  all: ['sentences'] as const,
  versions: () => [...sentenceKeys.all, 'versions'] as const,
  version: (versionId: number) => [...sentenceKeys.versions(), versionId] as const,
  evaluations: () => [...sentenceKeys.all, 'evaluations'] as const,
  evaluation: (versionId: number) => [...sentenceKeys.evaluations(), versionId] as const,
};

export function useTodayEvaluationUsageQuery(options: { enabled?: boolean } = {}) {
  const { enabled = true } = options;

  return useQuery({
    queryKey: evaluationUsageKeys.today(),
    queryFn: getTodayEvaluationUsage,
    staleTime: 60 * 1000,
    enabled,
  });
}

export function useSentenceVersionQuery(versionId: number | null) {
  return useQuery({
    queryKey: sentenceKeys.version(versionId ?? 0),
    queryFn: () => getSentenceVersion(versionId as number),
    enabled: versionId !== null && versionId > 0,
    staleTime: 60 * 1000,
  });
}

export function useCreateSentenceDraftMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: createSentenceDraft,
    onSuccess: (draft) => {
      queryClient.setQueryData(sentenceKeys.version(draft.id), draft);
    },
  });
}

export function useCreateSentenceVersionMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ sentenceId, content }: { sentenceId: number; content: string }) =>
      createSentenceVersion(sentenceId, content),
    onSuccess: (draft) => {
      queryClient.setQueryData(sentenceKeys.version(draft.id), draft);
    },
  });
}

export function useUpdateSentenceDraftMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ versionId, content }: { versionId: number; content: string }) =>
      updateSentenceDraft(versionId, content),
    onSuccess: (draft) => {
      queryClient.setQueryData(sentenceKeys.version(draft.id), draft);
    },
  });
}

const EVALUATING_REFETCH_INTERVAL_MS = 1000;
const EVALUATING_ERROR_REFETCH_INTERVAL_MS = 2000;
export const MAX_EVALUATION_POLL_FAILURES = 5;

export function shouldContinueEvaluationPolling(
  evaluationStatus: import('./types').SentenceEvaluationStatus | undefined,
  failureCount: number,
) {
  return (
    (evaluationStatus === undefined || evaluationStatus === 'EVALUATING') &&
    failureCount < MAX_EVALUATION_POLL_FAILURES
  );
}

export function evaluationRefetchIntervalMs(failureCount: number) {
  return failureCount > 0 ? EVALUATING_ERROR_REFETCH_INTERVAL_MS : EVALUATING_REFETCH_INTERVAL_MS;
}

export function useSentenceEvaluationQuery(versionId: number | null, enabled: boolean) {
  return useQuery({
    queryKey: sentenceKeys.evaluation(versionId ?? 0),
    queryFn: () => getSentenceEvaluation(versionId as number),
    enabled: enabled && versionId !== null && versionId > 0,
    ...ALWAYS_REFRESH_ON_MOUNT,
    retry: false,
    // 평가 접수 응답이나 409 충돌 복구로 EVALUATING을 받으면
    // 최종 상태(EVALUATED·REJECTED·EVALUATION_FAILED)가 될 때까지 재조회한다.
    // 일시적 실패는 흡수하되 영구적인 404·403·네트워크 오류에서 무한 요청하지 않도록
    // 연속 실패 상한에 도달하면 멈춘다. 호출 화면은 failureCount로 탈출 UI를 제공한다.
    refetchInterval: (query) =>
      shouldContinueEvaluationPolling(
        query.state.data?.evaluationStatus,
        query.state.fetchFailureCount,
      )
        ? evaluationRefetchIntervalMs(query.state.fetchFailureCount)
        : false,
  });
}

export function useEvaluateSentenceMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: evaluateSentenceVersion,
    onSuccess: (evaluation) => {
      queryClient.setQueryData(sentenceKeys.evaluation(evaluation.sentenceVersionId), evaluation);
      queryClient.setQueryData<import('./types').SentenceVersionResponse>(
        sentenceKeys.version(evaluation.sentenceVersionId),
        (version) =>
          version ? { ...version, evaluationStatus: evaluation.evaluationStatus } : version,
      );
      void queryClient.invalidateQueries({ queryKey: evaluationUsageKeys.today() });
      void queryClient.invalidateQueries({ queryKey: archiveKeys.all });
    },
    onError: () => {
      // 실패해도 서버의 사용량은 달라져 있을 수 있다. 특히 429는 이미 소진된 상태인데
      // 화면이 "남은 횟수"를 그대로 보여주면 사용자가 계속 눌러 계속 실패한다.
      void queryClient.invalidateQueries({ queryKey: evaluationUsageKeys.today() });
    },
  });
}
