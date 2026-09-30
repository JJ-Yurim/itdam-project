import type { PageResponse } from '@/shared/api';

export type BossRunStatus = 'SCHEDULED' | 'ACTIVE' | 'CLEARED' | 'ENDED_UNCLEARED' | 'CLOSED';

export type BossPhaseStatus = 'WAITING' | 'ACTIVE' | 'CLEARED';

export type MoodRole = 'THEME' | 'WEAKNESS';

export interface BossPhaseResponse {
  phaseId: number;
  phaseNo: number;
  maxHp: number;
  remainingHp: number;
  progressPercent: number;
  hardMinScore: number | null;
  recommendedScore: number | null;
  damageMultiplier: number;
  /** 현재 페이즈 보스 이미지의 공개 URL. 이미지가 없는 회차는 null이다. */
  imageUrl?: string | null;
  status: BossPhaseStatus;
}

export interface BossMoodResponse {
  code: string;
  displayName: string;
  role: MoodRole;
  weight: number;
}

export interface BossSubmissionSummary {
  submissionId: number;
  bossRunId: number;
  bossName: string;
  bossPhaseId: number;
  sentenceVersionId: number;
  content: string;
  finalDamage: number;
  submittedAt: string;
}

export interface BossRunResponse {
  bossRunId: number;
  bossId: number;
  bossName: string;
  bossDescription: string;
  status: BossRunStatus;
  startedAt: string;
  endsAt: string;
  clearedAt: string | null;
  accumulatedDamage: number;
  currentPhase: BossPhaseResponse;
  moods: BossMoodResponse[];
  mySubmissionCount: number;
  remainingSubmissionCount: number;
  mySubmissions: BossSubmissionSummary[];
}

/**
 * 목록 한 줄에 필요한 만큼만 담긴 응답.
 *
 * 상세와 나뉜 이유는 제출 이력이다. 상세는 내 제출 문장을 원문까지 내려주는데, 목록이
 * 같은 모양을 쓰면 목록 한 페이지를 여는 것만으로 모든 회차의 제출 전문을 받게 된다.
 */
export interface BossRunSummaryResponse {
  bossRunId: number;
  bossId: number;
  bossName: string;
  status: BossRunStatus;
  startedAt: string;
  endsAt: string;
  clearedAt: string | null;
  accumulatedDamage: number;
  currentPhase: BossPhaseResponse;
  moods: BossMoodResponse[];
  mySubmissionCount: number;
  remainingSubmissionCount: number;
}

export type BossRunPageResponse<T> = PageResponse<T>;

export interface BossRunListParams {
  /** 여러 개를 보내면 그 상태를 모두 조회한다. 생략하면 전체다. */
  status?: BossRunStatus[];
  page?: number;
  size?: number;
}

export interface EligibleSentenceResponse {
  sentenceVersionId: number;
  sentenceId: number;
  versionNo: number;
  content: string;
  baseScore: number;
  wordBonus: number;
  gameBaseAttack: number;
  /** 현재 보스 보너스와 페이즈 배율까지 반영한 예상 최종 피해 */
  sentenceEnergy: number;
}

export interface EligibleSentenceListParams {
  page?: number;
  size?: number;
}

export interface BossSubmissionRequest {
  sentenceVersionId: number;
  submissionConsentAccepted: true;
}

export interface BossSubmissionDamage {
  aiBaseScore: number;
  wordBonus: number;
  moodBonus: number;
  weaknessBonus: number;
  phaseModifier: number;
  finalDamage: number;
}

export interface BossSubmissionPhase {
  phaseNo: number;
  remainingHp: number;
  status: BossPhaseStatus;
}

export interface BossSubmissionResponse {
  submissionId: number;
  bossRunId: number;
  bossPhaseId: number;
  serviceDate: string;
  damage: BossSubmissionDamage;
  phase: BossSubmissionPhase;
  bossRunStatus: BossRunStatus;
}

// 내가 문장을 제출해 참여한 보스런 이력 항목
export interface MyBossRunResponse {
  bossRunId: number;
  bossId: number;
  bossName: string;
  status: BossRunStatus;
  currentPhaseNo: number;
  startedAt: string | null;
  endsAt: string;
  clearedAt: string | null;
  lastSubmittedAt: string;
  bookId: number | null;
  // 책 행이 있으면 상태와 무관하게 bookId가 채워지므로, 열람 가능 여부는 이 상태로 판정한다.
  bookStatus: 'GENERATING' | 'COMPLETED' | 'FAILED' | null;
}

export interface MyBossRunListParams {
  status?: BossRunStatus;
  page?: number;
  size?: number;
}

// 회차 기여도 리더보드. entries와 SSE leaderboard 이벤트가 같은 항목 형태를 공유한다.
export interface BossRunLeaderboardEntry {
  rank: number;
  userId: number;
  nickname: string;
  totalDamage: number;
}

export interface BossRunLeaderboardMyRank {
  rank: number;
  totalDamage: number;
}

export interface BossRunLeaderboardResponse {
  bossRunId: number;
  entries: BossRunLeaderboardEntry[];
  /** 요청자가 아직 제출하지 않았으면 null이다. */
  me: BossRunLeaderboardMyRank | null;
}
