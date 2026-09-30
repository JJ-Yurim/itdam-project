import { describe, expect, it } from 'vitest';

import { initialTutorialState, tutorialReducer } from './tutorialReducer';
import { TUTORIAL_STEP_COUNT, TUTORIAL_STEP_LABELS } from './steps';

describe('tutorialReducer', () => {
  it('단계 개수는 진행 라벨의 단일 원본에서 파생한다', () => {
    expect(TUTORIAL_STEP_COUNT).toBe(TUTORIAL_STEP_LABELS.length);
  });

  it('단어 공개부터 평가와 공격까지 결정적인 상태로 진행한다', () => {
    let state = tutorialReducer(initialTutorialState, { type: 'START' });
    state = tutorialReducer(state, { type: 'REVEAL_WORD', wordId: 101 });
    state = tutorialReducer(state, { type: 'REVEAL_WORD', wordId: 101 });
    state = tutorialReducer(state, { type: 'FILL_EXAMPLE' });
    state = tutorialReducer(state, { type: 'START_EVALUATION' });
    state = tutorialReducer(state, { type: 'COMPLETE_EVALUATION' });
    state = tutorialReducer(state, { type: 'SELECT_SENTENCE', sentenceVersionId: 8400 });
    state = tutorialReducer(state, { type: 'START_ATTACK' });
    state = tutorialReducer(state, { type: 'COMPLETE_ATTACK' });

    expect(state.step).toBe(1);
    expect(state.revealedWordIds).toEqual([101]);
    expect(state.sentence).toContain('은하수와 윤슬');
    expect(state.evaluationStatus).toBe('complete');
    expect(state.selectedSentenceVersionId).toBe(8400);
    expect(state.attackStatus).toBe('complete');
  });

  it('초기화하면 모든 진행 상태를 지운다', () => {
    const progressedState = {
      ...initialTutorialState,
      step: 5,
      revealedWordIds: [101, 103],
      sentence: '완성된 문장',
      evaluationStatus: 'complete' as const,
      selectedSentenceVersionId: 8400,
      attackStatus: 'complete' as const,
    };

    expect(tutorialReducer(progressedState, { type: 'RESET' })).toEqual(initialTutorialState);
  });
});
