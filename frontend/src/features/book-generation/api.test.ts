import { describe, expect, it } from 'vitest';

import { getBookGenerationStatus } from './api';
import { BOOK_GENERATION_POLLING_INTERVAL_MS, getBookGenerationRefetchInterval } from './queries';

describe('getBookGenerationStatus', () => {
  it('보스전 ID로 공동 책 생성 상태를 조회한다', async () => {
    const result = await getBookGenerationStatus(7);

    expect(result.bossRunId).toBe(7);
    expect(result.bookStatus).toBe('GENERATING');
    expect(result.jobStatus).toBe('PROCESSING');
    expect(result.completedAt).toBeNull();
  });

  it('존재하지 않는 보스전이면 404 오류를 전달한다', async () => {
    await expect(getBookGenerationStatus(999)).rejects.toMatchObject({
      response: {
        status: 404,
        data: {
          code: 'NOT_FOUND',
          message: '공동 책 생성 상태를 찾을 수 없습니다.',
          fieldErrors: [],
        },
      },
    });
  });
});

describe('getBookGenerationRefetchInterval', () => {
  it('생성 중일 때만 polling 간격을 반환한다', () => {
    expect(
      getBookGenerationRefetchInterval({
        bossRunId: 7,
        bookId: 18,
        bookStatus: 'GENERATING',
        jobId: 31,
        jobStatus: 'PROCESSING',
        requestedAt: '2026-07-27T10:00:00Z',
        completedAt: null,
        updatedAt: '2026-07-27T10:01:00Z',
      }),
    ).toBe(BOOK_GENERATION_POLLING_INTERVAL_MS);

    expect(
      getBookGenerationRefetchInterval({
        bossRunId: 7,
        bookId: 18,
        bookStatus: 'COMPLETED',
        jobId: 31,
        jobStatus: 'SUCCESS',
        requestedAt: '2026-07-27T10:00:00Z',
        completedAt: '2026-07-27T10:05:00Z',
        updatedAt: '2026-07-27T10:05:00Z',
      }),
    ).toBe(false);

    expect(
      getBookGenerationRefetchInterval({
        bossRunId: 7,
        bookId: 18,
        bookStatus: 'FAILED',
        jobId: 31,
        jobStatus: 'FAILED',
        requestedAt: '2026-07-27T10:00:00Z',
        completedAt: null,
        updatedAt: '2026-07-27T10:05:00Z',
      }),
    ).toBe(false);
  });
});
