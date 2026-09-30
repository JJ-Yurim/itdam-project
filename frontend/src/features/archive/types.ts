export interface ArchiveMood {
  code: string;
  strength: number;
  rank: number;
}

export interface ArchiveUsedDailyWord {
  dailyWordSetItemId: number;
  wordId: number;
  word: string;
}

export interface ArchiveBossSubmission {
  submissionId: number;
  bossRunId: number;
  bossName: string;
  bossPhaseId: number;
  sentenceVersionId: number;
  finalDamage: number;
  submittedAt: string;
}

export interface ArchiveBookInclusion {
  bookId: number;
  sequenceNo: number;
}

export interface ArchiveSentence {
  sentenceVersionId: number;
  sentenceId: number;
  versionNo: number;
  content: string;
  baseScore: number;
  wordBonus: number;
  gameBaseAttack: number;
  moods: ArchiveMood[];
  usedDailyWords: ArchiveUsedDailyWord[];
  bossSubmissions: ArchiveBossSubmission[];
  bookInclusions: ArchiveBookInclusion[];
  evaluatedAt: string;
}

export interface ArchivePageResponse {
  content: ArchiveSentence[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

// 아직 평가받지 않은 저장 문장. 평가 전이라 점수·분위기 정보가 없다.
export interface UnevaluatedArchiveSentence {
  sentenceVersionId: number;
  sentenceId: number;
  versionNo: number;
  content: string;
  evaluationStatus: 'DRAFT' | 'EVALUATING' | 'EVALUATION_FAILED';
  createdAt: string;
}

export interface UnevaluatedArchivePageResponse {
  content: UnevaluatedArchiveSentence[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface ArchiveEvaluationScores {
  completeness: number;
  originality: number;
  expression: number;
  imagery: number;
  emotionalImpact: number;
  baseScore: number;
}

export interface ArchiveEvaluation {
  sentenceVersionId: number;
  evaluationStatus: string;
  scores: ArchiveEvaluationScores | null;
  wordBonus: number | null;
  gameBaseAttack: number | null;
  overallFeedback: string | null;
}

// 내 문장이 수록된 완성 공동 책 (GET /me/library/books)
// 목록 응답은 공통 PageResponse<MyContributedBook>을 사용한다.
export interface MyContributedBook {
  bookId: number;
  bossRunId: number;
  bossName: string;
  completedAt: string;
  contentCount: number;
  myIncludedContentCount: number;
}
