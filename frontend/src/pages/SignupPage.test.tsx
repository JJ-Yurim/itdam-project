import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { SignupPage } from './SignupPage';

function renderSignupPage() {
  const queryClient = new QueryClient();

  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/signup']}>
        <SignupPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('SignupPage', () => {
  it('카카오·네이버·구글 소셜 로그인 진입 버튼을 노출한다', () => {
    renderSignupPage();

    expect(screen.getByRole('link', { name: '카카오로 계속하기' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '네이버로 계속하기' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '구글로 계속하기' })).toBeInTheDocument();
  });
});
