// 주간 선정 문장 피드 (GET /api/v1/sentences/feed)
export interface WeeklySentenceFeedItem {
  /** 좋아요 등록·해제에 사용하는 책 콘텐츠 식별자 */
  bookContentId: number;
  content: string;
  /** 탈퇴한 작성자는 null */
  authorNickname: string | null;
  bookId: number;
  bossName: string;
  likeCount: number;
  liked: boolean;
}

export interface WeeklySentenceFeedResponse {
  /** 피드가 노출되는 주의 시작일(월요일), YYYY-MM-DD */
  weekStartDate: string;
  items: WeeklySentenceFeedItem[];
}
