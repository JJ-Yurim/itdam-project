import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import { server } from '@/mocks/server';

import { LoginForm } from './LoginForm';

function renderLoginForm(onSuccess = vi.fn()) {
  const queryClient = new QueryClient({
    defaultOptions: {
      mutations: {
        retry: false,
      },
    },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <LoginForm onSuccess={onSuccess} />
    </QueryClientProvider>,
  );

  return { onSuccess };
}

describe('LoginForm', () => {
  it('올바르지 않은 이메일을 입력하면 검증 오류를 표시한다', async () => {
    const user = userEvent.setup();
    const { onSuccess } = renderLoginForm();

    await user.type(screen.getByLabelText('이메일'), 'demo');
    await user.type(screen.getByLabelText('비밀번호'), 'password123');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    expect(await screen.findByText('올바른 이메일 형식을 입력해 주세요.')).toBeInTheDocument();
    expect(onSuccess).not.toHaveBeenCalled();
  });

  it('인증 실패 시 서버 영문 메시지 대신 한국어 사유를 표시한다', async () => {
    server.use(
      http.post('*/api/v1/auth/sessions', () =>
        HttpResponse.json(
          {
            occurredAt: '2026-08-05T00:00:00Z',
            code: 'UNAUTHORIZED',
            message: 'Invalid email or password.',
            fieldErrors: [],
          },
          { status: 401 },
        ),
      ),
    );
    const user = userEvent.setup();
    const { onSuccess } = renderLoginForm();

    await user.type(screen.getByLabelText('이메일'), 'writer@example.com');
    await user.type(screen.getByLabelText('비밀번호'), 'wrong-password');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('로그인하지 못했어요');
    expect(screen.getByRole('alert')).toHaveTextContent(
      '이메일 또는 비밀번호가 일치하지 않습니다.',
    );
    expect(screen.queryByText('Invalid email or password.')).not.toBeInTheDocument();
    expect(onSuccess).not.toHaveBeenCalled();
  });
});
