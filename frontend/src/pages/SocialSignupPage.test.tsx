import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { server } from '@/mocks/server';

import { SocialSignupPage } from './SocialSignupPage';

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/oauth/signup']}>
        <Routes>
          <Route path="/oauth/signup" element={<SocialSignupPage />} />
          <Route path="/login" element={<h1>로그인 화면</h1>} />
          <Route path="/" element={<h1>홈 화면</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('SocialSignupPage', () => {
  it('shows onboarding only when an OAuth registration is pending', async () => {
    server.use(
      http.get('*/api/v1/auth/social-registration', () => HttpResponse.json({ pending: true })),
    );

    renderPage();

    expect(await screen.findByRole('heading', { name: '소셜 회원가입 완료' })).toBeInTheDocument();
    expect(screen.getByLabelText('닉네임')).toBeInTheDocument();
  });

  it('redirects direct access without a pending profile to login', async () => {
    server.use(
      http.get('*/api/v1/auth/social-registration', () => HttpResponse.json({ pending: false })),
    );

    renderPage();

    expect(await screen.findByRole('heading', { name: '로그인 화면' })).toBeInTheDocument();
  });

  it('navigates home after registration completes', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(await screen.findByLabelText('닉네임'), '완료닉네임');
    await user.click(
      screen.getByRole('checkbox', {
        name: '[필수] 위 저작권 이용 동의서 내용을 확인했으며 이에 동의합니다.',
      }),
    );
    await user.click(screen.getByRole('button', { name: '가입 완료' }));

    expect(await screen.findByRole('heading', { name: '홈 화면' })).toBeInTheDocument();
  });

  it('offers retry when pending-state lookup fails', async () => {
    server.use(
      http.get('*/api/v1/auth/social-registration', () =>
        HttpResponse.json(
          { code: 'INTERNAL_ERROR', message: 'failed', fieldErrors: [] },
          { status: 500 },
        ),
      ),
    );

    renderPage();

    expect(await screen.findByRole('button', { name: '다시 시도' })).toBeInTheDocument();
  });
});
