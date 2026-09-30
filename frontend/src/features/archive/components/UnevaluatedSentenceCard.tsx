import { useEffect, useRef, useState } from 'react';

import { Link } from 'react-router-dom';

import { useQueryClient } from '@tanstack/react-query';

import type { SentenceEvaluationResponse } from '@/features/sentences';
import {
  evaluationUsageKeys,
  MAX_EVALUATION_POLL_FAILURES,
  sentenceKeys,
  useEvaluateSentenceMutation,
  useSentenceEvaluationQuery,
} from '@/features/sentences';
import type { SentenceVersionResponse } from '@/features/sentences';
import { Badge, Button, Modal, useToast } from '@/shared/components';

import { archiveKeys, useDeleteArchiveSentenceMutation } from '../queries';
import { formatArchiveDate } from '../format';
import type { UnevaluatedArchiveSentence } from '../types';

const EVALUATION_POLL_MAX_DURATION_MS = 60_000;

interface UnevaluatedSentenceCardProps {
  sentence: UnevaluatedArchiveSentence;
  /** 평가 완료 시 호출. 결과 팝업은 목록 갱신으로 카드가 사라져도 유지되도록 상위에서 렌더링한다. */
  onEvaluated: (
    sentence: UnevaluatedArchiveSentence,
    evaluation: SentenceEvaluationResponse,
  ) => void;
}

export function UnevaluatedSentenceCard({ sentence, onEvaluated }: UnevaluatedSentenceCardProps) {
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [requestedVersionId, setRequestedVersionId] = useState<number | null>(null);
  const [pollingAttempt, setPollingAttempt] = useState(0);
  const [timedOutPollingSession, setTimedOutPollingSession] = useState<string | null>(null);
  const submittedSentenceRef = useRef(sentence);
  const reportedVersionRef = useRef<number | null>(null);
  const queryClient = useQueryClient();
  const evaluateMutation = useEvaluateSentenceMutation();
  const trackedVersionId =
    sentence.evaluationStatus === 'EVALUATING' ? sentence.sentenceVersionId : requestedVersionId;
  const pollingSession = trackedVersionId === null ? null : `${trackedVersionId}:${pollingAttempt}`;
  const isPollingTimedOut = pollingSession !== null && timedOutPollingSession === pollingSession;
  const evaluationQuery = useSentenceEvaluationQuery(
    trackedVersionId,
    trackedVersionId !== null && !isPollingTimedOut,
  );
  const deleteMutation = useDeleteArchiveSentenceMutation();
  const { showToast } = useToast();

  const completedEvaluation = evaluationQuery.data;
  const hasFinalEvaluation =
    completedEvaluation !== undefined && completedEvaluation.evaluationStatus !== 'EVALUATING';
  const hasReachedFailureLimit =
    evaluationQuery.failureCount >= MAX_EVALUATION_POLL_FAILURES && !evaluationQuery.isFetching;
  const isPollingExhausted = isPollingTimedOut || hasReachedFailureLimit;

  // 워커 중단처럼 EVALUATING이 계속 성공 응답으로 오는 경우도 있으므로 전체 대기 시간에
  // 상한을 둔다. 시간 초과 후에는 사용자가 재요청·수정·삭제 중 하나를 선택할 수 있다.
  useEffect(() => {
    if (
      pollingSession === null ||
      isPollingTimedOut ||
      hasReachedFailureLimit ||
      hasFinalEvaluation
    ) {
      return;
    }

    const timeoutId = window.setTimeout(() => {
      setTimedOutPollingSession(pollingSession);
    }, EVALUATION_POLL_MAX_DURATION_MS);

    return () => window.clearTimeout(timeoutId);
  }, [hasFinalEvaluation, hasReachedFailureLimit, isPollingTimedOut, pollingSession]);

  // 비동기 접수 응답 뒤에는 평가 조회를 폴링한다. 최종 상태가 오면 관련 캐시를 즉시
  // 갱신해 카드가 미평가 목록에서 사라지고 결과 팝업은 상위 화면에 남도록 한다.
  useEffect(() => {
    if (!completedEvaluation || completedEvaluation.evaluationStatus === 'EVALUATING') {
      return;
    }

    if (reportedVersionRef.current === completedEvaluation.sentenceVersionId) {
      return;
    }
    reportedVersionRef.current = completedEvaluation.sentenceVersionId;

    queryClient.setQueryData<SentenceVersionResponse>(
      sentenceKeys.version(completedEvaluation.sentenceVersionId),
      (version) =>
        version ? { ...version, evaluationStatus: completedEvaluation.evaluationStatus } : version,
    );
    void queryClient.invalidateQueries({ queryKey: evaluationUsageKeys.today() });
    void queryClient.invalidateQueries({ queryKey: archiveKeys.all });
    onEvaluated(submittedSentenceRef.current, completedEvaluation);
  }, [completedEvaluation, onEvaluated, queryClient]);

  const handleEvaluate = () => {
    submittedSentenceRef.current = sentence;
    setPollingAttempt((attempt) => attempt + 1);
    evaluateMutation.mutate(sentence.sentenceVersionId, {
      onSuccess: (evaluation) => {
        if (evaluation.evaluationStatus === 'EVALUATING') {
          reportedVersionRef.current = null;
          setRequestedVersionId(sentence.sentenceVersionId);
          return;
        }

        reportedVersionRef.current = evaluation.sentenceVersionId;
        onEvaluated(sentence, evaluation);
      },
    });
  };

  const isEvaluating =
    evaluateMutation.isPending ||
    (trackedVersionId !== null && !hasFinalEvaluation && !isPollingExhausted);

  return (
    <article className="rounded-2xl border border-stone-200 bg-white p-5 shadow-sm">
      <div className="flex flex-wrap items-center gap-2 text-xs font-semibold">
        <Badge>{isEvaluating ? '평가 중' : isPollingExhausted ? '확인 실패' : '평가 전'}</Badge>
        <span className="text-stone-500">버전 {sentence.versionNo}</span>
        <span className="ml-auto text-stone-500">{formatArchiveDate(sentence.createdAt)}</span>
      </div>

      <p className="mt-4 line-clamp-3 whitespace-pre-wrap break-words text-sm leading-6 text-stone-800">
        {sentence.content}
      </p>

      {(evaluateMutation.isError || evaluationQuery.isError || isPollingTimedOut) && (
        <p role="alert" className="mt-3 text-sm font-semibold text-red-700">
          {evaluateMutation.isError
            ? 'AI 평가를 접수하지 못했어요. 평가 횟수는 차감되지 않으며 다시 시도할 수 있어요.'
            : isPollingExhausted
              ? '평가 상태 확인을 중단했어요. 다시 평가를 요청하거나 문장을 수정·삭제해 주세요.'
              : '평가 상태 확인이 지연되고 있어요. 잠시 후 자동으로 다시 확인할게요.'}
        </p>
      )}

      <div className="mt-4 flex items-center gap-2">
        <button
          type="button"
          onClick={handleEvaluate}
          disabled={isEvaluating || deleteMutation.isPending}
          className="flex min-h-11 flex-1 items-center justify-center rounded-xl bg-gradient-to-br from-violet-500 to-violet-700 px-4 py-2 text-sm font-semibold text-white transition hover:from-violet-600 hover:to-violet-800 disabled:cursor-not-allowed disabled:from-stone-300 disabled:to-stone-300"
        >
          {isEvaluating
            ? '평가 중…'
            : isPollingExhausted
              ? '✨ AI 평가 다시 요청'
              : '✨ AI 평가 요청'}
        </button>

        <Link
          to={`/sentences/new?versionId=${sentence.sentenceVersionId}`}
          aria-disabled={isEvaluating}
          tabIndex={isEvaluating ? -1 : undefined}
          onClick={isEvaluating ? (event) => event.preventDefault() : undefined}
          className={`flex min-h-11 items-center justify-center rounded-xl border border-violet-300 bg-white px-4 py-2 text-sm font-semibold text-violet-700 transition ${
            isEvaluating ? 'pointer-events-none opacity-50' : 'hover:bg-violet-50'
          }`}
        >
          이어 쓰기
        </Link>

        <button
          type="button"
          aria-label={`문장 삭제: ${sentence.content.slice(0, 20)}`}
          onClick={() => setIsDeleteModalOpen(true)}
          disabled={isEvaluating || deleteMutation.isPending}
          className="flex min-h-11 items-center justify-center rounded-xl border border-stone-300 bg-white px-4 py-2 text-sm font-semibold text-stone-600 transition hover:bg-stone-50 disabled:cursor-not-allowed disabled:opacity-50"
        >
          삭제
        </button>
      </div>

      {isDeleteModalOpen && (
        <Modal
          open
          title="문장을 삭제할까요?"
          onClose={() => {
            if (!deleteMutation.isPending) {
              setIsDeleteModalOpen(false);
            }
          }}
          footer={
            <>
              <Button
                variant="secondary"
                disabled={deleteMutation.isPending}
                onClick={() => setIsDeleteModalOpen(false)}
              >
                취소
              </Button>
              <Button
                variant="danger"
                disabled={deleteMutation.isPending}
                onClick={() =>
                  deleteMutation.mutate(
                    {
                      sentenceId: sentence.sentenceId,
                      sentenceVersionId: sentence.sentenceVersionId,
                    },
                    {
                      onSuccess: () => {
                        setIsDeleteModalOpen(false);
                        // 카드가 목록에서 사라지므로 삭제가 완료됐음을 명시적으로 알린다.
                        showToast({ message: '문장을 삭제했어요.' });
                      },
                    },
                  )
                }
              >
                {deleteMutation.isPending ? '삭제 중…' : '삭제하기'}
              </Button>
            </>
          }
        >
          <p className="line-clamp-3 whitespace-pre-wrap rounded-xl bg-stone-50 p-3 text-sm text-stone-700">
            {sentence.content}
          </p>
          <p className="mt-3 text-sm leading-6 text-red-700">
            아직 평가받지 않은 문장이에요. 삭제하면 되돌릴 수 없습니다.
          </p>
          {deleteMutation.isError && (
            <p role="alert" className="mt-3 text-sm font-semibold text-red-700">
              문장을 삭제하지 못했어요. 잠시 후 다시 시도해 주세요.
            </p>
          )}
        </Modal>
      )}
    </article>
  );
}
