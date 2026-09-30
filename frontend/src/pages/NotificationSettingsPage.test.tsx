import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';

import { server } from '@/mocks/server';

import { NotificationSettingsPage } from './NotificationSettingsPage';

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

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <NotificationSettingsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('NotificationSettingsPage', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('알림 토글 6개를 기본 켜짐 상태로 표시한다', async () => {
    loginAsMember();
    renderPage();

    const switches = await screen.findAllByRole('switch');

    expect(switches).toHaveLength(6);
    switches.forEach((toggle) => expect(toggle).toHaveAttribute('aria-checked', 'true'));
    expect(screen.getByRole('link', { name: '‹ 마이페이지' })).toHaveAttribute('href', '/my-page');
  });

  it('토글을 끄면 localStorage에 저장되고 다시 열어도 유지된다', async () => {
    const user = userEvent.setup();

    loginAsMember();
    const { unmount } = renderPage();

    await user.click(await screen.findByRole('switch', { name: '이주의 문장 선정 알림' }));

    expect(screen.getByRole('switch', { name: '이주의 문장 선정 알림' })).toHaveAttribute(
      'aria-checked',
      'false',
    );

    // 다시 방문해도 저장된 상태가 복원된다.
    unmount();
    renderPage();

    expect(
      await screen.findByRole('switch', { name: '이주의 문장 선정 알림' }),
    ).toHaveAttribute('aria-checked', 'false');
    expect(screen.getByRole('switch', { name: '원정 종료 알림' })).toHaveAttribute(
      'aria-checked',
      'true',
    );
  });

  it('비로그인 상태면 로그인 안내를 표시한다', async () => {
    renderPage();

    expect(await screen.findByText('로그인하면 알림을 설정할 수 있어요')).toBeVisible();
    expect(screen.getByRole('link', { name: '로그인 하기' })).toHaveAttribute('href', '/login');
  });
});
