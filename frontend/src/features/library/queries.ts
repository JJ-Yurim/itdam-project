import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { getApiError } from '@/shared/api';

import {
  bookmarkBook,
  getLibraryBook,
  getLibraryBooks,
  getMyBookmarks,
  likeBook,
  unbookmarkBook,
  unlikeBook,
} from './api';
import { libraryQueryKeys } from './queryKeys';

import type { LibraryBookListParams } from './types';

export function useLibraryBooksQuery(params: LibraryBookListParams = {}, enabled = true) {
  return useQuery({
    queryKey: libraryQueryKeys.list(params),
    queryFn: () => getLibraryBooks(params),
    enabled,
    // 페이지를 넘길 때 이전 목록을 유지한다. 없으면 새 페이지 키가 pending이 되며 목록과
    // 페이지네이션 컨트롤이 함께 사라져, 연속으로 넘기는 도중 클릭 대상이 없어진다.
    placeholderData: keepPreviousData,
  });
}

export function useLibraryBookQuery(bookId: number) {
  return useQuery({
    queryKey: libraryQueryKeys.detail(bookId),
    queryFn: () => getLibraryBook(bookId),
    enabled: bookId > 0,
  });
}

export function useMyBookmarksQuery(params: LibraryBookListParams = {}, enabled = true) {
  return useQuery({
    queryKey: libraryQueryKeys.bookmarkList(params),
    queryFn: () => getMyBookmarks(params),
    enabled,
  });
}

// 좋아요·북마크 토글 성공 후에는 liked·bookmarked 플래그가 실린 도서관 목록과
// 북마크 목록이 함께 달라지므로 library 루트 키 전체를 무효화한다.
function useBookReactionInvalidation() {
  const queryClient = useQueryClient();

  return () => {
    void queryClient.invalidateQueries({ queryKey: libraryQueryKeys.all });
  };
}

export function useBookLikeMutation() {
  const invalidate = useBookReactionInvalidation();

  return useMutation({
    mutationFn: ({ bookId, next }: { bookId: number; next: boolean }) =>
      next ? likeBook(bookId) : unlikeBook(bookId),
    onSuccess: invalidate,
  });
}

export function useBookBookmarkMutation() {
  const invalidate = useBookReactionInvalidation();

  return useMutation({
    mutationFn: ({ bookId, next }: { bookId: number; next: boolean }) =>
      next ? bookmarkBook(bookId) : unbookmarkBook(bookId),
    onSuccess: invalidate,
  });
}

/**
 * 반응 실패를 사용자에게 알릴 문구를 고른다.
 *
 * 낙관적 갱신이 없어 실패하면 화면이 그대로다. 안내가 없으면 눌렀는데 아무 일도
 * 일어나지 않은 상태로 남아, 서버 문제인지 버튼 고장인지 구분할 수 없다.
 *
 * 뮤테이션 훅이 아니라 여기에 둔 이유는 데이터 계층이 ToastProvider에 묶이면 이 훅을
 * 쓰는 모든 컴포넌트가 그 프로바이더 없이는 렌더조차 못 하기 때문이다.
 */
export function getBookReactionErrorMessage(error: unknown, fallback: string) {
  return getApiError(error).code === 'UNAUTHORIZED' ? '로그인이 필요해요.' : fallback;
}
