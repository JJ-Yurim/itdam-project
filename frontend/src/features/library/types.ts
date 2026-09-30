export interface LibraryBookSummaryResponse {
  bookId: number;
  bossRunId: number;
  bossName: string;
  coverImageUrl: string | null;
  completedAt: string;
  contentCount: number;
  // 요약 계약 이전에 생성된 책은 null이다.
  summary: string | null;
  likeCount: number;
  liked: boolean;
  bookmarked: boolean;
}

export type BookContentType = 'USER_ORIGINAL' | 'USER_EDITED' | 'AI_GENERATED';

export interface BookContributorResponse {
  userId: number | null;
  nickname: string;
}

export interface BookContributionMoodResponse {
  code: string;
  strength: number;
  rank: number;
}

export interface BookContributionDetailsResponse {
  originalContent: string | null;
  finalDamage: number;
  moods: BookContributionMoodResponse[];
  usedDailyWords: string[];
}

export interface BookContentResponse {
  contentId: number;
  sequenceNo: number;
  contentType: BookContentType;
  content: string;
  contributor: BookContributorResponse | null;
  sourceSubmissionId: number | null;
  contribution: BookContributionDetailsResponse | null;
}

export interface LibraryBookDetailResponse {
  bookId: number;
  bossRunId: number;
  bossName: string;
  coverImageUrl: string | null;
  completedAt: string;
  body: string | null;
  likeCount: number;
  liked: boolean;
  bookmarked: boolean;
  contents: BookContentResponse[];
}

export interface LibraryBookPageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface LibraryBookListParams {
  page?: number;
  size?: number;
}
