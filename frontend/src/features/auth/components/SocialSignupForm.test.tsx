import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import { server } from '@/mocks/server';

import { SocialSignupForm } from './SocialSignupForm';

function renderForm(options?: { onSuccess?: () => void; onSessionExpired?: () => void }) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const onSuccess = options?.onSuccess ?? vi.fn();
  const onSessionExpired = options?.onSessionExpired ?? vi.fn();

  render(
    <QueryClientProvider client={queryClient}>
      <SocialSignupForm onSuccess={onSuccess} onSessionExpired={onSessionExpired} />
    </QueryClientProvider>,
  );

  return { onSuccess, onSessionExpired };
}

async function fillAndAgree(user: ReturnType<typeof userEvent.setup>, nickname = '직접고른닉네임') {
  await user.type(screen.getByLabelText('닉네임'), nickname);
  await user.click(
    await screen.findByRole('checkbox', {
      name: '[필수] 위 저작권 이용 동의서 내용을 확인했으며 이에 동의합니다.',
    }),
  );
}

describe('SocialSignupForm', () => {
  it('requires copyright consent before completion', async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(screen.getByLabelText('닉네임'), '닉네임');

    expect(await screen.findByRole('button', { name: '가입 완료' })).toBeDisabled();
  });

  it('submits the chosen nickname and current copyright consent', async () => {
    let submittedBody: unknown;
    server.use(
      http.post('*/api/v1/auth/social-registration', async ({ request }) => {
        submittedBody = await request.json();
        return HttpResponse.json(
          {
            id: 7,
            email: 'social@example.com',
            nickname: '직접고른닉네임',
            status: 'ACTIVE',
            role: 'USER',
            createdAt: '2026-08-07T00:00:00Z',
          },
          { status: 201 },
        );
      }),
    );
    const user = userEvent.setup();
    const { onSuccess } = renderForm();

    await fillAndAgree(user);
    await user.click(screen.getByRole('button', { name: '가입 완료' }));

    await waitFor(() => expect(onSuccess).toHaveBeenCalledOnce());
    expect(submittedBody).toEqual({
      nickname: '직접고른닉네임',
      copyrightAgreementId: 1,
      copyrightAgreed: true,
    });
  });

  it('maps a conflict to the nickname field', async () => {
    server.use(
      http.post('*/api/v1/auth/social-registration', () =>
        HttpResponse.json(
          {
            occurredAt: new Date().toISOString(),
            code: 'CONFLICT',
            message: 'Nickname is already in use.',
            fieldErrors: [],
          },
          { status: 409 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderForm();

    await fillAndAgree(user, '중복닉네임');
    await user.click(screen.getByRole('button', { name: '가입 완료' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('이미 사용 중인 닉네임입니다.');
  });

  it('reloads a changed agreement and requires consent again', async () => {
    let agreementRequestCount = 0;
    server.use(
      http.get('*/api/v1/copyright-agreements/current', () => {
        agreementRequestCount += 1;
        return HttpResponse.json({
          id: agreementRequestCount === 1 ? 1 : 2,
          version: agreementRequestCount === 1 ? '1.0' : '1.1',
          title: agreementRequestCount === 1 ? '기존 동의서' : '변경된 동의서',
          content: agreementRequestCount === 1 ? '기존 내용' : '변경된 내용',
          effectiveAt: '2026-08-01T00:00:00Z',
          validMonths: 12,
        });
      }),
      http.post('*/api/v1/auth/social-registration', () =>
        HttpResponse.json(
          {
            occurredAt: new Date().toISOString(),
            code: 'COPYRIGHT_AGREEMENT_MISMATCH',
            message: 'The copyright agreement has changed.',
            fieldErrors: [],
          },
          { status: 409 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderForm();

    await fillAndAgree(user);
    await user.click(screen.getByRole('button', { name: '가입 완료' }));

    expect(await screen.findByText('변경된 동의서')).toBeInTheDocument();
    expect(screen.getByRole('checkbox')).not.toBeChecked();
    expect(screen.getByRole('button', { name: '가입 완료' })).toBeDisabled();
  });

  it('reports an expired pending session to the page', async () => {
    server.use(
      http.post('*/api/v1/auth/social-registration', () =>
        HttpResponse.json(
          {
            occurredAt: new Date().toISOString(),
            code: 'UNAUTHORIZED',
            message: 'Authentication is required.',
            fieldErrors: [],
          },
          { status: 401 },
        ),
      ),
    );
    const user = userEvent.setup();
    const { onSessionExpired } = renderForm();

    await fillAndAgree(user);
    await user.click(screen.getByRole('button', { name: '가입 완료' }));

    await waitFor(() => expect(onSessionExpired).toHaveBeenCalledOnce());
  });
});
