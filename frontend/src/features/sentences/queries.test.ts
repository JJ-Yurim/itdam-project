import { describe, expect, it } from 'vitest';

import {
  evaluationRefetchIntervalMs,
  MAX_EVALUATION_POLL_FAILURES,
  shouldContinueEvaluationPolling,
} from './queries';

describe('shouldContinueEvaluationPolling', () => {
  it('진행 중이거나 첫 응답 전이면 실패 상한까지 폴링한다', () => {
    expect(shouldContinueEvaluationPolling(undefined, 0)).toBe(true);
    expect(shouldContinueEvaluationPolling('EVALUATING', MAX_EVALUATION_POLL_FAILURES - 1)).toBe(
      true,
    );
  });

  it('최종 상태 또는 연속 실패 상한에서는 폴링을 중단한다', () => {
    expect(shouldContinueEvaluationPolling('EVALUATED', 0)).toBe(false);
    expect(shouldContinueEvaluationPolling('EVALUATING', MAX_EVALUATION_POLL_FAILURES)).toBe(false);
    expect(shouldContinueEvaluationPolling(undefined, MAX_EVALUATION_POLL_FAILURES)).toBe(false);
  });
});

describe('evaluationRefetchIntervalMs', () => {
  it('정상 폴링은 1초, 조회 실패 후 재시도는 2초 간격을 사용한다', () => {
    expect(evaluationRefetchIntervalMs(0)).toBe(1000);
    expect(evaluationRefetchIntervalMs(1)).toBe(2000);
    expect(evaluationRefetchIntervalMs(MAX_EVALUATION_POLL_FAILURES - 1)).toBe(2000);
  });
});
