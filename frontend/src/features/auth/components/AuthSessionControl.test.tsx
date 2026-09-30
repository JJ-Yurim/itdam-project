import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { beforeAll, describe, expect, it } from 'vitest';

import { server } from '@/mocks/server';

import { AuthSessionControl } from './AuthSessionControl';

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function showModal() {
    this.open = true;
  };
  HTMLDialogElement.prototype.close = function close() {
    this.open = false;
  };
});

function renderAuthSessionControl() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
      mutations: {
        retry: false,
      },
    },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AuthSessionControl />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('AuthSessionControl', () => {
  it('로그인 사용자의 닉네임과 프로필 이미지를 함께 표시한다', async () => {
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

    renderAuthSessionControl();

    expect(
      await screen.findByRole('button', { name: '문장여행자 프로필 메뉴 열기' }),
    ).toBeInTheDocument();
  });

  it('세션이 없으면 로그인 링크를 표시한다', async () => {
    server.use(
      http.get('*/api/v1/users/me', () => {
        return HttpResponse.json(
          {
            code: 'UNAUTHORIZED',
            message: '로그인이 필요합니다.',
          },
          { status: 401 },
        );
      }),
    );

    renderAuthSessionControl();

    expect(await screen.findByRole('link', { name: '로그인' })).toBeInTheDocument();
  });

  it('세션이 유효하면 프로필 아바타 버튼을 표시한다', async () => {
    server.use(
      http.get('*/api/v1/users/me', () => {
        return HttpResponse.json({
          id: 1,
          email: 'writer@example.com',
          nickname: '문장여행자',
          status: 'ACTIVE',
          role: 'USER',
          createdAt: '2026-07-30T00:00:00Z',
        });
      }),
    );

    renderAuthSessionControl();

    expect(
      await screen.findByRole('button', { name: '문장여행자 프로필 메뉴 열기' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '로그아웃' })).not.toBeInTheDocument();
  });

  it('아바타를 누르면 드로어에 닉네임과 메뉴가 표시되고 닫기 버튼으로 닫힌다', async () => {
    const user = userEvent.setup();

    server.use(
      http.get('*/api/v1/users/me', () => {
        return HttpResponse.json({
          id: 1,
          email: 'writer@example.com',
          nickname: '문장여행자',
          status: 'ACTIVE',
          role: 'USER',
          createdAt: '2026-07-30T00:00:00Z',
        });
      }),
    );

    renderAuthSessionControl();

    await user.click(await screen.findByRole('button', { name: '문장여행자 프로필 메뉴 열기' }));

    expect(screen.getByText(/문장여행자 작가님/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /내 서고/ })).toHaveAttribute('href', '/archive');
    expect(screen.getByRole('link', { name: /마이 페이지/ })).toHaveAttribute('href', '/my-page');
    expect(screen.getByRole('button', { name: /로그아웃/ })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '프로필 메뉴 닫기' }));

    expect(screen.queryByText(/문장여행자 작가님/)).not.toBeInTheDocument();
  });

  it('드로어에서 로그아웃에 성공하면 로그인 링크를 표시한다', async () => {
    const user = userEvent.setup();

    // 로그아웃하면 서버 세션이 끊겨 이후 조회는 401이다. 로그인 경계에서 캐시를
    // 비우면 활성 쿼리가 다시 조회되므로, 목이 계속 로그인 상태를 주면 실제와 어긋난다.
    let loggedIn = true;

    server.use(
      http.get('*/api/v1/users/me', () => {
        if (!loggedIn) {
          return HttpResponse.json(
            { code: 'UNAUTHORIZED', message: 'Authentication is required.', fieldErrors: [] },
            { status: 401 },
          );
        }

        return HttpResponse.json({
          id: 1,
          email: 'writer@example.com',
          nickname: '문장여행자',
          status: 'ACTIVE',
          role: 'USER',
          createdAt: '2026-07-30T00:00:00Z',
        });
      }),
      http.get('*/api/v1/csrf', () => {
        return HttpResponse.json({
          token: 'logout-csrf-token',
        });
      }),
      http.post('*/api/v1/auth/sessions/current', () => {
        loggedIn = false;
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderAuthSessionControl();

    await user.click(await screen.findByRole('button', { name: '문장여행자 프로필 메뉴 열기' }));
    await user.click(screen.getByRole('button', { name: /로그아웃/ }));

    expect(await screen.findByRole('link', { name: '로그인' })).toBeInTheDocument();
  });

  it('사용자 조회에 실패하면 재시도와 로그인 경로를 표시한다', async () => {
    const user = userEvent.setup();
    let requestCount = 0;

    server.use(
      http.get('*/api/v1/users/me', () => {
        requestCount += 1;

        return HttpResponse.json(
          {
            code: 'INTERNAL_SERVER_ERROR',
            message: '사용자 조회에 실패했습니다.',
          },
          { status: 500 },
        );
      }),
    );

    renderAuthSessionControl();

    expect(await screen.findByRole('alert')).toHaveTextContent('사용자 조회에 실패했습니다.');

    expect(screen.getByRole('link', { name: '로그인' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '다시 시도' }));

    await waitFor(() => {
      expect(requestCount).toBe(2);
    });
  });
});
