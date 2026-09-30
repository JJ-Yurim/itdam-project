import { beforeEach, describe, expect, it, vi } from 'vitest';

import { getTodayDailyWords } from './api';

const { mockGet } = vi.hoisted(() => ({
  mockGet: vi.fn(),
}));

vi.mock('@/shared/api', () => ({
  httpClient: {
    get: mockGet,
  },
}));

const firstWord = {
  position: 1,
  wordId: 1,
  word: 'first',
  partOfSpeech: 'noun',
  definition: 'first definition',
  example: 'first example',
};

const secondWord = {
  ...firstWord,
  position: 2,
  wordId: 2,
  word: 'second',
};

describe('getTodayDailyWords', () => {
  beforeEach(() => {
    mockGet.mockReset();
  });

  it('sorts items by position in ascending order', async () => {
    mockGet.mockResolvedValue({
      data: {
        dailyWordSetId: 109,
        serviceDate: '2026-08-03',
        items: [secondWord, firstWord],
      },
    });

    const result = await getTodayDailyWords();

    expect(result.items.map(({ position }) => position)).toEqual([1, 2]);
  });

  it('normalizes a missing items field to an empty array', async () => {
    mockGet.mockResolvedValue({
      data: {
        dailyWordSetId: 109,
        serviceDate: '2026-08-03',
      },
    });

    await expect(getTodayDailyWords()).resolves.toMatchObject({ items: [] });
  });

  it('normalizes null items to an empty array', async () => {
    mockGet.mockResolvedValue({
      data: {
        dailyWordSetId: 109,
        serviceDate: '2026-08-03',
        items: null,
      },
    });

    await expect(getTodayDailyWords()).resolves.toMatchObject({ items: [] });
  });

  it.each([{}, 7])('normalizes non-array items (%j) to an empty array', async (items) => {
    mockGet.mockResolvedValue({
      data: {
        dailyWordSetId: 109,
        serviceDate: '2026-08-03',
        items,
      },
    });

    await expect(getTodayDailyWords()).resolves.toMatchObject({ items: [] });
  });

  it.each([undefined, null, {}, { dailyWordSetId: 109 }, { serviceDate: '2026-08-03' }])(
    'throws a clear error for an invalid response (%j)',
    async (data) => {
      mockGet.mockResolvedValue({ data });

      await expect(getTodayDailyWords()).rejects.toThrow('Today daily words response is invalid.');
    },
  );
});
