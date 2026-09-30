import { HttpResponse, http } from 'msw';

import type { BookGenerationStatusResponse } from '@/features/book-generation';

const generatingStatus: BookGenerationStatusResponse = {
  bossRunId: 7,
  bookId: 18,
  bookStatus: 'GENERATING',
  jobId: 31,
  jobStatus: 'PROCESSING',
  requestedAt: '2026-07-27T10:00:00Z',
  completedAt: null,
  updatedAt: '2026-07-27T10:01:00Z',
};

const completedStatus: BookGenerationStatusResponse = {
  bossRunId: 8,
  bookId: 19,
  bookStatus: 'COMPLETED',
  jobId: 32,
  jobStatus: 'SUCCESS',
  requestedAt: '2026-07-27T10:00:00Z',
  completedAt: '2026-07-27T10:05:00Z',
  updatedAt: '2026-07-27T10:05:00Z',
};

const failedStatus: BookGenerationStatusResponse = {
  bossRunId: 9,
  bookId: 20,
  bookStatus: 'FAILED',
  jobId: 33,
  jobStatus: 'FAILED',
  requestedAt: '2026-07-27T10:00:00Z',
  completedAt: null,
  updatedAt: '2026-07-27T10:03:00Z',
};

const statuses = new Map<number, BookGenerationStatusResponse>([
  [generatingStatus.bossRunId, generatingStatus],
  [completedStatus.bossRunId, completedStatus],
  [failedStatus.bossRunId, failedStatus],
]);

function createBookGenerationStatusHandler(path: string) {
  return http.get(path, ({ params }) => {
    const bossRunId = Number(params.bossRunId);
    const status = statuses.get(bossRunId);

    if (!status) {
      return HttpResponse.json(
        {
          occurredAt: new Date().toISOString(),
          code: 'NOT_FOUND',
          message: '공동 책 생성 상태를 찾을 수 없습니다.',
          fieldErrors: [],
        },
        {
          status: 404,
        },
      );
    }

    return HttpResponse.json(status);
  });
}

export const bookGenerationHandlers = [
  createBookGenerationStatusHandler('*/api/v1/boss-runs/:bossRunId/book-generation'),
  createBookGenerationStatusHandler('*/v1/boss-runs/:bossRunId/book-generation'),
];
