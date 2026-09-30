import { httpClient } from '@/shared/api';

import type {
  LibraryBookDetailResponse,
  LibraryBookListParams,
  LibraryBookPageResponse,
  LibraryBookSummaryResponse,
} from './types';

export async function getLibraryBooks(
  params: LibraryBookListParams = {},
): Promise<LibraryBookPageResponse<LibraryBookSummaryResponse>> {
  const { data } = await httpClient.get<LibraryBookPageResponse<LibraryBookSummaryResponse>>(
    '/v1/library/books',
    {
      params,
    },
  );

  return data;
}

export async function getLibraryBook(bookId: number): Promise<LibraryBookDetailResponse> {
  const { data } = await httpClient.get<LibraryBookDetailResponse>(`/v1/library/books/${bookId}`);

  return data;
}

export async function getMyBookmarks(
  params: LibraryBookListParams = {},
): Promise<LibraryBookPageResponse<LibraryBookSummaryResponse>> {
  const { data } = await httpClient.get<LibraryBookPageResponse<LibraryBookSummaryResponse>>(
    '/v1/me/bookmarks',
    {
      params,
    },
  );

  return data;
}

export async function likeBook(bookId: number): Promise<void> {
  await httpClient.put(`/v1/books/${bookId}/like`);
}

export async function unlikeBook(bookId: number): Promise<void> {
  await httpClient.delete(`/v1/books/${bookId}/like`);
}

export async function bookmarkBook(bookId: number): Promise<void> {
  await httpClient.put(`/v1/books/${bookId}/bookmark`);
}

export async function unbookmarkBook(bookId: number): Promise<void> {
  await httpClient.delete(`/v1/books/${bookId}/bookmark`);
}
