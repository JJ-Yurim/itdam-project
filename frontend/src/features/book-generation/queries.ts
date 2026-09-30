import { useQuery } from '@tanstack/react-query';

import { getBookGenerationStatus } from './api';
import { bookGenerationQueryKeys } from './queryKeys';

import type { BookGenerationStatusResponse } from './types';

export const BOOK_GENERATION_POLLING_INTERVAL_MS = 10_000;

export function getBookGenerationRefetchInterval(status?: BookGenerationStatusResponse) {
  return status?.bookStatus === 'GENERATING' ? BOOK_GENERATION_POLLING_INTERVAL_MS : false;
}

export function useBookGenerationStatusQuery(bossRunId: number) {
  return useQuery({
    queryKey: bookGenerationQueryKeys.detail(bossRunId),
    queryFn: () => getBookGenerationStatus(bossRunId),
    enabled: bossRunId > 0,
    refetchInterval: (query) => getBookGenerationRefetchInterval(query.state.data),
  });
}
