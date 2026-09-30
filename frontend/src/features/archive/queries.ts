import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { ALWAYS_REFRESH_ON_MOUNT } from '@/shared/api';

import {
  deleteArchiveSentence,
  getArchive,
  getArchiveDetail,
  getArchiveEvaluation,
  getMyContributedBooks,
  getUnevaluatedArchive,
} from './api';

export const archiveKeys = {
  all: ['archive'] as const,
  list: (page: number, size: number) => [...archiveKeys.all, 'list', { page, size }] as const,
  unevaluatedList: (page: number, size: number) =>
    [...archiveKeys.all, 'unevaluated', { page, size }] as const,
  contributedBooks: (page: number, size: number) =>
    [...archiveKeys.all, 'contributed-books', { page, size }] as const,
  detail: (sentenceVersionId: number) => [...archiveKeys.all, 'detail', sentenceVersionId] as const,
  evaluation: (sentenceVersionId: number) =>
    [...archiveKeys.all, 'evaluation', sentenceVersionId] as const,
};

export function useArchiveQuery(page: number, size: number, enabled = true) {
  return useQuery({
    queryKey: archiveKeys.list(page, size),
    queryFn: () => getArchive(page, size),
    enabled,
    ...ALWAYS_REFRESH_ON_MOUNT,
  });
}

export function useUnevaluatedArchiveQuery(page: number, size: number, enabled = true) {
  return useQuery({
    queryKey: archiveKeys.unevaluatedList(page, size),
    queryFn: () => getUnevaluatedArchive(page, size),
    enabled,
    ...ALWAYS_REFRESH_ON_MOUNT,
  });
}

export function useArchiveDetailQuery(sentenceVersionId: number | null) {
  return useQuery({
    queryKey: archiveKeys.detail(sentenceVersionId ?? 0),
    queryFn: () => getArchiveDetail(sentenceVersionId as number),
    enabled: sentenceVersionId !== null,
  });
}

export function useArchiveEvaluationQuery(sentenceVersionId: number | null) {
  return useQuery({
    queryKey: archiveKeys.evaluation(sentenceVersionId ?? 0),
    queryFn: () => getArchiveEvaluation(sentenceVersionId as number),
    enabled: sentenceVersionId !== null,
  });
}

export function useMyContributedBooksQuery(page: number, size: number, enabled = true) {
  return useQuery({
    queryKey: archiveKeys.contributedBooks(page, size),
    queryFn: () => getMyContributedBooks(page, size),
    enabled,
  });
}

export function useDeleteArchiveSentenceMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ sentenceId }: { sentenceId: number; sentenceVersionId: number }) =>
      deleteArchiveSentence(sentenceId),
    onSuccess: (_data, { sentenceVersionId }) => {
      void queryClient.invalidateQueries({ queryKey: archiveKeys.all });
      queryClient.removeQueries({ queryKey: archiveKeys.detail(sentenceVersionId) });
      queryClient.removeQueries({ queryKey: archiveKeys.evaluation(sentenceVersionId) });
      void queryClient.invalidateQueries({ queryKey: ['sentences'] });
    },
  });
}
