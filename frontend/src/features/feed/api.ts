import { httpClient } from '@/shared/api';

import type { WeeklySentenceFeedResponse } from './types';

export async function getWeeklySentenceFeed(): Promise<WeeklySentenceFeedResponse> {
  const { data } = await httpClient.get<WeeklySentenceFeedResponse>('/v1/sentences/feed');

  return { ...data, items: data.items ?? [] };
}

export async function likeBookContent(bookContentId: number): Promise<void> {
  await httpClient.put(`/v1/book-contents/${bookContentId}/like`);
}

export async function unlikeBookContent(bookContentId: number): Promise<void> {
  await httpClient.delete(`/v1/book-contents/${bookContentId}/like`);
}
