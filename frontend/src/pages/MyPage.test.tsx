import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { server } from '@/mocks/server';

import { MyPage } from './MyPage';

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

function renderMyPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const router = createMemoryRouter(
    [
      { path: '/my-page', element: <MyPage /> },
      { path: '/', element: <div>홈 화면</div> },
    ],
    { initialEntries: ['/my-page'] },
  );

  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );

  return router;
}

describe('MyPage', () => {
  it('프로필 정보와 가입일을 표시한다', async () => {
    loginAsMember();
    renderMyPage();

    expect(await screen.findByText('문장여행자')).toBeVisible();
    expect(screen.getByText('writer@example.com')).toBeVisible();
    expect(screen.getByText(/가입일: 2026년 7월/)).toBeVisible();
  });

  it('창작 활동 요약 3종을 표시한다', async () => {
    loginAsMember();
    renderMyPage();

    // 문장 = 평가 완료 2 + 미평가 2
    const summary = await screen.findByRole('heading', { name: '창작 활동 요약' });
    expect(summary).toBeVisible();

    await waitFor(() => {
      expect(screen.getByText('✍️ 문장').nextElementSibling).toHaveTextContent('4');
    });
    expect(screen.getByText('⚔️ 원정').nextElementSibling).toHaveTextContent('3');
    expect(screen.getByText('📖 기여한 책').nextElementSibling).toHaveTextContent('1');

    expect(screen.getByRole('link', { name: '내 서고에서 자세히 보기' })).toHaveAttribute(
      'href',
      '/archive',
    );
  });

  it('설정 링크 두 개를 표시한다', async () => {
    loginAsMember();
    renderMyPage();

    expect(await screen.findByRole('link', { name: /알림 설정/ })).toHaveAttribute(
      'href',
      '/settings/notifications',
    );
    expect(screen.getByRole('link', { name: /서비스 설정/ })).toHaveAttribute(
      'href',
      '/settings/service',
    );
  });

  it('로그아웃하면 홈으로 이동한다', async () => {
    const user = userEvent.setup();

    loginAsMember();
    server.use(
      http.get('*/api/v1/csrf', () => HttpResponse.json({ token: 'csrf' })),
      http.post('*/api/v1/auth/sessions/current', () => new HttpResponse(null, { status: 204 })),
    );

    const router = renderMyPage();

    await user.click(await screen.findByRole('button', { name: '로그아웃' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
    expect(await screen.findByText('홈 화면')).toBeVisible();
  });

  it('비로그인 상태면 로그인 안내를 표시한다', async () => {
    renderMyPage();

    expect(await screen.findByText('로그인하면 마이페이지를 볼 수 있어요')).toBeVisible();
    expect(screen.getByRole('link', { name: '로그인 하기' })).toHaveAttribute('href', '/login');
  });
});
