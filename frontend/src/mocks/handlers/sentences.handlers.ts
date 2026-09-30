import { HttpResponse, http } from 'msw';

import type {
  EvaluationUsage,
  SentenceEvaluationResponse,
  SentenceVersionResponse,
} from '@/features/sentences';

const todayEvaluationUsage: EvaluationUsage = {
  serviceDate: '2026-07-30',
  usedCount: 8,
  limit: 15,
  remainingCount: 7,
};

const createDraftResponse = (content: string): SentenceVersionResponse => ({
  id: 84,
  sentenceVersionId: 84,
  sentenceId: 42,
  versionNo: 1,
  content: content.trim(),
  evaluationStatus: 'DRAFT',
  createdAt: '2026-07-30T01:00:00Z',
  confirmedAt: null,
});

const evaluationResponse: SentenceEvaluationResponse = {
  sentenceVersionId: 84,
  evaluationStatus: 'EVALUATED',
  safety: { status: 'PASSED', reason: null },
  scores: {
    completeness: 82,
    originality: 76,
    expression: 88,
    imagery: 91,
    emotionalImpact: 84,
    baseScore: 83.35,
  },
  wordBonus: 5,
  gameBaseAttack: 88.35,
  overallFeedback: '이미지가 선명하고 문장의 흐름이 자연스러워요.',
  moods: [
    { code: 'CALM', strength: 0.82, rank: 1 },
    { code: 'MYSTERY', strength: 0.64, rank: 2 },
  ],
  usedDailyWords: [
    { dailyWordSetItemId: 15, wordId: 1, word: '은하수' },
    { dailyWordSetItemId: 16, wordId: 3, word: '윤슬' },
  ],
  modelName: 'gpt-4.1-mini',
  promptVersion: '2026-07-01',
  rubricVersion: 'v1',
  evaluatedAt: '2026-07-30T02:00:00Z',
};

export const sentencesHandlers = [
  http.get('*/api/v1/evaluation-usage/today', () => HttpResponse.json(todayEvaluationUsage)),
  http.post('*/api/v1/sentences', async ({ request }) => {
    const body = (await request.json()) as { content: string };

    return HttpResponse.json(createDraftResponse(body.content), { status: 201 });
  }),
  http.post('*/api/v1/sentences/:sentenceId/versions', async ({ params, request }) => {
    const body = (await request.json()) as { content: string };

    return HttpResponse.json(
      {
        ...createDraftResponse(body.content),
        id: 85,
        sentenceVersionId: 85,
        sentenceId: Number(params.sentenceId),
        versionNo: 2,
      },
      { status: 201 },
    );
  }),
  http.patch('*/api/v1/sentence-versions/:versionId', async ({ params, request }) => {
    const body = (await request.json()) as { content: string };
    const versionId = Number(params.versionId);

    return HttpResponse.json({
      ...createDraftResponse(body.content),
      id: versionId,
      sentenceVersionId: versionId,
    });
  }),
  http.get('*/api/v1/sentence-versions/:versionId', ({ params }) => {
    const versionId = Number(params.versionId);

    return HttpResponse.json({
      // 평가 게이트(오늘의 단어 2개 이상)를 통과할 수 있게 단어 2개를 포함한다.
      ...createDraftResponse('고요한 골목에 은하수와 윤슬이 번졌다.'),
      id: versionId,
      sentenceVersionId: versionId,
    });
  }),
  http.post('*/api/v1/sentence-versions/:versionId/evaluations', ({ params }) =>
    HttpResponse.json({
      ...evaluationResponse,
      sentenceVersionId: Number(params.versionId),
    }),
  ),
  http.get('*/api/v1/sentence-versions/:versionId/evaluation', ({ params }) =>
    HttpResponse.json({
      ...evaluationResponse,
      sentenceVersionId: Number(params.versionId),
    }),
  ),
];
