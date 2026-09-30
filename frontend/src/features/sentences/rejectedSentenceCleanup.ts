import { deleteSentence } from './api';
import { sentenceKeys } from './queries';

import type { QueryClient } from '@tanstack/react-query';

// 거부(REJECTED) 문장 폐기 정책을 한곳에서 관리한다.
// 판정: 거부됐고 그 문장의 유일한 버전(versionNo=1)일 때만 폐기한다.
// 이전 버전이 있는 문장은 평가 완료된 과거 버전까지 지워지므로 폐기하지 않는다.
export function isDiscardableRejectedSentence(params: {
  evaluationStatus: string | undefined;
  versionNo: number | undefined;
}): boolean {
  return params.evaluationStatus === 'REJECTED' && params.versionNo === 1;
}

export interface RejectedSentenceTarget {
  sentenceId: number;
  sentenceVersionId: number;
}

// 삭제 후에는 남아 있는 버전·평가 캐시를 제거하고 서고 목록을 갱신한다.
// 삭제 실패는 조용히 무시한다 — REJECTED는 어느 목록에도 노출되지 않아 사용자 경험은 동일하다.
function discardRejectedSentence(target: RejectedSentenceTarget, queryClient: QueryClient): void {
  deleteSentence(target.sentenceId)
    .then(() => {
      queryClient.removeQueries({ queryKey: sentenceKeys.version(target.sentenceVersionId) });
      queryClient.removeQueries({ queryKey: sentenceKeys.evaluation(target.sentenceVersionId) });
      void queryClient.invalidateQueries({ queryKey: ['archive'] });
    })
    .catch(() => {});
}

// 예약은 컴포넌트 인스턴스가 아니라 모듈 스코프에서 sentenceId 기준으로 관리한다.
// 실제 언마운트 후 같은 문장 화면이 새 인스턴스로 다시 열려도(뒤로 가기 등)
// 마운트 시 취소가 가능해, 사용자가 보고 있는 문장이 삭제되는 일을 막는다.
const pendingDeletionTimers = new Map<number, number>();

export function cancelScheduledRejectedSentenceDeletion(sentenceId: number): void {
  const timerId = pendingDeletionTimers.get(sentenceId);

  if (timerId !== undefined) {
    window.clearTimeout(timerId);
    pendingDeletionTimers.delete(sentenceId);
  }
}

// 매크로태스크로 예약해 StrictMode의 가짜 언마운트-재마운트에서 취소될 여지를 둔다.
export function scheduleRejectedSentenceDeletion(
  target: RejectedSentenceTarget,
  queryClient: QueryClient,
): void {
  cancelScheduledRejectedSentenceDeletion(target.sentenceId);

  const timerId = window.setTimeout(() => {
    pendingDeletionTimers.delete(target.sentenceId);
    discardRejectedSentence(target, queryClient);
  }, 0);

  pendingDeletionTimers.set(target.sentenceId, timerId);
}

// 팝업 닫기처럼 이탈 의사가 확정된 시점에는 예약 없이 즉시 폐기한다.
export function discardRejectedSentenceNow(
  target: RejectedSentenceTarget,
  queryClient: QueryClient,
): void {
  cancelScheduledRejectedSentenceDeletion(target.sentenceId);
  discardRejectedSentence(target, queryClient);
}
