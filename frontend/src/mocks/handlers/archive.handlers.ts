import { HttpResponse, http } from 'msw';

import type {
  ArchivePageResponse,
  ArchiveSentence,
  MyContributedBook,
  UnevaluatedArchivePageResponse,
  UnevaluatedArchiveSentence,
} from '@/features/archive';

const archiveSentences: ArchiveSentence[] = [
  {
    sentenceVersionId: 84,
    sentenceId: 42,
    versionNo: 2,
    content: '달빛이 소담하게 내리는 골목에서 은하수 한 조각을 발견했다.',
    baseScore: 83.35,
    wordBonus: 5,
    gameBaseAttack: 88.35,
    moods: [
      { code: 'CALM', strength: 0.82, rank: 1 },
      { code: 'MYSTERY', strength: 0.64, rank: 2 },
    ],
    usedDailyWords: [
      { dailyWordSetItemId: 15, wordId: 1, word: '은하수' },
      { dailyWordSetItemId: 16, wordId: 3, word: '윤슬' },
    ],
    bossSubmissions: [
      {
        submissionId: 55,
        bossRunId: 7,
        bossName: '안개의 수호자',
        bossPhaseId: 21,
        sentenceVersionId: 84,
        finalDamage: 57,
        submittedAt: '2026-07-30T10:30:00Z',
      },
    ],
    bookInclusions: [{ bookId: 18, sequenceNo: 3 }],
    evaluatedAt: '2026-07-30T02:00:00Z',
  },
  {
    sentenceVersionId: 85,
    sentenceId: 43,
    versionNo: 1,
    content: '산들바람이 포근한 마중처럼 창문 사이로 스며들었다.',
    baseScore: 78,
    wordBonus: 0,
    gameBaseAttack: 78,
    moods: [{ code: 'HOPE', strength: 0.76, rank: 1 }],
    usedDailyWords: [{ dailyWordSetItemId: 17, wordId: 4, word: '산들바람' }],
    bossSubmissions: [],
    bookInclusions: [],
    evaluatedAt: '2026-07-29T02:00:00Z',
  },
];

const unevaluatedSentences: UnevaluatedArchiveSentence[] = [
  {
    sentenceVersionId: 92,
    sentenceId: 47,
    versionNo: 1,
    content: '고요한 바람이 숲의 이야기를 전해 주었다.',
    evaluationStatus: 'DRAFT',
    createdAt: '2026-07-31T01:00:00Z',
  },
  {
    sentenceVersionId: 93,
    sentenceId: 48,
    versionNo: 1,
    content: '마중 나온 강아지가 꼬리로 인사를 건넸다.',
    evaluationStatus: 'DRAFT',
    createdAt: '2026-07-30T22:00:00Z',
  },
];

const contributedBooks: MyContributedBook[] = [
  {
    bookId: 18,
    bossRunId: 7,
    bossName: '새벽의 수호자',
    completedAt: '2026-07-31T13:00:00Z',
    contentCount: 12,
    myIncludedContentCount: 2,
  },
];

export const archiveHandlers = [
  http.delete('*/api/v1/sentences/:sentenceId', () => new HttpResponse(null, { status: 204 })),
  http.get('*/api/v1/me/library/books', ({ request }) => {
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') ?? 0);
    const size = Number(url.searchParams.get('size') ?? 20);
    const start = page * size;

    return HttpResponse.json({
      content: contributedBooks.slice(start, start + size),
      page,
      size,
      totalElements: contributedBooks.length,
      totalPages: Math.ceil(contributedBooks.length / size),
      hasNext: start + size < contributedBooks.length,
    });
  }),
  // NOTE: /me/sentences/:sentenceVersionId보다 먼저 매칭되도록 상세 핸들러 앞에 둔다.
  // (상세 핸들러도 비숫자 세그먼트를 다음 핸들러로 넘기므로 순서가 바뀌어도 동작한다)
  http.get('*/api/v1/me/sentences/unevaluated', ({ request }) => {
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') ?? 0);
    const size = Number(url.searchParams.get('size') ?? 10);
    const start = page * size;
    const content = unevaluatedSentences.slice(start, start + size);
    const response: UnevaluatedArchivePageResponse = {
      content,
      page,
      size,
      totalElements: unevaluatedSentences.length,
      totalPages: Math.ceil(unevaluatedSentences.length / size),
      hasNext: start + size < unevaluatedSentences.length,
    };

    return HttpResponse.json(response);
  }),
  http.get('*/api/v1/me/sentences', ({ request }) => {
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') ?? 0);
    const size = Number(url.searchParams.get('size') ?? 10);
    const start = page * size;
    const content = archiveSentences.slice(start, start + size);
    const response: ArchivePageResponse = {
      content,
      page,
      size,
      totalElements: archiveSentences.length,
      totalPages: Math.ceil(archiveSentences.length / size),
      hasNext: start + size < archiveSentences.length,
    };

    return HttpResponse.json(response);
  }),
  http.get('*/api/v1/me/sentences/:sentenceVersionId', ({ params }) => {
    // 'unevaluated' 같은 비숫자 세그먼트는 이 핸들러가 처리하지 않고 다음 핸들러로 넘긴다.
    if (!Number.isInteger(Number(params.sentenceVersionId))) {
      return undefined;
    }

    const sentence = archiveSentences.find(
      ({ sentenceVersionId }) => sentenceVersionId === Number(params.sentenceVersionId),
    );

    return sentence
      ? HttpResponse.json(sentence)
      : HttpResponse.json(
          { code: 'NOT_FOUND', message: '문장을 찾을 수 없습니다.' },
          { status: 404 },
        );
  }),
];
