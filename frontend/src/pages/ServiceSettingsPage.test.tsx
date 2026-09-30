import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { HttpResponse, http } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { server } from '@/mocks/server';

import { ServiceSettingsPage } from './ServiceSettingsPage';

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

  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ServiceSettingsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('ServiceSettingsPage', () => {
  it('회원이면 계정 정보와 약관 링크를 표시한다', async () => {
    loginAsMember();
    renderPage();

    expect(await screen.findByText('문장여행자')).toBeVisible();
    expect(screen.getByText('writer@example.com')).toBeVisible();
    expect(screen.getByRole('link', { name: /이용약관/ })).toHaveAttribute('href', '/terms');
    expect(screen.getByRole('link', { name: /개인정보처리방침/ })).toHaveAttribute(
      'href',
      '/privacy',
    );
    expect(screen.getByRole('link', { name: '‹ 마이페이지' })).toHaveAttribute('href', '/my-page');
  });

  it('비로그인이어도 약관 링크는 보이고 계정 정보에는 로그인 안내를 표시한다', async () => {
    renderPage();

    expect(await screen.findByText('로그인하면 계정 정보를 볼 수 있어요')).toBeVisible();
    expect(screen.getByRole('link', { name: /이용약관/ })).toBeVisible();
    expect(screen.getByRole('link', { name: /개인정보처리방침/ })).toBeVisible();
  });
});
