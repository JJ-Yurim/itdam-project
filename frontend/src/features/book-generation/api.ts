import { httpClient } from '@/shared/api';

import type { BookGenerationStatusResponse } from './types';

export async function getBookGenerationStatus(
  bossRunId: number,
): Promise<BookGenerationStatusResponse> {
  const { data } = await httpClient.get<BookGenerationStatusResponse>(
    `/v1/boss-runs/${bossRunId}/book-generation`,
  );

  return data;
}
