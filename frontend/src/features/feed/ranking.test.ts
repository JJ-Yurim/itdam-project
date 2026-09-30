import { describe, expect, it } from 'vitest';

import { BEST_SENTENCE_LIMIT, rankBestSentences } from './ranking';

import type { WeeklySentenceFeedItem } from './types';

function createItem(
  bookContentId: number,
  likeCount: number,
  content = `문장 ${bookContentId}`,
): WeeklySentenceFeedItem {
  return {
    bookContentId,
    content,
    authorNickname: '작가',
    bookId: 1,
    bossName: '보스',
    likeCount,
    liked: false,
  };
}

describe('rankBestSentences', () => {
  it('좋아요가 많은 순으로 순위를 매긴다', () => {
    const ranked = rankBestSentences([
      createItem(1, 3),
      createItem(2, 10),
      createItem(3, 7),
    ]);

    expect(ranked.map(({ rank, item }) => [rank, item.bookContentId])).toEqual([
      [1, 2],
      [2, 3],
      [3, 1],
    ]);
  });

  it('좋아요 수가 같으면 받은 순서를 유지한다', () => {
    const ranked = rankBestSentences([createItem(1, 5), createItem(2, 5), createItem(3, 5)]);

    expect(ranked.map(({ item }) => item.bookContentId)).toEqual([1, 2, 3]);
  });

  it('기본값으로 상위 10개만 남긴다', () => {
    const items = Array.from({ length: 20 }, (_, index) => createItem(index + 1, 20 - index));

    const ranked = rankBestSentences(items);

    expect(ranked).toHaveLength(BEST_SENTENCE_LIMIT);
    expect(ranked.at(-1)?.item.bookContentId).toBe(10);
  });

  it('항목이 10개보다 적으면 있는 만큼만 돌려준다', () => {
    expect(rankBestSentences([createItem(1, 1), createItem(2, 2)])).toHaveLength(2);
  });

  it('원본 배열을 바꾸지 않는다', () => {
    const items = [createItem(1, 1), createItem(2, 9)];

    rankBestSentences(items);

    expect(items.map((item) => item.bookContentId)).toEqual([1, 2]);
  });
});
