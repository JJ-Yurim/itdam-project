import { tutorialExampleSentence } from './data';
import { TUTORIAL_STEP_COUNT } from './steps';

export { TUTORIAL_STEP_COUNT } from './steps';

export type TutorialAsyncStatus = 'idle' | 'loading' | 'complete';

export interface TutorialState {
  step: number;
  revealedWordIds: number[];
  sentence: string;
  evaluationStatus: TutorialAsyncStatus;
  selectedSentenceVersionId: number | null;
  attackStatus: TutorialAsyncStatus;
}

export const initialTutorialState: TutorialState = {
  step: 0,
  revealedWordIds: [],
  sentence: '',
  evaluationStatus: 'idle',
  selectedSentenceVersionId: null,
  attackStatus: 'idle',
};

export type TutorialAction =
  | { type: 'START' }
  | { type: 'REVEAL_WORD'; wordId: number }
  | { type: 'SET_SENTENCE'; sentence: string }
  | { type: 'FILL_EXAMPLE' }
  | { type: 'GO_TO_STEP'; step: number }
  | { type: 'BACK' }
  | { type: 'START_EVALUATION' }
  | { type: 'COMPLETE_EVALUATION' }
  | { type: 'SELECT_SENTENCE'; sentenceVersionId: number | null }
  | { type: 'START_ATTACK' }
  | { type: 'COMPLETE_ATTACK' }
  | { type: 'RESET' };

export function tutorialReducer(state: TutorialState, action: TutorialAction): TutorialState {
  switch (action.type) {
    case 'START':
      return { ...state, step: 1 };
    case 'REVEAL_WORD':
      return state.revealedWordIds.includes(action.wordId)
        ? state
        : { ...state, revealedWordIds: [...state.revealedWordIds, action.wordId] };
    case 'SET_SENTENCE':
      return { ...state, sentence: action.sentence, evaluationStatus: 'idle' };
    case 'FILL_EXAMPLE':
      return { ...state, sentence: tutorialExampleSentence, evaluationStatus: 'idle' };
    case 'GO_TO_STEP':
      return {
        ...state,
        step: Math.min(Math.max(action.step, 0), TUTORIAL_STEP_COUNT - 1),
      };
    case 'BACK':
      return { ...state, step: Math.max(state.step - 1, 0) };
    case 'START_EVALUATION':
      return { ...state, evaluationStatus: 'loading' };
    case 'COMPLETE_EVALUATION':
      return { ...state, evaluationStatus: 'complete' };
    case 'SELECT_SENTENCE':
      return { ...state, selectedSentenceVersionId: action.sentenceVersionId };
    case 'START_ATTACK':
      return { ...state, attackStatus: 'loading' };
    case 'COMPLETE_ATTACK':
      return { ...state, attackStatus: 'complete' };
    case 'RESET':
      return initialTutorialState;
  }
}
