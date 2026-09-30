import type { LibraryBookListParams } from './types';

export const libraryQueryKeys = {
  all: ['library-books'] as const,

  lists: () => [...libraryQueryKeys.all, 'list'] as const,

  list: (params: LibraryBookListParams) => [...libraryQueryKeys.lists(), params] as const,

  details: () => [...libraryQueryKeys.all, 'detail'] as const,

  detail: (bookId: number) => [...libraryQueryKeys.details(), bookId] as const,

  bookmarks: () => [...libraryQueryKeys.all, 'my-bookmarks'] as const,

  bookmarkList: (params: LibraryBookListParams) =>
    [...libraryQueryKeys.bookmarks(), params] as const,
};
