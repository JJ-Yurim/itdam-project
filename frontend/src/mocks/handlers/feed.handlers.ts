import { HttpResponse, http } from 'msw';

interface FeedItemSeed {
  bookContentId: number;
  content: string;
  authorNickname: string | null;
  bookId: number;
  bossName: string;
  likeCount: number;
}

const feedItemSeeds: FeedItemSeed[] = [
  {
    bookContentId: 101,
    content: '안개가 낮게 깔린 숲에서 첫 번째 문장이 조용히 빛났다.',
    authorNickname: '문장여행자',
    bookId: 18,
    bossName: '안개의 수호자',
    likeCount: 7,
  },
  {
    bookContentId: 201,
    content: '기억의 문장은 오래된 종이 냄새를 따라 천천히 돌아왔다.',
    authorNickname: null,
    bookId: 19,
    bossName: '기억의 파수꾼',
    likeCount: 3,
  },
];

const likedContentIds = new Set<number>();

export function resetFeedMockState() {
  likedContentIds.clear();
}

export const feedHandlers = [
  http.get('*/api/v1/sentences/feed', () =>
    HttpResponse.json({
      weekStartDate: '2026-08-03',
      items: feedItemSeeds.map((seed) => ({
        ...seed,
        likeCount: seed.likeCount + (likedContentIds.has(seed.bookContentId) ? 1 : 0),
        liked: likedContentIds.has(seed.bookContentId),
      })),
    }),
  ),

  http.put('*/api/v1/book-contents/:bookContentId/like', ({ params }) => {
    likedContentIds.add(Number(params.bookContentId));
    return new HttpResponse(null, { status: 204 });
  }),

  http.delete('*/api/v1/book-contents/:bookContentId/like', ({ params }) => {
    likedContentIds.delete(Number(params.bookContentId));
    return new HttpResponse(null, { status: 204 });
  }),
];
