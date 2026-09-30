import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { beforeAll, describe, expect, it } from 'vitest';

import { server } from '@/mocks/server';

import { ArchiveDetailPage } from './ArchiveDetailPage';

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function showModal() {
    this.open = true;
  };
  HTMLDialogElement.prototype.close = function close() {
    this.open = false;
  };
});

function renderArchiveDetail(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(
    [{ path: '/archive/:sentenceVersionId', element: <ArchiveDetailPage /> }],
    { initialEntries: [path] },
  );

  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );

  return router;
}

describe('ArchiveDetailPage', () => {
  it('평가 점수와 제출·공동 책 수록 이력을 표시한다', async () => {
    renderArchiveDetail('/archive/84');

    expect(await screen.findByRole('heading', { name: '문장 상세' })).toBeVisible();
    expect(screen.getByText('83.35')).toBeVisible();
    expect(screen.getByText('안개의 수호자 원정 제출')).toBeVisible();
    expect(screen.getByText('공동 책 #18 수록')).toBeVisible();
    expect(screen.getByText('평가 완료 문장은 수정할 수 없어요.')).toBeVisible();
    expect(screen.getByRole('link', { name: '관련 원정 보기' })).toHaveAttribute('href', '/boss/7');
    expect(screen.getByRole('link', { name: '공동 책 보기' })).toHaveAttribute(
      'href',
      '/library/books/18',
    );
  });

  it('잘못된 문장 ID는 오류 상태를 표시한다', () => {
    renderArchiveDetail('/archive/not-a-number');

    expect(screen.getByRole('alert')).toHaveTextContent('잘못된 문장 주소입니다');
  });

  it('삭제를 취소하면 상세 화면을 유지한다', async () => {
    const user = userEvent.setup();
    const router = renderArchiveDetail('/archive/84');

    await user.click(await screen.findByRole('button', { name: '문장 삭제' }));
    expect(screen.getByRole('dialog')).toHaveTextContent('공동 책의 문장 참조는 유지');
    await user.click(screen.getByRole('button', { name: '취소' }));

    expect(router.state.location.pathname).toBe('/archive/84');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('문장 삭제 성공 후 내 서고 목록으로 이동한다', async () => {
    const user = userEvent.setup();
    let deletedSentenceId = '';
    server.use(
      http.delete('*/api/v1/sentences/:sentenceId', ({ params }) => {
        deletedSentenceId = String(params.sentenceId);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const router = renderArchiveDetail('/archive/84');

    await user.click(await screen.findByRole('button', { name: '문장 삭제' }));
    await user.click(screen.getByRole('button', { name: '삭제하기' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/archive'));
    expect(deletedSentenceId).toBe('42');
  });

  it('문장 삭제 실패 시 오류를 표시하고 Modal을 유지한다', async () => {
    const user = userEvent.setup();
    server.use(
      http.delete('*/api/v1/sentences/:sentenceId', () =>
        HttpResponse.json({ code: 'INTERNAL_ERROR', message: '삭제 실패' }, { status: 500 }),
      ),
    );
    renderArchiveDetail('/archive/84');

    await user.click(await screen.findByRole('button', { name: '문장 삭제' }));
    await user.click(screen.getByRole('button', { name: '삭제하기' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('문장을 삭제하지 못했어요');
    expect(screen.getByRole('dialog')).toBeVisible();
  });
});
