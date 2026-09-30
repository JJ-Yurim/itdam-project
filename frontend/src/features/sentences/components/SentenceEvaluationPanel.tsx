import { useEffect, useRef, useState } from 'react';

import { useQueryClient } from '@tanstack/react-query';

import { archiveKeys } from '@/features/archive';
import { getApiError } from '@/shared/api';
import { ButtonLink } from '@/shared/components';

import {
  evaluationUsageKeys,
  sentenceKeys,
  useEvaluateSentenceMutation,
  useSentenceEvaluationQuery,
  useTodayEvaluationUsageQuery,
} from '../queries';
import {
  cancelScheduledRejectedSentenceDeletion,
  isDiscardableRejectedSentence,
  scheduleRejectedSentenceDeletion,
} from '../rejectedSentenceCleanup';
import type { RejectedSentenceTarget } from '../rejectedSentenceCleanup';
import type { SentenceVersionResponse } from '../types';
import type { SentenceDraftEditorContext } from './SentenceDraftEditor';

import { EvaluationUsageSummary } from './EvaluationUsageSummary';
import { SentenceEvaluationLoading } from './SentenceEvaluationLoading';
import { SentenceEvaluationResult } from './SentenceEvaluationResult';

/** 평가를 보낸 문장. 에디터는 곧바로 비워지므로 결과는 이 스냅샷을 따라간다. */
interface SubmittedSentence {
  versionId: number;
  content: string;
  sentenceId: number | undefined;
  versionNo: number | undefined;
}

export function SentenceEvaluationPanel({
  versionId,
  content,
  matchedWordIds,
  hasUnsavedChanges,
  isSaving,
  sentenceVersion,
  isRevisionMode,
  startRevision,
  cancelRevision,
  saveMode,
  saveDraft,
  detachAfterEvaluation,
  reopenForRevision,
}: SentenceDraftEditorContext) {
  const queryClient = useQueryClient();
  const usageQuery = useTodayEvaluationUsageQuery();
  const evaluateMutation = useEvaluateSentenceMutation();

  // 평가를 보내면 에디터는 새 초안으로 떨어져 나가므로, 결과 추적은 versionId가 아니라
  // 보낸 시점의 스냅샷을 기준으로 한다.
  const [submitted, setSubmitted] = useState<SubmittedSentence | null>(null);

  // 결과를 보여줄 대상은 둘이다. 이 화면에서 방금 보낸 문장이 우선이고, 없으면
  // 서고 등에서 이미 평가된 버전으로 바로 들어온 경우다.
  const openedEvaluatedVersionId =
    sentenceVersion != null && sentenceVersion.evaluationStatus !== 'DRAFT' ? versionId : null;
  const resultVersionId = submitted?.versionId ?? openedEvaluatedVersionId;
  const resultContent = submitted?.content ?? content;

  // 접수 응답(EVALUATING)이든 동기 결과든 mutation으로 먼저 오고, EVALUATING이면
  // 이 재조회가 정상 응답 중에는 1초, 조회 실패 후에는 2초 간격으로
  // 최종 상태(EVALUATED·REJECTED·EVALUATION_FAILED)를 받아 온다.
  const evaluationQuery = useSentenceEvaluationQuery(resultVersionId, resultVersionId !== null);
  const evaluation =
    evaluationQuery.data ??
    (evaluateMutation.data?.sentenceVersionId === resultVersionId
      ? evaluateMutation.data
      : undefined);
  const remainingCount = usageQuery.data?.remainingCount ?? 0;
  const isRejectedResult = evaluation?.evaluationStatus === 'REJECTED';
  const isFailedResult = evaluation?.evaluationStatus === 'EVALUATION_FAILED';

  // 평가 요청 가능 여부는 에디터가 지금 붙들고 있는 버전만으로 판정한다.
  // 방금 보낸 문장의 상태가 여기 섞이면 다음 문장을 못 보낸다.
  const currentStatus = sentenceVersion?.evaluationStatus;
  const canRequestEvaluation =
    currentStatus === undefined
      ? versionId === null || isRevisionMode
      : currentStatus === 'DRAFT' ||
        currentStatus === 'EVALUATION_FAILED' ||
        (isRevisionMode && hasUnsavedChanges);

  const handleEvaluate = async () => {
    let targetVersionId = versionId;

    // 저장되지 않은 변경(또는 첫 저장)이 있으면 먼저 저장한다.
    if (targetVersionId === null || hasUnsavedChanges) {
      targetVersionId = await saveDraft();
    }

    if (targetVersionId === null) {
      // 저장 실패·빈 내용은 에디터의 저장 상태 문구가 안내한다.
      return;
    }

    // 거부 문장 자동 삭제 판정에 sentenceId·versionNo가 필요한데, 에디터를 비우고 나면
    // 컨텍스트에서 사라진다. 저장이 채워 둔 캐시에서 지금 읽어 스냅샷에 담는다.
    const saved = queryClient.getQueryData<SentenceVersionResponse>(
      sentenceKeys.version(targetVersionId),
    );
    const evaluatedContent = content;

    evaluateMutation.mutate(targetVersionId, {
      // 성공한 뒤에만 결과 대상으로 잡는다. 보내기 전에 잡으면 아직 평가되지 않은
      // 버전에 평가 조회가 나가고, 그 응답이 버전 상태를 덮어써 에디터가 잠긴다.
      // 평가를 마친 버전은 더 이상 DRAFT가 아니므로 에디터를 떼어내 다음 문장을
      // 곧바로 쓸 수 있게 한다. 실패했을 때는 비우지 않는다. 쓰던 글이 화면에서
      // 사라지면 다시 시도할 방법이 없어 보인다.
      onSuccess: () => {
        setSubmitted({
          versionId: targetVersionId,
          content: evaluatedContent,
          sentenceId: saved?.sentenceId,
          versionNo: saved?.versionNo,
        });
        detachAfterEvaluation(evaluatedContent);
      },
    });
  };

  // 거부된 문장을 고쳐 쓰면 같은 문장의 다음 버전이 된다.
  // 방금 보낸 문장은 에디터에서 떨어져 나갔으므로 다시 열어야 하고,
  // 이미 그 버전을 열고 있었다면 수정 모드로 들어가기만 하면 된다.
  const reviseRejected = () => {
    evaluateMutation.reset();

    if (!submitted) {
      startRevision();
      return;
    }

    if (submitted.sentenceId !== undefined) {
      cancelScheduledRejectedSentenceDeletion(submitted.sentenceId);
    }

    setSubmitted(null);
    reopenForRevision(submitted.versionId);
  };

  // 접수 후 비동기로 실패한 문장은 이미 에디터에서 떨어져 나가 있다. EVALUATION_FAILED는
  // 같은 버전으로 재접수가 허용되므로 스냅샷을 유지한 채 바로 다시 요청한다.
  const retryFailedEvaluation = () => {
    if (!submitted) {
      return;
    }

    evaluateMutation.mutate(submitted.versionId);
  };

  // 거부된 문장을 수정하지 않고 화면을 떠나면 서비스에 남기지 않고 삭제한다.
  // 판정·예약·취소는 rejectedSentenceCleanup 모듈이 화면 공통으로 관리한다.
  // 값은 ref로 추적해 실제 언마운트 시점의 최신 상태로만 판단한다.
  const rejectedCleanupRef = useRef<RejectedSentenceTarget | null>(null);

  useEffect(() => {
    // 방금 보낸 문장과 이미 열려 있던 버전 중 지금 결과를 보여주는 쪽을 대상으로 한다.
    const target = submitted
      ? {
          sentenceId: submitted.sentenceId,
          versionId: submitted.versionId,
          versionNo: submitted.versionNo,
        }
      : sentenceVersion != null && !isRevisionMode
        ? {
            sentenceId: sentenceVersion.sentenceId,
            versionId: sentenceVersion.sentenceVersionId,
            versionNo: sentenceVersion.versionNo,
          }
        : null;

    rejectedCleanupRef.current =
      target?.sentenceId !== undefined &&
      isDiscardableRejectedSentence({
        evaluationStatus: evaluation?.evaluationStatus,
        versionNo: target.versionNo,
      })
        ? { sentenceId: target.sentenceId, sentenceVersionId: target.versionId }
        : null;
  }, [evaluation?.evaluationStatus, isRevisionMode, sentenceVersion, submitted]);

  // 같은 문장 화면이 다시 열리면(StrictMode 재마운트, 뒤로 가기 재진입 등)
  // 이전 인스턴스가 예약해 둔 삭제를 취소한다.
  const currentSentenceId = sentenceVersion?.sentenceId;
  useEffect(() => {
    if (currentSentenceId !== undefined) {
      cancelScheduledRejectedSentenceDeletion(currentSentenceId);
    }
  }, [currentSentenceId]);

  useEffect(
    () => () => {
      const target = rejectedCleanupRef.current;

      if (target) {
        scheduleRejectedSentenceDeletion(target, queryClient);
      }
    },
    [queryClient],
  );

  // 재조회로 확인한 평가 상태를 버전 캐시에도 반영해 두 캐시의 상태를 일치시킨다.
  const refetchedStatus = evaluationQuery.data?.evaluationStatus;
  const submittedVersionId = submitted?.versionId;
  useEffect(() => {
    if (submittedVersionId === undefined || refetchedStatus === undefined) {
      return;
    }

    queryClient.setQueryData<SentenceVersionResponse>(
      sentenceKeys.version(submittedVersionId),
      (version) =>
        version && version.evaluationStatus !== refetchedStatus
          ? { ...version, evaluationStatus: refetchedStatus }
          : version,
    );

    // 접수 후 폴링으로 종결된 경우 사용량·서고는 접수 시점 값이라 낡아 있다.
    // 특히 실패는 서버가 평가권을 반환하므로 남은 횟수를 다시 읽어야 한다.
    if (refetchedStatus !== 'EVALUATING') {
      void queryClient.invalidateQueries({ queryKey: evaluationUsageKeys.today() });
      void queryClient.invalidateQueries({ queryKey: archiveKeys.all });
    }
  }, [queryClient, refetchedStatus, submittedVersionId]);

  return (
    <div className="space-y-4">
      {usageQuery.isPending && (
        <p role="status" className="text-center text-sm text-stone-500">
          AI 평가 이용 가능 횟수를 확인하고 있어요.
        </p>
      )}

      {usageQuery.data && <EvaluationUsageSummary usage={usageQuery.data} />}

      {evaluateMutation.isPending && <SentenceEvaluationLoading />}

      {evaluateMutation.isError && (
        <section role="alert" className="rounded-2xl border border-red-200 bg-red-50 p-4">
          <p className="text-sm text-red-800">
            {getApiError(evaluateMutation.error).code === 'RATE_LIMITED'
              ? '오늘 사용할 수 있는 AI 평가 횟수를 모두 사용했어요.'
              : 'AI 평가를 완료하지 못했어요. 평가 횟수는 차감되지 않으며 다시 시도할 수 있어요.'}
          </p>
        </section>
      )}

      {/* 결과는 팝업 대신 이 자리에 남긴다. 글을 쓰는 도중 팝업이 뜨면 입력이 끊기고,
          어느 문장의 결과인지도 함께 보여야 여러 건을 이어 보낼 때 헷갈리지 않는다. */}
      {evaluation && (
        <section aria-live="polite" className="motion-rise space-y-3">
          {submitted && (
            <p className="rounded-xl bg-stone-50 px-4 py-3 text-sm leading-6 text-stone-600">
              <span className="font-semibold text-stone-800">평가한 문장 · </span>
              {resultContent}
            </p>
          )}

          <SentenceEvaluationResult evaluation={evaluation} />

          {/* 평가를 마친 문장은 서고에 보관되고 원정에 쓸 수 있다. 결과만 보여주고 끝내면
              다음에 무엇을 할 수 있는지 알 수 없으므로 이어지는 행동을 바로 제시한다. */}
          {evaluation.evaluationStatus === 'EVALUATED' && (
            <div className="grid grid-cols-2 gap-2">
              <ButtonLink to="/archive" variant="secondary" className="text-sm">
                내 서고에서 보기
              </ButtonLink>
              <ButtonLink to="/boss" className="text-sm">
                원정에 참여하기
              </ButtonLink>
            </div>
          )}

          {isRejectedResult && !isRevisionMode && (
            <>
              <p className="text-sm leading-6 text-red-700">
                문장을 고쳐 쓰면 다시 평가를 요청할 수 있어요.
              </p>
              <button
                type="button"
                onClick={reviseRejected}
                className="min-h-12 w-full rounded-xl border border-violet-300 bg-white px-4 py-3 font-semibold text-violet-800 transition hover:bg-violet-50"
              >
                이 문장 고쳐 쓰기
              </button>
            </>
          )}

          {isFailedResult && submitted && (
            <button
              type="button"
              onClick={retryFailedEvaluation}
              disabled={evaluateMutation.isPending || remainingCount === 0}
              className="min-h-12 w-full rounded-xl border border-violet-300 bg-white px-4 py-3 font-semibold text-violet-800 transition hover:bg-violet-50 disabled:cursor-not-allowed disabled:border-stone-200 disabled:text-stone-400"
            >
              이 문장 다시 평가 요청
            </button>
          )}
        </section>
      )}

      {isRevisionMode && (
        <div className="flex items-center justify-between gap-3 rounded-xl bg-amber-50 px-4 py-3">
          <p role="status" className="text-sm text-amber-900">
            {saveMode === 'manual'
              ? '문장을 수정한 뒤 다시 평가를 요청하거나 미평가 문장 저장을 누를 수 있어요.'
              : '문장을 수정해 주세요. 입력을 멈추면 새로운 초안 버전으로 자동 저장됩니다.'}
          </p>
          <button
            type="button"
            onClick={cancelRevision}
            className="shrink-0 text-sm font-semibold text-amber-900 underline underline-offset-2"
          >
            수정 취소
          </button>
        </div>
      )}

      <div>
        <button
          type="button"
          onClick={() => void handleEvaluate()}
          disabled={
            content.trim().length === 0 ||
            isSaving ||
            evaluateMutation.isPending ||
            usageQuery.isPending ||
            usageQuery.isError ||
            remainingCount === 0 ||
            !canRequestEvaluation
          }
          aria-describedby="evaluation-help"
          className="min-h-12 w-full rounded-xl bg-gradient-to-br from-violet-500 to-violet-700 px-4 py-3 font-semibold text-white transition hover:from-violet-600 hover:to-violet-800 disabled:cursor-not-allowed disabled:from-stone-300 disabled:to-stone-300"
        >
          {isSaving || evaluateMutation.isPending
            ? '평가 중…'
            : currentStatus === 'EVALUATION_FAILED'
              ? '✨ AI 평가 다시 요청'
              : '✨ 문장 분석 및 AI 평가 요청'}
        </button>
        {/* 사용 단어 수는 백엔드에서도 차단 조건이 아니라 보너스 점수에만 반영된다.
            막지 않고 많이 쓸수록 유리하다는 것만 알려 준다. */}
        <p id="evaluation-help" className="mt-2 text-center text-xs text-stone-500">
          {matchedWordIds.size === 0
            ? '오늘의 단어를 쓰면 점수가 더 올라가요.'
            : '평가를 요청하면 문장이 함께 저장돼요.'}
        </p>

        {saveMode === 'manual' && (
          <button
            type="button"
            onClick={() => void saveDraft()}
            disabled={isSaving || content.trim().length === 0 || !hasUnsavedChanges}
            className="mt-3 flex min-h-12 w-full items-center justify-center rounded-xl border border-violet-300 bg-white px-4 py-3 font-semibold text-violet-800 transition hover:bg-violet-50 disabled:cursor-not-allowed disabled:border-stone-200 disabled:text-stone-400"
          >
            {isSaving ? '저장 중…' : '📚 미평가 문장 저장'}
          </button>
        )}
      </div>
    </div>
  );
}
