import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { getWeeklySentenceFeed, likeBookContent, unlikeBookContent } from './api';

export const feedQueryKeys = {
  all: ['sentence-feed'] as const,

  weekly: () => [...feedQueryKeys.all, 'weekly'] as const,
};

export function useWeeklySentenceFeedQuery(enabled = true) {
  return useQuery({
    queryKey: feedQueryKeys.weekly(),
    queryFn: getWeeklySentenceFeed,
    enabled,
  });
}

export function useBookContentLikeMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ bookContentId, next }: { bookContentId: number; next: boolean }) =>
      next ? likeBookContent(bookContentId) : unlikeBookContent(bookContentId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: feedQueryKeys.all });
    },
  });
}
