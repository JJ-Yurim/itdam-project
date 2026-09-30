import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { SocialLoginButtons } from './SocialLoginButtons';

describe('SocialLoginButtons', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('카카오·네이버·구글 진입 링크를 API 베이스 아래 oauth2 경로로 연결한다', () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080/api');

    render(<SocialLoginButtons />);

    expect(screen.getByRole('link', { name: '카카오로 계속하기' })).toHaveAttribute(
      'href',
      'http://localhost:8080/api/oauth2/authorization/kakao',
    );
    expect(screen.getByRole('link', { name: '네이버로 계속하기' })).toHaveAttribute(
      'href',
      'http://localhost:8080/api/oauth2/authorization/naver',
    );
    expect(screen.getByRole('link', { name: '구글로 계속하기' })).toHaveAttribute(
      'href',
      'http://localhost:8080/api/oauth2/authorization/google',
    );
  });

  it('배포처럼 상대 경로 베이스여도 진입 링크가 /api 아래로 생성된다', () => {
    vi.stubEnv('VITE_API_BASE_URL', '/api');

    render(<SocialLoginButtons />);

    expect(screen.getByRole('link', { name: '카카오로 계속하기' })).toHaveAttribute(
      'href',
      '/api/oauth2/authorization/kakao',
    );
  });

  it('베이스가 비어 있으면 /api로 폴백한다', () => {
    vi.stubEnv('VITE_API_BASE_URL', '');

    render(<SocialLoginButtons />);

    expect(screen.getByRole('link', { name: '카카오로 계속하기' })).toHaveAttribute(
      'href',
      '/api/oauth2/authorization/kakao',
    );
  });

  it('베이스의 끝 슬래시를 제거해 이중 슬래시를 막는다', () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080/api/');

    render(<SocialLoginButtons />);

    expect(screen.getByRole('link', { name: '카카오로 계속하기' })).toHaveAttribute(
      'href',
      'http://localhost:8080/api/oauth2/authorization/kakao',
    );
  });
});
