import { httpClient } from '@/shared/api';
import type { PageResponse } from '@/shared/api';

import type {
  ArchiveEvaluation,
  ArchivePageResponse,
  ArchiveSentence,
  MyContributedBook,
  UnevaluatedArchivePageResponse,
} from './types';

export async function getArchive(page: number, size: number): Promise<ArchivePageResponse> {
  const { data } = await httpClient.get<ArchivePageResponse>('/v1/me/sentences', {
    params: { page, size },
  });

  return { ...data, content: data.content ?? [] };
}

export async function getUnevaluatedArchive(
  page: number,
  size: number,
): Promise<UnevaluatedArchivePageResponse> {
  const { data } = await httpClient.get<UnevaluatedArchivePageResponse>(
    '/v1/me/sentences/unevaluated',
    { params: { page, size } },
  );

  return { ...data, content: data.content ?? [] };
}

export async function getMyContributedBooks(
  page: number,
  size: number,
): Promise<PageResponse<MyContributedBook>> {
  const { data } = await httpClient.get<PageResponse<MyContributedBook>>('/v1/me/library/books', {
    params: { page, size },
  });

  return { ...data, content: data.content ?? [] };
}

export async function getArchiveDetail(sentenceVersionId: number): Promise<ArchiveSentence> {
  const { data } = await httpClient.get<ArchiveSentence>(`/v1/me/sentences/${sentenceVersionId}`);

  return data;
}

export async function getArchiveEvaluation(sentenceVersionId: number): Promise<ArchiveEvaluation> {
  const { data } = await httpClient.get<ArchiveEvaluation>(
    `/v1/sentence-versions/${sentenceVersionId}/evaluation`,
  );

  return data;
}

export async function deleteArchiveSentence(sentenceId: number): Promise<void> {
  await httpClient.delete(`/v1/sentences/${sentenceId}`);
}
