import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { AuthGateProvider } from '@/features/auth';
import { ToastProvider } from '@/shared/components';
import type { LibraryBookSummaryResponse } from '@/features/library';
import { server } from '@/mocks/server';

import { LibraryPage } from './LibraryPage';

function createBook(overrides: Partial<LibraryBookSummaryResponse>): LibraryBookSummaryResponse {
  return {
    bookId: 1,
    bossRunId: 1,
    bossName: '테스트 공동 책',
    coverImageUrl: null,
    completedAt: '2026-08-01T00:00:00Z',
    contentCount: 4,
    summary: null,
    likeCount: 0,
    liked: false,
    bookmarked: false,
    ...overrides,
  };
}

function renderLibraryPage(initialEntry = '/library') {
  server.use(
    http.get('*/api/v1/users/me', () =>
      HttpResponse.json({
        id: 1,
        email: 'writer@example.com',
        nickname: '문장여행자',
        status: 'ACTIVE',
        role: 'USER',
        createdAt: '2026-07-30T00:00:00Z',
      }),
    ),
  );
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  // 책 카드의 좋아요·북마크가 로그인 유도 게이트를 쓰므로 Provider가 필요하다.
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <AuthGateProvider>
          <ToastProvider>
            <LibraryPage />
          </ToastProvider>
        </AuthGateProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

// 좋아요·북마크 변경은 회원 전용이라 로그인 상태를 만든다. 기본 목은 비회원이다.
function loginAsMember() {
  server.use(
    http.get('*/api/v1/users/me', () =>
      HttpResponse.json({
        id: 1,
        email: 'writer@example.com',
        nickname: '문장여행자',
        status: 'ACTIVE',
        role: 'USER',
        createdAt: '2026-07-30T00:00:00Z',
      }),
    ),
  );
}

describe('LibraryPage', () => {
  it('공동 책 카드에서 좋아요와 북마크를 변경한다', async () => {
    const user = userEvent.setup();

    loginAsMember();
    renderLibraryPage();

    const likeButton = await screen.findByRole('button', { name: '좋아요, 현재 12개' });
    expect(likeButton).toHaveClass('pointer-events-auto');
    expect(likeButton.parentElement).toHaveClass('pointer-events-none');

    await user.click(likeButton);
    expect(await screen.findByRole('button', { name: '좋아요 취소, 현재 13개' })).toBeVisible();

    const bookmarkButton = screen.getByRole('button', { name: '북마크 해제' });
    await user.click(bookmarkButton);
    await waitFor(() => expect(bookmarkButton).toHaveAccessibleName('북마크 추가'));
  });

  it('공동 책을 표지 중심의 2열 목록으로 표시한다', async () => {
    let requestedSize: string | null = null;
    server.use(
      http.get('*/api/v1/library/books', ({ request }) => {
        requestedSize = new URL(request.url).searchParams.get('size');
        return HttpResponse.json({
          content: [
            createBook({
              bookId: 18,
              bossName: '달빛 아래 도서관',
              coverImageUrl: '/images/book-covers/moon.webp',
            }),
            createBook({ bookId: 19, bossName: '비밀정원과 열쇠' }),
          ],
          page: 0,
          size: 100,
          totalElements: 2,
          totalPages: 1,
          hasNext: false,
        });
      }),
    );

    renderLibraryPage();

    expect(await screen.findByText('달빛 아래 도서관')).toBeVisible();
    expect(screen.getByText('비밀정원과 열쇠')).toBeVisible();
    expect(screen.getByAltText('달빛 아래 도서관 책 표지')).toHaveAttribute(
      'src',
      '/images/book-covers/moon.webp',
    );
    expect(screen.getByRole('link', { name: '달빛 아래 도서관 상세 보기' })).toHaveAttribute(
      'href',
      '/library/books/18',
    );
    expect(screen.getByText('2권')).toBeVisible();
    expect(
      within(screen.getByRole('link', { name: '비밀정원과 열쇠 상세 보기' })).queryByText(
        '공동 책',
      ),
    ).not.toBeInTheDocument();
    expect(requestedSize).toBe('100');
  });

  it('책 제목을 검색하고 검색 결과를 4권씩 페이지네이션한다', async () => {
    const user = userEvent.setup();
    const books = Array.from({ length: 5 }, (_, index) =>
      createBook({ bookId: index + 1, bossName: `달빛 이야기 ${index + 1}` }),
    );

    server.use(
      http.get('*/api/v1/library/books', () =>
        HttpResponse.json({
          content: [...books, createBook({ bookId: 99, bossName: '고양이 이야기' })],
          page: 0,
          size: 100,
          totalElements: 6,
          totalPages: 1,
          hasNext: false,
        }),
      ),
    );

    renderLibraryPage();

    const searchInput = await screen.findByRole('searchbox', { name: '책 제목 검색' });
    await user.type(searchInput, '달빛');

    expect(screen.getByText('5권')).toBeVisible();
    expect(screen.getByText('1 / 2')).toBeVisible();
    expect(screen.queryByText('고양이 이야기')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '다음' }));
    expect(screen.getByText('2 / 2')).toBeVisible();
    expect(screen.getByText('달빛 이야기 5')).toBeVisible();

    await user.click(screen.getByRole('button', { name: '검색어 지우기' }));
    expect(searchInput).toHaveValue('');
    expect(screen.getByText('달빛 이야기 1')).toBeVisible();
  });

  it('이주의 문장 탭에서 책 수록 문장과 좋아요·출처를 표시한다', async () => {
    const user = userEvent.setup();
    let liked = false;
    server.use(
      http.get('*/api/v1/sentences/feed', () =>
        HttpResponse.json({
          weekStartDate: '2026-08-03',
          items: [
            {
              bookContentId: 101,
              content: '달빛 아래에서 문장이 조용히 빛났다.',
              authorNickname: '시안',
              bookId: 18,
              bossName: '달빛 아래 도서관',
              likeCount: liked ? 143 : 142,
              liked,
            },
          ],
        }),
      ),
      http.put('*/api/v1/book-contents/101/like', () => {
        liked = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderLibraryPage();
    await user.click(screen.getByRole('tab', { name: '이주의 문장' }));

    expect(await screen.findByText(/달빛 아래에서 문장이 조용히 빛났다/)).toBeVisible();
    expect(screen.queryByText(/1위/)).not.toBeInTheDocument();
    const likeButton = screen.getByRole('button', { name: '좋아요, 현재 142개' });
    expect(likeButton).toHaveTextContent('♡');
    await user.click(likeButton);
    expect(await screen.findByRole('button', { name: '좋아요 취소, 현재 143개' })).toHaveTextContent(
      '♥',
    );
    expect(screen.getByText('시안')).toBeVisible();
    expect(screen.getByRole('link', { name: '출처 · 달빛 아래 도서관' })).toHaveAttribute(
      'href',
      '/library/books/18',
    );
  });

  it('모아 볼 문장이 없으면 주차 설명 없이 간결한 빈 상태를 표시한다', async () => {
    const user = userEvent.setup();
    server.use(
      http.get('*/api/v1/sentences/feed', () =>
        HttpResponse.json({ weekStartDate: '2026-08-03', items: [] }),
      ),
    );

    renderLibraryPage();
    await user.click(screen.getByRole('tab', { name: '이주의 문장' }));

    expect(await screen.findByText('아직 모아 볼 문장이 없어요')).toBeVisible();
    expect(screen.queryByText('8월 첫째 주 문장')).not.toBeInTheDocument();
    expect(screen.queryByText('문장이 선정되면 이곳에 표시됩니다.')).not.toBeInTheDocument();
  });

  it('도서관과 이야기의 전당의 문장 화면 이름이 겹치지 않는다', async () => {
    renderLibraryPage();

    // 도서관의 주간 선정 문장은 "이주의 문장" 탭으로 노출한다.
    expect(await screen.findByRole('tab', { name: '이주의 문장' })).toBeVisible();
  });

  it('API가 없는 개인 책과 미완성 책에는 준비 안내를 표시한다', async () => {
    const user = userEvent.setup();
    renderLibraryPage();

    await user.click(screen.getByRole('tab', { name: '개인 책' }));
    expect(screen.getByText('개인 책은 준비 중이에요')).toBeVisible();
    expect(
      screen.queryByText('관련 백엔드 API가 준비되면 이곳에서 확인할 수 있습니다.'),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: '미완성 책' }));
    expect(screen.getByText('미완성 책은 준비 중이에요')).toBeVisible();
    expect(
      screen.queryByText('관련 백엔드 API가 준비되면 이곳에서 확인할 수 있습니다.'),
    ).not.toBeInTheDocument();
  });

  it('공동 책 조회 실패 시 재시도 안내를 표시한다', async () => {
    server.use(
      http.get('*/api/v1/library/books', () =>
        HttpResponse.json(
          { code: 'INTERNAL_ERROR', message: 'failed', fieldErrors: [] },
          { status: 500 },
        ),
      ),
    );

    renderLibraryPage();

    expect(await screen.findByRole('alert')).toHaveTextContent('도서관을 불러오지 못했습니다');
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeVisible();
  });
});
