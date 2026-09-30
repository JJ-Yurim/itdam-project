import type { WeeklySentenceFeedItem } from './types';

/** 이야기의 전당에 전시할 문장 수 */
export const BEST_SENTENCE_LIMIT = 10;

export interface RankedSentence {
  rank: number;
  item: WeeklySentenceFeedItem;
}

/**
 * 좋아요가 많은 순으로 상위 N개를 고른다.
 *
 * 좋아요 수가 같으면 주간 세트 순서를 그대로 유지한다. 응답이 세트 순서대로 오고
 * Array.prototype.sort는 안정 정렬이라 별도 보조 키가 필요하지 않다.
 */
export function rankBestSentences(
  items: WeeklySentenceFeedItem[],
  limit: number = BEST_SENTENCE_LIMIT,
): RankedSentence[] {
  return [...items]
    .sort((a, b) => b.likeCount - a.likeCount)
    .slice(0, limit)
    .map((item, index) => ({ rank: index + 1, item }));
}
