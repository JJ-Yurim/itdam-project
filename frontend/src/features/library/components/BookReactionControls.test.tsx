import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { AuthGateProvider } from '@/features/auth';
import { ToastProvider } from '@/shared/components';
import { server } from '@/mocks/server';

import { BookReactionControls } from './BookReactionControls';

describe('BookReactionControls', () => {
  it('비회원이 좋아요를 누르면 API를 호출하지 않고 로그인 팝업을 표시한다', async () => {
    const user = userEvent.setup();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    let likeRequestCount = 0;
    server.use(
      http.post('*/api/v1/library/books/18/likes', () => {
        likeRequestCount += 1;
        return new HttpResponse(null, { status: 204 });
      }),
    );

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/library']}>
          <AuthGateProvider>
            <ToastProvider>
              <BookReactionControls
                bookId={18}
                likeCount={3}
                liked={false}
                bookmarked={false}
              />
            </ToastProvider>
          </AuthGateProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await user.click(await screen.findByRole('button', { name: '좋아요, 현재 3개' }));

    expect(screen.getByRole('dialog')).toHaveTextContent('로그인이 필요한 기능이에요');
    expect(likeRequestCount).toBe(0);
  });
});
