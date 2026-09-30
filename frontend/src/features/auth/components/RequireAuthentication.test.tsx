import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { server } from '@/mocks/server';

import { RequireAuthentication } from './RequireAuthentication';

function renderProtectedRoute() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/private']}>
        <Routes>
          <Route path="/" element={<div>홈 화면</div>} />
          <Route
            path="/private"
            element={
              <RequireAuthentication>
                <div>회원 전용 화면</div>
              </RequireAuthentication>
            }
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('RequireAuthentication', () => {
  it('비회원의 직접 접근을 팝업으로 막고 닫으면 홈으로 이동한다', async () => {
    const user = userEvent.setup();
    renderProtectedRoute();

    expect(await screen.findByRole('dialog')).toHaveTextContent('로그인이 필요한 기능이에요');
    expect(screen.queryByText('회원 전용 화면')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '다음에 하기' }));
    expect(await screen.findByText('홈 화면')).toBeVisible();
  });

  it('회원은 인증 전용 화면을 이용한다', async () => {
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
    renderProtectedRoute();

    expect(await screen.findByText('회원 전용 화면')).toBeVisible();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
