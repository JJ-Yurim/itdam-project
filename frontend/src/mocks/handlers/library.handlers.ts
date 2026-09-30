import { HttpResponse, http } from 'msw';

import type { LibraryBookDetailResponse, LibraryBookSummaryResponse } from '@/features/library';

interface LibraryBookSeed {
  bookId: number;
  bossRunId: number;
  bossName: string;
  coverImageUrl: string | null;
  completedAt: string;
  contentCount: number;
  summary: string | null;
  likeCount: number;
}

const libraryBookSeeds: LibraryBookSeed[] = [
  {
    bookId: 18,
    bossRunId: 7,
    bossName: '안개의 수호자',
    coverImageUrl: null,
    completedAt: '2026-07-27T10:05:00Z',
    contentCount: 8,
    summary: '안개 낀 숲에서 흩어진 문장들이 모여 하나의 길이 되는 이야기.',
    likeCount: 12,
  },
  {
    bookId: 19,
    bossRunId: 12,
    bossName: '기억의 파수꾼',
    coverImageUrl: null,
    completedAt: '2026-07-29T15:20:00Z',
    contentCount: 11,
    summary: null,
    likeCount: 4,
  },
  {
    bookId: 20,
    bossRunId: 16,
    bossName: '별빛을 삼킨 탑',
    coverImageUrl: null,
    completedAt: '2026-07-31T08:40:00Z',
    contentCount: 6,
    summary: null,
    likeCount: 0,
  },
];

// 좋아요·북마크는 토글 API로 변하는 상태라 시드와 분리해 관리한다.
const likedBookIds = new Set<number>();
const bookmarkedBookIds = new Set<number>([18]);
// 표지도 관리자 API로 바뀌므로 시드와 분리한다.
const replacedCoverUrls = new Map<number, string>();

export function resetLibraryMockState() {
  likedBookIds.clear();
  bookmarkedBookIds.clear();
  bookmarkedBookIds.add(18);
  replacedCoverUrls.clear();
}

/**
 * 관리자 표지 교체 목이 쓰는 접근자. 교체 핸들러는 raid-admin 쪽에 있지만 대상 존재
 * 검증과 결과 반영은 이 파일이 가진 시드로만 할 수 있다.
 */
export function hasLibraryBook(bookId: number) {
  return libraryBookSeeds.some((seed) => seed.bookId === bookId);
}

export function replaceLibraryBookCover(bookId: number, coverImageUrl: string) {
  replacedCoverUrls.set(bookId, coverImageUrl);
}

function toSummaryResponse(seed: LibraryBookSeed): LibraryBookSummaryResponse {
  return {
    ...seed,
    coverImageUrl: replacedCoverUrls.get(seed.bookId) ?? seed.coverImageUrl,
    likeCount: seed.likeCount + (likedBookIds.has(seed.bookId) ? 1 : 0),
    liked: likedBookIds.has(seed.bookId),
    bookmarked: bookmarkedBookIds.has(seed.bookId),
  };
}

function pageResponse(books: LibraryBookSummaryResponse[], page: number, size: number) {
  const startIndex = page * size;
  const content = books.slice(startIndex, startIndex + size);

  return {
    content,
    page,
    size,
    totalElements: books.length,
    totalPages: Math.ceil(books.length / size),
    hasNext: startIndex + size < books.length,
  };
}

const fogGuardianBody = [
  '안개가 낮게 깔린 숲에서 첫 번째 문장이 조용히 빛났다. 새벽의 습기를 머금은 글자는 이끼 위에서 작은 숨을 쉬었다.',
  '수호자는 흩어진 문장들을 모아 오래된 문 앞에 세웠다. 어느 문장은 떨고 있었고, 어느 문장은 끝맺음을 잃은 채 같은 자리를 맴돌았다.',
  '문에는 손잡이도 열쇠구멍도 없었다. 다만 나무결 사이마다 누군가 오래전에 지워 버린 이야기의 흔적이 가느다랗게 남아 있었다.',
  '수호자가 손바닥을 대자 문 너머에서 종이 넘기는 소리가 들렸다. 바람이 없는 숲인데도 나뭇잎들은 그 소리에 맞춰 한 장씩 몸을 뒤집었다.',
  '그는 가장 짧은 문장을 골라 문턱에 내려놓았다. “나는 여기 있다.” 네 글자뿐인 고백이었지만 닫힌 문은 아주 조금 따뜻해졌다.',
  '두 번째 문장은 오래된 약속에 관한 것이었다. 돌아오겠다는 말과 기다리겠다는 말 사이에는 계절 세 개만큼의 빈칸이 놓여 있었다.',
  '수호자는 빈칸을 억지로 채우지 않았다. 이야기에는 말보다 침묵이 더 정확한 순간도 있다는 것을 그는 여러 겨울을 지나며 배웠다.',
  '해가 숲 가장자리에 닿자 안개 속에서 아이 하나가 나타났다. 아이는 이름 대신 구겨진 종잇조각을 내밀었고, 그 위에는 집으로 가는 길이 적혀 있었다.',
  '하지만 길을 설명하는 문장은 중간에서 끊겨 있었다. 다리를 건너라는 말 뒤에 어느 방향으로 가야 하는지, 누구를 만나야 하는지가 사라져 있었다.',
  '수호자는 아이와 함께 사라진 뒷부분을 찾기로 했다. 둘은 희미한 마침표들이 반딧불처럼 떠다니는 오솔길을 따라 숲 깊은 곳으로 걸었다.',
  '첫 번째 갈림길에서는 쉼표들이 비처럼 내렸다. 서두르는 사람은 같은 자리로 돌아왔고, 잠시 멈춘 사람만 나무 사이의 좁은 길을 발견할 수 있었다.',
  '두 번째 갈림길에는 물음표 모양의 연못이 있었다. 아이가 자신의 이름을 기억하느냐고 묻자 물결 위로 오래된 저녁 식탁과 웃는 얼굴들이 떠올랐다.',
  '아이는 대답 대신 그 풍경을 오래 바라보았다. 기억은 완전하지 않았지만 누군가 자신을 기다리고 있다는 감각만은 작은 불씨처럼 남아 있었다.',
  '연못 건너편에서 잃어버린 문장의 절반이 발견되었다. 젖은 종이에는 “가장 밝은 창문을 찾아”라는 말이 번진 잉크 사이로 겨우 읽혔다.',
  '둘은 숲에서 가장 높은 언덕에 올랐다. 아래로 수백 개의 창문 같은 빛이 보였지만 안개가 번질 때마다 위치와 색이 조금씩 달라졌다.',
  '수호자는 눈을 감고 지금까지 모은 문장들을 소리 내어 읽었다. 서로 다른 목소리로 태어난 말들이 이어지자 흩어진 빛들이 하나의 별자리처럼 정돈되었다.',
  '가운데에서 유난히 따뜻한 창문 하나가 반짝였다. 아이는 그 빛을 보자 잊고 있던 이름을 아주 작은 목소리로 말했고, 숲은 그 이름을 멀리까지 되돌려 주었다.',
  '돌아가는 길에는 더 이상 표지판이 필요하지 않았다. 아이가 한 걸음 내디딜 때마다 발밑에서 다음 문장이 피어나 어둠 속에 길을 만들었다.',
  '오래된 문 앞에 도착했을 때 수호자가 모아 둔 문장들은 가지런한 한 편의 이야기가 되어 있었다. 빠진 부분에는 아이가 기억해 낸 이름이 놓였다.',
  '문은 소리 없이 열렸다. 그 너머에는 거대한 도서관이 있었고, 아직 쓰이지 않은 책들이 새하얀 등을 보인 채 다음 목소리를 기다리고 있었다.',
  '수호자는 가장 가까운 책 한 권을 꺼냈다. 첫 장에는 오늘의 숲이, 두 번째 장에는 아직 만나지 못한 여행자의 발자국이 희미하게 번지고 있었다.',
  '그는 빈 여백 한쪽에 아이가 찾아낸 길을 기록했다. 문장이 끝나자 멀리 있는 집의 창문 하나가 대답하듯 두 번 반짝였고, 책장은 따뜻하게 식어 갔다.',
  '도서관 가장 안쪽에서는 이름 없는 이야기들이 낮은 목소리로 서로를 불렀다. 제목을 얻지 못한 책들도 누군가 펼쳐 주기만 하면 곧 여행을 시작할 준비가 되어 있었다.',
  '수호자는 그중 한 권의 표지를 쓰다듬고 빈 책상 위에 놓았다. 오늘 길을 찾은 아이처럼 내일은 또 다른 사람이 자신의 잃어버린 문장을 찾아올 것이었다.',
  '열린 문으로 새벽바람이 들어오자 책장마다 종이 냄새가 파도처럼 번졌다. 숲에 남아 있던 안개는 그 향기를 따라 가느다란 리본이 되어 천장으로 올라갔다.',
  '마지막 안개 한 조각이 사라진 자리에는 작은 문장 하나가 남았다. “끝은 다음 이야기가 들어올 자리를 비워 두는 일이다.” 수호자는 그 말을 오래 바라보았다.',
  '아이는 가장 밝은 창문이 있는 집으로 돌아갔다. 수호자는 문턱에 남아 새로 도착한 문장들의 먼지를 털고, 각자의 자리를 천천히 찾아 주었다.',
  '마지막 장면에서 안개는 길이 되었고, 사람들은 다시 서로를 읽었다. 숲의 모든 마침표가 별처럼 빛난 뒤에야 수호자는 조용히 다음 장을 펼쳤다.',
].join('\n\n');

const libraryBookDetails: LibraryBookDetailResponse[] = [
  {
    bookId: 18,
    bossRunId: 7,
    bossName: '안개의 수호자',
    coverImageUrl: null,
    completedAt: '2026-07-27T10:05:00Z',
    body: fogGuardianBody,
    likeCount: 12,
    liked: false,
    bookmarked: false,
    contents: [
      {
        contentId: 101,
        sequenceNo: 1,
        contentType: 'USER_ORIGINAL',
        content: '안개가 낮게 깔린 숲에서 첫 번째 문장이 조용히 빛났다.',
        contributor: {
          userId: 1,
          nickname: '문장여행자',
        },
        sourceSubmissionId: 55,
        contribution: {
          originalContent: null,
          finalDamage: 121,
          moods: [
            { code: 'MYSTERY', strength: 0.86, rank: 1 },
            { code: 'CALM', strength: 0.61, rank: 2 },
          ],
          usedDailyWords: ['안개', '빛'],
        },
      },
      {
        contentId: 102,
        sequenceNo: 2,
        contentType: 'USER_EDITED',
        content: '수호자는 흩어진 문장들을 모아 오래된 문 앞에 세웠다.',
        contributor: {
          userId: 2,
          nickname: '새벽작가',
        },
        sourceSubmissionId: 56,
        contribution: {
          originalContent: '수호자는 흩어진 문장들을 주워 오래된 문 앞에 두었다.',
          finalDamage: 137,
          moods: [
            { code: 'CALM', strength: 0.82, rank: 1 },
            { code: 'MYSTERY', strength: 0.64, rank: 2 },
          ],
          usedDailyWords: ['수호자', '새벽'],
        },
      },
      {
        contentId: 103,
        sequenceNo: 3,
        contentType: 'AI_GENERATED',
        content: '마지막 장면에서 안개는 길이 되었고, 사람들은 다시 서로를 읽었다.',
        contributor: null,
        sourceSubmissionId: null,
        contribution: null,
      },
    ],
  },
  {
    bookId: 19,
    bossRunId: 12,
    bossName: '기억의 파수꾼',
    coverImageUrl: null,
    completedAt: '2026-07-29T15:20:00Z',
    body: '기억의 문장은 오래된 종이 냄새를 따라 천천히 돌아왔다.',
    likeCount: 3,
    liked: false,
    bookmarked: false,
    contents: [
      {
        contentId: 201,
        sequenceNo: 1,
        contentType: 'USER_ORIGINAL',
        content: '기억의 문장은 오래된 종이 냄새를 따라 천천히 돌아왔다.',
        contributor: {
          userId: 3,
          nickname: '기록자',
        },
        sourceSubmissionId: 61,
        contribution: {
          originalContent: null,
          finalDamage: 94,
          moods: [{ code: 'NOSTALGIA', strength: 0.79, rank: 1 }],
          usedDailyWords: ['기억'],
        },
      },
    ],
  },
];

export const libraryHandlers = [
  http.get('*/api/v1/library/books', ({ request }) => {
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') ?? '0');
    const size = Number(url.searchParams.get('size') ?? '20');

    return HttpResponse.json(pageResponse(libraryBookSeeds.map(toSummaryResponse), page, size));
  }),

  http.get('*/api/v1/me/bookmarks', ({ request }) => {
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') ?? '0');
    const size = Number(url.searchParams.get('size') ?? '20');
    const bookmarkedBooks = libraryBookSeeds
      .filter((seed) => bookmarkedBookIds.has(seed.bookId))
      .map(toSummaryResponse);

    return HttpResponse.json(pageResponse(bookmarkedBooks, page, size));
  }),

  http.put('*/api/v1/books/:bookId/like', ({ params }) => {
    likedBookIds.add(Number(params.bookId));
    return new HttpResponse(null, { status: 204 });
  }),

  http.delete('*/api/v1/books/:bookId/like', ({ params }) => {
    likedBookIds.delete(Number(params.bookId));
    return new HttpResponse(null, { status: 204 });
  }),

  http.put('*/api/v1/books/:bookId/bookmark', ({ params }) => {
    bookmarkedBookIds.add(Number(params.bookId));
    return new HttpResponse(null, { status: 204 });
  }),

  http.delete('*/api/v1/books/:bookId/bookmark', ({ params }) => {
    bookmarkedBookIds.delete(Number(params.bookId));
    return new HttpResponse(null, { status: 204 });
  }),

  http.get('*/api/v1/library/books/:bookId', ({ params }) => {
    const bookId = Number(params.bookId);
    const detail = libraryBookDetails.find((book) => book.bookId === bookId);

    if (!detail) {
      return HttpResponse.json(
        {
          occurredAt: new Date().toISOString(),
          code: 'NOT_FOUND',
          message: '공동 책을 찾을 수 없습니다.',
          fieldErrors: [],
        },
        {
          status: 404,
        },
      );
    }

    return HttpResponse.json({
      ...detail,
      likeCount: detail.likeCount + (likedBookIds.has(bookId) ? 1 : 0),
      liked: likedBookIds.has(bookId),
      bookmarked: bookmarkedBookIds.has(bookId),
    });
  }),
];
