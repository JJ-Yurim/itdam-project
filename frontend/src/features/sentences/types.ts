export interface EvaluationUsage {
  serviceDate: string;
  usedCount: number;
  limit: number;
  remainingCount: number;
}

export type SentenceEvaluationStatus =
  'DRAFT' | 'EVALUATING' | 'EVALUATED' | 'REJECTED' | 'EVALUATION_FAILED';

export interface SentenceDraftRequest {
  content: string;
}

export interface SentenceVersionResponse {
  id: number;
  sentenceVersionId: number;
  sentenceId: number;
  versionNo: number;
  content: string;
  evaluationStatus: SentenceEvaluationStatus;
  createdAt: string;
  confirmedAt: string | null;
}

export interface SentenceEvaluationSafety {
  status: 'PASSED' | 'REJECTED';
  reason: string | null;
}

export interface SentenceEvaluationScores {
  completeness: number;
  originality: number;
  expression: number;
  imagery: number;
  emotionalImpact: number;
  baseScore: number;
}

export interface SentenceEvaluationMood {
  code: string;
  strength: number;
  rank: number;
}

export interface SentenceEvaluationUsedWord {
  dailyWordSetItemId: number;
  wordId: number;
  word: string;
}

export interface SentenceEvaluationResponse {
  sentenceVersionId: number;
  evaluationStatus: SentenceEvaluationStatus;
  safety: SentenceEvaluationSafety | null;
  scores: SentenceEvaluationScores | null;
  wordBonus: number | null;
  gameBaseAttack: number | null;
  overallFeedback: string | null;
  moods: SentenceEvaluationMood[];
  usedDailyWords: SentenceEvaluationUsedWord[];
  modelName: string | null;
  promptVersion: string | null;
  rubricVersion: string | null;
  evaluatedAt: string | null;
}
