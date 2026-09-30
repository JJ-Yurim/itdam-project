import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { AuthGateProvider } from '@/features/auth';
import { ToastProvider } from '@/shared/components';
import { server } from '@/mocks/server';

import { BookDetailPage } from './BookDetailPage';

function renderBookDetailPage(initialEntry = '/library/books/18') {
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
      queries: {
        retry: false,
      },
    },
  });

  // 좋아요·북마크가 로그인 유도 게이트를 쓰므로 Provider가 필요하다.
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <AuthGateProvider>
          <ToastProvider>
            <Routes>
              <Route path="/library/books/:bookId" element={<BookDetailPage />} />
            </Routes>
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

describe('BookDetailPage', () => {
  it('책 상세에서 좋아요와 북마크를 변경한다', async () => {
    const user = userEvent.setup();

    loginAsMember();
    renderBookDetailPage();

    await user.click(await screen.findByRole('button', { name: '좋아요, 현재 12개' }));
    expect(await screen.findByRole('button', { name: '좋아요 취소, 현재 13개' })).toBeVisible();

    await user.click(screen.getByRole('button', { name: '북마크 해제' }));
    expect(await screen.findByRole('button', { name: '북마크 추가' })).toBeVisible();
  });

  it('공동 책 상세 정보와 수록 본문을 표시한다', async () => {
    renderBookDetailPage();

    expect(await screen.findByRole('heading', { name: '안개의 수호자' })).toBeInTheDocument();
    expect(screen.getByText('최종 클리어')).toBeInTheDocument();
    expect(screen.getByText('기여 문장')).toBeInTheDocument();
    expect(screen.getByText('3개')).toBeInTheDocument();
    expect(screen.getByText('참여 작가')).toBeInTheDocument();
    expect(screen.getByText('2명')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '레이드 정화 기록' })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '공동 소설집' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /공동 소설집 전체 읽기/ })).toHaveAttribute(
      'href',
      '/library/books/18/read',
    );
    expect(
      screen
        .getByRole('link', { name: /공동 소설집 전체 읽기/ })
        .compareDocumentPosition(screen.getByText('참여 작가')) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it('작품 소개와 기여 작가 정보를 표시한다', async () => {
    renderBookDetailPage();

    expect(await screen.findByRole('heading', { name: '작품 소개' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '기여한 모험가 동료들' })).toBeInTheDocument();
    const contributorList = screen.getByRole('list', { name: '기여 작가 전체 목록' });
    expect(within(contributorList).getAllByRole('listitem')).toHaveLength(2);
    expect(within(contributorList).getByText('문장여행자')).toBeInTheDocument();
    expect(within(contributorList).getByText('새벽작가')).toBeInTheDocument();
  });

  it('비회원 응답에서 ID가 숨겨진 서로 다른 기여 작가를 모두 표시한다', async () => {
    server.use(
      http.get('*/api/v1/library/books/:bookId', () =>
        HttpResponse.json({
          bookId: 18,
          bossRunId: 7,
          bossName: '안개의 수호자',
          coverImageUrl: null,
          completedAt: '2026-07-27T10:05:00Z',
          body: '함께 완성한 이야기입니다.',
          likeCount: 0,
          liked: false,
          bookmarked: false,
          contents: [
            {
              contentId: 101,
              sequenceNo: 1,
              contentType: 'USER_ORIGINAL',
              content: '첫 번째 문장',
              contributor: { userId: null, nickname: '별빛작가' },
              sourceSubmissionId: null,
              contribution: null,
            },
            {
              contentId: 102,
              sequenceNo: 2,
              contentType: 'USER_ORIGINAL',
              content: '두 번째 문장',
              contributor: { userId: null, nickname: '달빛작가' },
              sourceSubmissionId: null,
              contribution: null,
            },
          ],
        }),
      ),
    );

    renderBookDetailPage();

    expect(await screen.findByText('2명')).toBeInTheDocument();
    const contributorList = screen.getByRole('list', { name: '기여 작가 전체 목록' });
    expect(within(contributorList).getByText('별빛작가')).toBeInTheDocument();
    expect(within(contributorList).getByText('달빛작가')).toBeInTheDocument();
  });

  it('상세 API의 표지 이미지를 표시한다', async () => {
    server.use(
      http.get('*/api/v1/library/books/:bookId', () =>
        HttpResponse.json({
          bookId: 18,
          bossRunId: 7,
          bossName: '안개의 수호자',
          coverImageUrl: '/images/book-covers/mist.webp',
          completedAt: '2026-07-27T10:05:00Z',
          body: '안개 속에서 이야기가 시작됐다.',
          likeCount: 0,
          liked: false,
          bookmarked: false,
          contents: [],
        }),
      ),
    );

    renderBookDetailPage();

    expect(await screen.findByAltText('안개의 수호자 책 표지')).toHaveAttribute(
      'src',
      '/images/book-covers/mist.webp',
    );
  });

  it('본문과 기여자가 없으면 대체 안내를 표시한다', async () => {
    server.use(
      http.get('*/api/v1/library/books/:bookId', () =>
        HttpResponse.json({
          bookId: 18,
          bossRunId: 7,
          bossName: '안개의 수호자',
          coverImageUrl: null,
          completedAt: '2026-07-27T10:05:00Z',
          body: null,
          likeCount: 0,
          liked: false,
          bookmarked: false,
          contents: [],
        }),
      ),
    );

    renderBookDetailPage();

    expect(await screen.findByText('작품 소개가 준비 중입니다.')).toBeInTheDocument();
    expect(screen.getByText('표시할 기여 작가 정보가 없습니다.')).toBeInTheDocument();
  });

  it('잘못된 책 ID이면 오류 안내를 표시한다', () => {
    renderBookDetailPage('/library/books/not-number');

    expect(screen.getByRole('alert')).toHaveTextContent('잘못된 공동 책 주소입니다');
  });

  it('없는 책이면 도서관으로 돌아갈 길을 준다', async () => {
    server.use(
      http.get('*/api/v1/library/books/:bookId', () =>
        HttpResponse.json(
          {
            occurredAt: new Date().toISOString(),
            code: 'NOT_FOUND',
            message: '공동 책을 찾을 수 없습니다.',
            fieldErrors: [],
          },
          {
            status: 404,
          },
        ),
      ),
    );

    renderBookDetailPage();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '공동 책 상세를 불러오지 못했습니다',
    );
    // 404는 다시 시도해도 같은 결과다.
    expect(screen.getByRole('link', { name: '도서관으로' })).toHaveAttribute('href', '/library');
    expect(screen.queryByRole('button', { name: '다시 시도' })).not.toBeInTheDocument();
  });
});
