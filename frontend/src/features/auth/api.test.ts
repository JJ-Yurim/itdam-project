import { HttpResponse, http } from 'msw';
import { describe, expect, it } from 'vitest';

import { server } from '@/mocks/server';

import { completeSocialRegistration, getCurrentUser, getSocialRegistrationPending } from './api';

describe('getCurrentUser', () => {
  it('세션이 유효하면 현재 사용자를 반환한다', async () => {
    const currentUser = {
      id: 1,
      email: 'writer@example.com',
      nickname: '문장여행자',
      status: 'ACTIVE' as const,
      role: 'USER' as const,
      createdAt: '2026-07-30T00:00:00Z',
    };

    server.use(
      http.get('*/api/v1/users/me', () => {
        return HttpResponse.json(currentUser);
      }),
    );

    await expect(getCurrentUser()).resolves.toEqual(currentUser);
  });
  it('세션이 없으면 null을 반환한다', async () => {
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

    await expect(getCurrentUser()).resolves.toBeNull();
  });
});

describe('social registration API', () => {
  it('returns whether an OAuth registration is pending', async () => {
    server.use(
      http.get('*/api/v1/auth/social-registration', () => HttpResponse.json({ pending: true })),
    );

    await expect(getSocialRegistrationPending()).resolves.toEqual({ pending: true });
  });

  it('submits the chosen nickname and copyright consent', async () => {
    let submittedBody: unknown;
    const createdUser = {
      id: 7,
      email: 'social@example.com',
      nickname: '직접고른닉네임',
      status: 'ACTIVE' as const,
      role: 'USER' as const,
      createdAt: '2026-08-07T00:00:00Z',
    };
    server.use(
      http.post('*/api/v1/auth/social-registration', async ({ request }) => {
        submittedBody = await request.json();
        return HttpResponse.json(createdUser, { status: 201 });
      }),
    );

    await expect(
      completeSocialRegistration({
        nickname: '직접고른닉네임',
        copyrightAgreementId: 1,
        copyrightAgreed: true,
      }),
    ).resolves.toEqual(createdUser);
    expect(submittedBody).toEqual({
      nickname: '직접고른닉네임',
      copyrightAgreementId: 1,
      copyrightAgreed: true,
    });
  });
});
