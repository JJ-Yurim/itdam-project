import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { LoginPage } from './LoginPage';

function renderLoginPage(entry: string) {
  const queryClient = new QueryClient();

  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[entry]}>
        <LoginPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('LoginPage', () => {
  it('소셜 로그인 실패 코드를 사용자용 한국어 사유로 안내한다', async () => {
    renderLoginPage('/login?socialError=social_email_already_registered');

    expect(await screen.findByRole('alert')).toHaveTextContent('소셜 로그인에 실패했어요');
    expect(screen.getByRole('alert')).toHaveTextContent(
      '이미 다른 로그인 방식으로 가입된 이메일입니다.',
    );
  });

  it('카카오·네이버·구글 소셜 로그인 진입 버튼을 노출한다', () => {
    renderLoginPage('/login');

    expect(screen.getByRole('link', { name: '카카오로 계속하기' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '네이버로 계속하기' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '구글로 계속하기' })).toBeInTheDocument();
  });

  it('알 수 없는 소셜 로그인 실패 코드는 안전한 기본 문구로 안내한다', async () => {
    renderLoginPage('/login?socialError=unexpected_error');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '소셜 로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.',
    );
  });

  it('소셜 가입 대기 세션이 만료되면 다시 로그인을 안내한다', async () => {
    renderLoginPage('/login?socialError=social_signup_expired');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '가입 정보가 만료되었습니다. 소셜 로그인을 다시 시작해 주세요.',
    );
  });
});
