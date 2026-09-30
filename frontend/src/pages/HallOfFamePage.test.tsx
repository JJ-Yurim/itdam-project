import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import { HttpResponse, http } from 'msw';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { server } from '@/mocks/server';

import { HallOfFamePage } from './HallOfFamePage';

function overrideFeed(items: { bookContentId: number; content: string; likeCount: number }[]) {
  server.use(
    http.get('*/api/v1/sentences/feed', () =>
      HttpResponse.json({
        weekStartDate: '2026-08-03',
        items: items.map((item) => ({
          bookContentId: item.bookContentId,
          content: item.content,
          authorNickname: '작가',
          bookId: 18,
          bossName: '보스',
          likeCount: item.likeCount,
          liked: false,
        })),
      }),
    ),
  );
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const router = createMemoryRouter([{ path: '/hall-of-fame', element: <HallOfFamePage /> }], {
    initialEntries: ['/hall-of-fame'],
  });

  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );

  return router;
}

describe('HallOfFamePage', () => {
  it('선정 문장과 출처 링크를 표시한다', async () => {
    renderPage();

    expect(
      await screen.findByText(/안개가 낮게 깔린 숲에서 첫 번째 문장이 조용히 빛났다/),
    ).toBeVisible();
    expect(screen.getByText(/8월 첫째 주 좋아요를 가장 많이 받은 문장 10개/)).toBeVisible();
    expect(screen.getByText(/문장여행자 · 안개의 수호자/)).toBeVisible();
    // 탈퇴한 작성자는 비식별 표시한다.
    expect(screen.getByText(/탈퇴한 작가 · 기억의 파수꾼/)).toBeVisible();

    const bookLinks = screen.getAllByRole('link', { name: '책 보러 가기' });
    expect(bookLinks[0]).toHaveAttribute('href', '/library/books/18');
  });

  it('좋아요가 많은 순으로 순위를 매겨 표시한다', async () => {
    overrideFeed([
      { bookContentId: 1, content: '세 번째로 인기 있는 문장', likeCount: 5 },
      { bookContentId: 2, content: '가장 인기 있는 문장', likeCount: 30 },
      { bookContentId: 3, content: '두 번째로 인기 있는 문장', likeCount: 12 },
    ]);

    renderPage();

    const list = await screen.findByRole('list', { name: '이번 주 전당에 오른 문장' });
    const items = within(list).getAllByRole('listitem');

    expect(items).toHaveLength(3);
    expect(items[0]).toHaveTextContent('가장 인기 있는 문장');
    expect(items[0]).toHaveTextContent('주간 1위');
    expect(items[1]).toHaveTextContent('두 번째로 인기 있는 문장');
    expect(items[1]).toHaveTextContent('주간 2위');
    expect(items[2]).toHaveTextContent('세 번째로 인기 있는 문장');
    expect(items[2]).toHaveTextContent('주간 3위');
  });

  it('선정 문장이 10개를 넘으면 상위 10개만 표시한다', async () => {
    overrideFeed(
      Array.from({ length: 14 }, (_, index) => ({
        bookContentId: index + 1,
        content: `문장 ${index + 1}`,
        likeCount: 100 - index,
      })),
    );

    renderPage();

    const list = await screen.findByRole('list', { name: '이번 주 전당에 오른 문장' });

    expect(within(list).getAllByRole('listitem')).toHaveLength(10);
    expect(list).toHaveTextContent('주간 10위');
    expect(list).not.toHaveTextContent('주간 11위');
    expect(within(list).queryByText('문장 11')).not.toBeInTheDocument();
  });

  it('좋아요는 개수만 보여주고 누를 수 없다', async () => {
    renderPage();

    await screen.findByRole('list', { name: '이번 주 전당에 오른 문장' });

    // 좋아요 토글 버튼이 없어야 한다. 남는 버튼은 없다.
    expect(screen.queryByRole('button', { name: /좋아요/ })).not.toBeInTheDocument();
    expect(screen.getByText('7')).toBeVisible();
  });

  it('선정 문장이 없으면 빈 상태를 표시한다', async () => {
    overrideFeed([]);

    renderPage();

    expect(await screen.findByText('이번 주 전당에 오른 문장이 아직 없어요')).toBeVisible();
  });

  it('조회에 실패하면 오류와 재시도 버튼을 표시한다', async () => {
    server.use(
      http.get('*/api/v1/sentences/feed', () =>
        HttpResponse.json({ code: 'INTERNAL_ERROR', message: '조회 실패' }, { status: 500 }),
      ),
    );

    renderPage();

    expect(await screen.findByRole('alert')).toHaveTextContent('이야기의 전당을 불러오지 못했어요');
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeVisible();
  });
});
