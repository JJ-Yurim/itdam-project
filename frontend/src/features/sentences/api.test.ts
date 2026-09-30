import { HttpResponse, http } from 'msw';
import { describe, expect, it } from 'vitest';

import { server } from '@/mocks/server';

import {
  createSentenceDraft,
  createSentenceVersion,
  evaluateSentenceVersion,
  getSentenceEvaluation,
  getSentenceVersion,
  updateSentenceDraft,
} from './api';
import type { SentenceVersionResponse } from './types';

const createDraftVersion = (
  overrides: Partial<SentenceVersionResponse> = {},
): SentenceVersionResponse => ({
  id: 84,
  sentenceVersionId: 84,
  sentenceId: 42,
  versionNo: 1,
  content: '고요한 골목에 햇빛이 번졌다.',
  evaluationStatus: 'DRAFT',
  createdAt: '2026-07-30T01:00:00Z',
  confirmedAt: null,
  ...overrides,
});

describe('sentence draft API', () => {
  it('createSentenceDraft는 POST /v1/sentences로 content를 보내고 응답을 그대로 반환한다', async () => {
    const serverResponse = createDraftVersion({ content: '고요한 골목에 햇빛이 번졌다.' });
    let capturedBody: unknown = null;

    server.use(
      http.post('*/api/v1/sentences', async ({ request }) => {
        capturedBody = await request.json();

        return HttpResponse.json(serverResponse, { status: 201 });
      }),
    );

    const result = await createSentenceDraft('고요한 골목에 햇빛이 번졌다.');

    expect(capturedBody).toEqual({ content: '고요한 골목에 햇빛이 번졌다.' });
    expect(result).toEqual(serverResponse);
  });

  it('updateSentenceDraft는 PATCH /v1/sentence-versions/{versionId}로 content를 보내고 응답을 그대로 반환한다', async () => {
    const serverResponse = createDraftVersion({
      id: 91,
      sentenceVersionId: 91,
      content: '햇빛이 비치는 골목',
    });
    let capturedUrl: string | null = null;
    let capturedBody: unknown = null;

    server.use(
      http.patch('*/api/v1/sentence-versions/:versionId', async ({ request }) => {
        capturedUrl = new URL(request.url).pathname;
        capturedBody = await request.json();

        return HttpResponse.json(serverResponse);
      }),
    );

    const result = await updateSentenceDraft(91, '햇빛이 비치는 골목');

    expect(capturedUrl).toBe('/api/v1/sentence-versions/91');
    expect(capturedBody).toEqual({ content: '햇빛이 비치는 골목' });
    expect(result).toEqual(serverResponse);
  });

  it('createSentenceVersion은 POST /v1/sentences/{sentenceId}/versions로 content를 보내고 응답을 그대로 반환한다', async () => {
    const serverResponse = createDraftVersion({
      id: 85,
      sentenceVersionId: 85,
      versionNo: 2,
      content: '수정한 새 문장',
    });
    let capturedUrl: string | null = null;
    let capturedBody: unknown = null;

    server.use(
      http.post('*/api/v1/sentences/:sentenceId/versions', async ({ request }) => {
        capturedUrl = new URL(request.url).pathname;
        capturedBody = await request.json();

        return HttpResponse.json(serverResponse, { status: 201 });
      }),
    );

    const result = await createSentenceVersion(42, '수정한 새 문장');

    expect(capturedUrl).toBe('/api/v1/sentences/42/versions');
    expect(capturedBody).toEqual({ content: '수정한 새 문장' });
    expect(result).toEqual(serverResponse);
  });

  it('getSentenceVersion은 GET /v1/sentence-versions/{versionId}를 호출하고 응답을 그대로 반환한다', async () => {
    const serverResponse = createDraftVersion();
    let capturedUrl: string | null = null;

    server.use(
      http.get('*/api/v1/sentence-versions/:versionId', ({ request }) => {
        capturedUrl = new URL(request.url).pathname;

        return HttpResponse.json(serverResponse);
      }),
    );

    const result = await getSentenceVersion(84);

    expect(capturedUrl).toBe('/api/v1/sentence-versions/84');
    expect(result).toEqual(serverResponse);
  });
});

describe('sentence evaluation API', () => {
  it('저장된 문장의 AI 평가를 요청한다', async () => {
    const result = await evaluateSentenceVersion(84);

    expect(result.evaluationStatus).toBe('EVALUATED');
    expect(result.scores?.baseScore).toBe(83.35);
    expect(result.usedDailyWords).toHaveLength(2);
  });

  it('문장 평가 결과를 다시 조회한다', async () => {
    const result = await getSentenceEvaluation(84);

    expect(result.safety?.status).toBe('PASSED');
    expect(result.moods[0]?.code).toBe('CALM');
  });

  it('409 충돌이면 최신 평가 결과를 조회해 복구한다', async () => {
    let resultRequestCount = 0;
    server.use(
      http.post('*/api/v1/sentence-versions/:versionId/evaluations', () =>
        HttpResponse.json(
          { code: 'CONFLICT', message: '이미 평가 중입니다.', fieldErrors: [] },
          { status: 409 },
        ),
      ),
      http.get('*/api/v1/sentence-versions/:versionId/evaluation', () => {
        resultRequestCount += 1;
        return HttpResponse.json({
          sentenceVersionId: 84,
          evaluationStatus: 'EVALUATING',
          safety: null,
          scores: null,
          wordBonus: null,
          gameBaseAttack: null,
          overallFeedback: null,
          moods: [],
          usedDailyWords: [],
          modelName: null,
          promptVersion: null,
          rubricVersion: null,
          evaluatedAt: null,
        });
      }),
    );

    const result = await evaluateSentenceVersion(84);

    expect(result.evaluationStatus).toBe('EVALUATING');
    expect(resultRequestCount).toBe(1);
  });
});
