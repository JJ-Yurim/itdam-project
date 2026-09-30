import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { server } from '@/mocks/server';

import { ArchiveBookmarksPage } from './ArchiveBookmarksPage';

function renderBookmarksPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(
    [{ path: '/archive/bookmarks', element: <ArchiveBookmarksPage /> }],
    { initialEntries: ['/archive/bookmarks'] },
  );

  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );

  return router;
}

describe('ArchiveBookmarksPage', () => {
  it('북마크한 책 목록을 표시한다', async () => {
    server.use(
      http.get('*/api/v1/me/bookmarks', () =>
        HttpResponse.json({
          content: [
            {
              bookId: 18,
              bossRunId: 7,
              bossName: '안개의 수호자',
              coverImageUrl: '/images/book-covers/fog-guardian.webp',
              completedAt: '2026-07-27T10:05:00Z',
              contentCount: 8,
              summary: '안개 낀 숲에서 흩어진 문장들이 모여 하나의 길이 되는 이야기.',
              likeCount: 12,
              liked: false,
              bookmarked: true,
            },
          ],
          page: 0,
          size: 10,
          totalElements: 1,
          totalPages: 1,
          hasNext: false,
        }),
      ),
    );

    renderBookmarksPage();

    expect(await screen.findByText('안개의 수호자')).toBeVisible();
    expect(screen.getByAltText('안개의 수호자 책 표지')).toHaveAttribute(
      'src',
      '/images/book-covers/fog-guardian.webp',
    );
    expect(screen.getByText('총 1권')).toBeVisible();
    expect(screen.getByRole('link', { name: '책 자세히 보기' })).toHaveAttribute(
      'href',
      '/library/books/18',
    );
    expect(screen.getByRole('link', { name: '‹ 내 서고' })).toHaveAttribute('href', '/archive');
  });

  it('북마크를 해제하면 목록에서 사라진다', async () => {
    const user = userEvent.setup();

    renderBookmarksPage();

    await user.click(await screen.findByRole('button', { name: '북마크 해제' }));

    expect(await screen.findByText('아직 북마크한 책이 없어요')).toBeVisible();
  });

  it('북마크가 없으면 빈 상태를 표시한다', async () => {
    server.use(
      http.get('*/api/v1/me/bookmarks', () =>
        HttpResponse.json({
          content: [],
          page: 0,
          size: 10,
          totalElements: 0,
          totalPages: 0,
          hasNext: false,
        }),
      ),
    );

    renderBookmarksPage();

    expect(await screen.findByText('아직 북마크한 책이 없어요')).toBeVisible();
    // 빈 상태에서 다음 행동으로 도서관을 안내한다.
    expect(screen.getByRole('link', { name: '도서관 둘러보기' })).toHaveAttribute(
      'href',
      '/library',
    );
  });

  it('조회에 실패하면 오류와 재시도 버튼을 표시한다', async () => {
    server.use(
      http.get('*/api/v1/me/bookmarks', () =>
        HttpResponse.json({ code: 'INTERNAL_ERROR', message: '조회 실패' }, { status: 500 }),
      ),
    );

    renderBookmarksPage();

    expect(await screen.findByRole('alert')).toHaveTextContent('북마크 목록을 불러오지 못했어요');
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeVisible();
  });
});
