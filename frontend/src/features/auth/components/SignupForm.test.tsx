import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import { server } from '@/mocks/server';

import { SignupForm } from './SignupForm';

function renderSignupForm(onSuccess = vi.fn()) {
  const queryClient = new QueryClient({
    defaultOptions: {
      mutations: {
        retry: false,
      },
    },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <SignupForm onSuccess={onSuccess} />
    </QueryClientProvider>,
  );

  return { onSuccess };
}

describe('SignupForm', () => {
  async function fillSignupForm(user: ReturnType<typeof userEvent.setup>) {
    await user.type(screen.getByLabelText('이메일'), 'test@example.com');
    await user.type(screen.getByLabelText('닉네임'), '테스트문장가');
    await user.type(screen.getByLabelText('비밀번호'), 'password123');
    await user.type(screen.getByLabelText('비밀번호 확인'), 'password123');
  }

  async function verifyEmail(user: ReturnType<typeof userEvent.setup>) {
    await user.click(screen.getByRole('button', { name: '인증 코드 발송' }));
    await screen.findByText('인증 코드를 이메일로 발송했습니다.');
    await user.type(screen.getByLabelText('인증 코드'), '123456');
    await user.click(screen.getByRole('button', { name: '인증 확인' }));
    await screen.findByText('이메일 인증이 완료되었습니다.');
  }

  async function agreeToCopyright(user: ReturnType<typeof userEvent.setup>) {
    const checkbox = await screen.findByRole('checkbox', {
      name: '[필수] 위 저작권 이용 동의서 내용을 확인했으며 이에 동의합니다.',
    });
    await user.click(checkbox);
  }

  it('현재 저작권 이용 동의서의 제목, 버전, 내용을 표시한다', async () => {
    renderSignupForm();

    expect(await screen.findByText('잇담 저작권 이용 동의서')).toBeInTheDocument();
    expect(screen.getByText('버전 1.0')).toBeInTheDocument();
    expect(
      screen.getByText(
        '공동 창작에 제출한 문장을 서비스 내 책 생성 및 전시에 이용하는 것에 동의합니다.',
      ),
    ).toBeInTheDocument();
  });

  it('이메일 인증 전에는 회원가입 요청을 보낼 수 없다', async () => {
    const user = userEvent.setup();
    renderSignupForm();

    await fillSignupForm(user);

    expect(screen.getByRole('button', { name: '회원가입' })).toBeDisabled();
  });

  it('이메일 인증 후 회원가입에 성공한다', async () => {
    const user = userEvent.setup();
    const { onSuccess } = renderSignupForm();

    await fillSignupForm(user);
    await verifyEmail(user);
    expect(screen.getByRole('button', { name: '회원가입' })).toBeDisabled();
    await agreeToCopyright(user);
    await user.click(screen.getByRole('button', { name: '회원가입' }));

    await waitFor(() => {
      expect(onSuccess).toHaveBeenCalledOnce();
    });
  });

  it('정규화된 이메일과 인증 토큰으로 회원가입을 요청한다', async () => {
    let submittedBody:
      | {
          email?: string;
          verificationToken?: string;
          copyrightAgreementId?: number;
          copyrightAgreed?: boolean;
        }
      | undefined;
    server.use(
      http.post('*/api/v1/users', async ({ request }) => {
        submittedBody = (await request.json()) as typeof submittedBody;
        return HttpResponse.json(
          {
            id: 2,
            email: submittedBody?.email,
            nickname: '대소문자테스트',
            status: 'ACTIVE',
            role: 'USER',
            createdAt: new Date().toISOString(),
          },
          { status: 201 },
        );
      }),
    );
    const user = userEvent.setup();
    const { onSuccess } = renderSignupForm();

    await user.type(screen.getByLabelText('이메일'), 'Test@Example.com');
    await user.type(screen.getByLabelText('닉네임'), '대소문자테스트');
    await user.type(screen.getByLabelText('비밀번호'), 'password123');
    await user.type(screen.getByLabelText('비밀번호 확인'), 'password123');
    await verifyEmail(user);
    await agreeToCopyright(user);
    await user.click(screen.getByRole('button', { name: '회원가입' }));

    await waitFor(() => expect(onSuccess).toHaveBeenCalledOnce());
    expect(submittedBody).toMatchObject({
      email: 'test@example.com',
      verificationToken: 'mock-verification-token-test@example.com',
      copyrightAgreementId: 1,
      copyrightAgreed: true,
    });
  });

  it('인증 코드 불일치와 재발송 쿨다운을 안내한다', async () => {
    const user = userEvent.setup();
    renderSignupForm();

    await user.type(screen.getByLabelText('이메일'), 'mismatch@example.com');
    await user.click(screen.getByRole('button', { name: '인증 코드 발송' }));

    expect(await screen.findByRole('button', { name: '재발송 60초' })).toBeDisabled();

    await user.type(screen.getByLabelText('인증 코드'), '000000');
    await user.click(screen.getByRole('button', { name: '인증 확인' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('인증 코드가 일치하지 않습니다.');
  });

  it('만료된 인증 코드를 안내한다', async () => {
    server.use(
      http.post('*/api/v1/email-verifications', () =>
        HttpResponse.json({ expiresInSeconds: 0, resendCooldownSeconds: 0 }, { status: 202 }),
      ),
    );
    const user = userEvent.setup();
    renderSignupForm();

    await user.type(screen.getByLabelText('이메일'), 'expired@example.com');
    await user.click(screen.getByRole('button', { name: '인증 코드 발송' }));

    expect(
      await screen.findByText('인증 코드가 만료되었습니다. 새 코드를 발송해 주세요.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '인증 확인' })).toBeDisabled();
  });

  it('발송 요청 제한 오류를 안내한다', async () => {
    server.use(
      http.post('*/api/v1/email-verifications', () =>
        HttpResponse.json(
          {
            occurredAt: new Date().toISOString(),
            code: 'RATE_LIMITED',
            message: 'Too many requests.',
            fieldErrors: [],
          },
          { status: 429 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderSignupForm();

    await user.type(screen.getByLabelText('이메일'), 'limited@example.com');
    await user.click(screen.getByRole('button', { name: '인증 코드 발송' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.',
    );
  });

  it('인증된 이메일을 수정하면 인증 상태가 초기화된다', async () => {
    const user = userEvent.setup();
    renderSignupForm();

    await user.type(screen.getByLabelText('이메일'), 'edit@example.com');
    await verifyEmail(user);

    expect(screen.getByLabelText('이메일')).toHaveAttribute('readonly');
    await user.click(screen.getByRole('button', { name: '이메일 수정' }));
    await user.clear(screen.getByLabelText('이메일'));
    await user.type(screen.getByLabelText('이메일'), 'changed@example.com');

    expect(screen.getByRole('button', { name: '회원가입' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '인증 코드 발송' })).toBeEnabled();
  });

  it('가입 토큰이 만료되면 인증 상태를 초기화하고 재인증을 안내한다', async () => {
    server.use(
      http.post('*/api/v1/users', () =>
        HttpResponse.json(
          {
            occurredAt: new Date().toISOString(),
            code: 'EMAIL_NOT_VERIFIED',
            message: 'Email verification is required.',
            fieldErrors: [],
          },
          { status: 403 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderSignupForm();

    await user.type(screen.getByLabelText('이메일'), 'expired-token@example.com');
    await user.type(screen.getByLabelText('닉네임'), '만료토큰사용자');
    await user.type(screen.getByLabelText('비밀번호'), 'password123');
    await user.type(screen.getByLabelText('비밀번호 확인'), 'password123');
    await verifyEmail(user);
    await agreeToCopyright(user);
    await user.click(screen.getByRole('button', { name: '회원가입' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '이메일 인증이 만료되었거나 유효하지 않습니다. 다시 인증해 주세요.',
    );
    expect(screen.getByLabelText('이메일')).not.toHaveAttribute('readonly');
    expect(screen.getByLabelText('이메일')).toHaveValue('expired-token@example.com');
    expect(screen.getByLabelText('닉네임')).toHaveValue('만료토큰사용자');
    expect(screen.getByLabelText('비밀번호')).toHaveValue('password123');
    expect(screen.getByLabelText('비밀번호 확인')).toHaveValue('password123');
    expect(screen.getByRole('button', { name: '회원가입' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '인증 코드 발송' })).toBeEnabled();
  });

  it('일반 403 오류는 이메일 인증 만료로 처리하지 않는다', async () => {
    server.use(
      http.post('*/api/v1/users', () =>
        HttpResponse.json(
          {
            occurredAt: new Date().toISOString(),
            code: 'FORBIDDEN',
            message: '요청을 처리할 수 없습니다.',
            fieldErrors: [],
          },
          { status: 403 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderSignupForm();

    await user.type(screen.getByLabelText('이메일'), 'csrf-forbidden@example.com');
    await user.type(screen.getByLabelText('닉네임'), '권한오류사용자');
    await user.type(screen.getByLabelText('비밀번호'), 'password123');
    await user.type(screen.getByLabelText('비밀번호 확인'), 'password123');
    await verifyEmail(user);
    await agreeToCopyright(user);
    await user.click(screen.getByRole('button', { name: '회원가입' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('요청을 처리할 수 없습니다.');
    expect(screen.getByLabelText('이메일')).toHaveAttribute('readonly');
    expect(screen.getByRole('button', { name: '회원가입' })).toBeEnabled();
  });

  it('동의서가 변경되면 최신 내용을 다시 조회하고 재동의를 요구한다', async () => {
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
      http.post('*/api/v1/users', () =>
        HttpResponse.json(
          {
            occurredAt: new Date().toISOString(),
            code: 'COPYRIGHT_AGREEMENT_MISMATCH',
            message: 'Current agreement changed.',
            fieldErrors: [],
          },
          { status: 409 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderSignupForm();

    await fillSignupForm(user);
    await verifyEmail(user);
    await agreeToCopyright(user);
    await user.click(screen.getByRole('button', { name: '회원가입' }));

    expect(await screen.findByText('변경된 동의서')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent(
      '동의서가 변경되었습니다. 최신 내용을 확인하고 다시 동의해 주세요.',
    );
    expect(screen.getByRole('checkbox')).not.toBeChecked();
    expect(screen.getByRole('button', { name: '회원가입' })).toBeDisabled();
  });

  it('동의서 조회에 실패하면 가입을 막고 다시 조회할 수 있다', async () => {
    let shouldFail = true;
    server.use(
      http.get('*/api/v1/copyright-agreements/current', () => {
        if (shouldFail) {
          shouldFail = false;
          return HttpResponse.json(
            {
              occurredAt: new Date().toISOString(),
              code: 'NOT_FOUND',
              message: 'Current agreement not found.',
              fieldErrors: [],
            },
            { status: 404 },
          );
        }

        return HttpResponse.json({
          id: 1,
          version: '1.0',
          title: '다시 불러온 동의서',
          content: '동의서 내용',
          effectiveAt: '2026-08-01T00:00:00Z',
          validMonths: 12,
        });
      }),
    );
    const user = userEvent.setup();
    renderSignupForm();

    expect(
      await screen.findByText('저작권 이용 동의서를 불러오지 못했습니다.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '회원가입' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: '동의서 다시 불러오기' }));

    expect(await screen.findByText('다시 불러온 동의서')).toBeInTheDocument();
    expect(screen.getByRole('checkbox')).not.toBeChecked();
  });
});
