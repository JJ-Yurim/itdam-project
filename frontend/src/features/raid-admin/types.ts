import type { PageResponse } from '@/shared/api';

export type BossStatus = 'ACTIVE' | 'INACTIVE';
export type BossRunStatus =
  | 'SCHEDULED'
  | 'ACTIVE'
  | 'CLEARED'
  | 'ENDED_UNCLEARED'
  | 'CLOSED'
  | 'CANCELLED';
export type BookStatus = 'GENERATING' | 'COMPLETED' | 'FAILED';
export type BookVisibility = 'PUBLIC' | 'HIDDEN';
export type BossPhaseStatus = 'WAITING' | 'ACTIVE' | 'CLEARED';
export type MoodRole = 'THEME' | 'WEAKNESS';

export interface AdminBossMood {
  code: string;
  displayName: string;
  role: MoodRole;
  weight: number;
}

export interface AdminBoss {
  id: number;
  name: string;
  description: string;
  status: BossStatus;
  bookCoverImageAssetId: number | null;
  bookCoverImageUrl: string | null;
  moods: AdminBossMood[];
  phaseTemplates: BossPhaseTemplate[];
  createdAt: string;
  /** 동시 수정 감지용. 수정 요청의 expectedUpdatedAt으로 되돌려 보낸다. */
  updatedAt: string;
}

export interface AdminBossFilters {
  query?: string;
  status?: BossStatus;
  page?: number;
  size?: number;
}

export interface AdminBossMoodRequest {
  code: string;
  role: MoodRole;
  weight: number;
}

export interface AdminBossUpdateRequest {
  name: string;
  description: string;
  moods: AdminBossMoodRequest[];
  expectedUpdatedAt: string;
}

/**
 * 페이즈 템플릿은 전체 교체다. 서버는 새로 지정하는 이미지를 관리자 본인이 방금 올린
 * TEMPORARY 자산으로만 받으므로, 저장할 때는 모든 페이즈의 이미지를 다시 올려야 한다.
 */
export interface AdminBossPhaseTemplatesUpdateRequest {
  phaseTemplates: BossPhaseRequest[];
  expectedUpdatedAt: string;
}

export interface AdminBossStatusUpdateRequest {
  status: BossStatus;
  expectedUpdatedAt: string;
}

export interface AdminBossCreateRequest {
  name: string;
  description: string;
  /** 이 보스로 만들어지는 책의 기본 표지. 서버가 필수로 요구한다. */
  bookCoverImageAssetId: number;
  moods: Array<{ code: string; role: MoodRole; weight: number }>;
  phaseTemplates: BossPhaseRequest[];
}

export interface BossPhaseRequest {
  maxHp?: number;
  hardMinScore?: number;
  recommendedScore?: number;
  damageMultiplier?: number;
  imageAssetId: number;
}

export interface BossPhaseTemplate {
  id: number;
  phaseNo: number;
  maxHp: number;
  hardMinScore: number | null;
  recommendedScore: number | null;
  damageMultiplier: number;
  imageAssetId: number;
  imageUrl: string;
}

export interface AdminBookCoverResponse {
  bookId: number;
  coverImageAssetId: number;
  coverImageUrl: string;
  /** 표지 교체도 책 행을 수정하므로 이어지는 요청의 expectedUpdatedAt으로 이 값을 써야 한다. */
  updatedAt: string;
}

export interface BossImageUploadResponse {
  imageAssetId: number;
  imageUrl: string;
}

export interface AdminBossPhase {
  phaseNo: number;
  maxHp: number;
  remainingHp: number;
  hardMinScore: number | null;
  recommendedScore: number | null;
  damageMultiplier: number;
  imageAssetId: number | null;
  imageUrl: string | null;
  status: BossPhaseStatus;
}

export interface AdminBossRun {
  id: number;
  bossId: number;
  bossName: string;
  status: BossRunStatus;
  currentPhaseNo: number;
  startedAt: string | null;
  endsAt: string;
  phases: AdminBossPhase[];
  createdAt: string;
  /** 동시 수정 감지용. 수정·상태 전환 요청의 expectedUpdatedAt으로 되돌려 보낸다. */
  updatedAt: string;
}

export interface AdminBossRunFilters {
  query?: string;
  /** 서버는 status 파라미터를 반복 전달받아 여러 상태를 함께 필터한다. */
  statuses?: BossRunStatus[];
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

/** 예정 회차는 전체 설정을, 진행 중 회차는 endsAt만 보낸다. */
export interface AdminBossRunUpdateRequest {
  bossId?: number;
  startsAt?: string;
  endsAt: string;
  phases?: BossPhaseRequest[];
  expectedUpdatedAt: string;
}

export interface AdminBossRunActionRequest {
  expectedUpdatedAt: string;
}

export interface AdminBossPhaseImageUpdateRequest {
  imageAssetId: number;
  expectedUpdatedAt: string;
}

export interface AdminBook {
  bookId: number;
  bossRunId: number;
  bossName: string;
  status: BookStatus;
  visibility: BookVisibility;
  coverImageAssetId: number | null;
  coverImageUrl: string | null;
  completedAt: string | null;
  contentCount: number;
  updatedAt: string;
}

export interface AdminBookFilters {
  query?: string;
  statuses?: BookStatus[];
  visibility?: BookVisibility;
  page?: number;
  size?: number;
}

export interface AdminBookVisibilityUpdateRequest {
  visibility: BookVisibility;
  expectedUpdatedAt: string;
}

export interface AdminBossRunCreateRequest {
  bossId: number;
  startsAt?: string;
  endsAt: string;
  activate: boolean;
  phases: BossPhaseRequest[];
}

export type AdminBossPage = PageResponse<AdminBoss>;
export type AdminBossRunPage = PageResponse<AdminBossRun>;
export type AdminBookPage = PageResponse<AdminBook>;
