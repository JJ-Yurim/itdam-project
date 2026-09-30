import { HttpResponse, http } from 'msw';

import type {
  CopyrightAgreementResponse,
  EmailVerificationConfirmRequest,
  EmailVerificationSendRequest,
  LoginRequest,
  SocialRegistrationRequest,
  SignupRequest,
  UserResponse,
} from '@/features/auth';

const currentCopyrightAgreement: CopyrightAgreementResponse = {
  id: 1,
  version: '1.0',
  title: '잇담 저작권 이용 동의서',
  content: '공동 창작에 제출한 문장을 서비스 내 책 생성 및 전시에 이용하는 것에 동의합니다.',
  effectiveAt: '2026-08-01T00:00:00Z',
  validMonths: 12,
};

interface MockUser extends UserResponse {
  password: string;
}

const users = new Map<string, MockUser>([
  [
    'writer@example.com',
    {
      id: 1,
      email: 'writer@example.com',
      nickname: '문장여행자',
      password: 'password123',
      status: 'ACTIVE',
      role: 'USER',
      createdAt: '2026-07-30T00:00:00Z',
    },
  ],
  // 관리 콘솔은 ADMIN 세션에서만 열리므로 목 환경에도 관리자 계정이 있어야 화면을 확인할 수 있다.
  [
    'admin@example.com',
    {
      id: 2,
      email: 'admin@example.com',
      nickname: '운영자',
      password: 'password123',
      status: 'ACTIVE',
      role: 'ADMIN',
      createdAt: '2026-07-30T00:00:00Z',
    },
  ],
]);

let currentUser: MockUser | null = null;
let socialRegistrationPending = true;
const issuedVerificationCodes = new Map<string, string>();
const verifiedTokens = new Map<string, string>();

export function resetAuthMockState() {
  currentUser = null;
  socialRegistrationPending = true;
  issuedVerificationCodes.clear();
  verifiedTokens.clear();
}

function createError(
  code: string,
  message: string,
  fieldErrors: { field: string; reason: string }[] = [],
) {
  return {
    occurredAt: new Date().toISOString(),
    code,
    message,
    fieldErrors,
  };
}

function toUserResponse(user: MockUser): UserResponse {
  return {
    id: user.id,
    email: user.email,
    nickname: user.nickname,
    status: user.status,
    role: user.role,
    createdAt: user.createdAt,
  };
}

export const authHandlers = [
  http.get('*/api/v1/csrf', () => {
    return HttpResponse.json({
      token: 'mock-csrf-token',
    });
  }),

  http.get('*/api/v1/copyright-agreements/current', () => {
    return HttpResponse.json(currentCopyrightAgreement);
  }),

  http.get('*/api/v1/auth/social-registration', () => {
    return HttpResponse.json({ pending: socialRegistrationPending });
  }),

  http.post('*/api/v1/auth/social-registration', async ({ request }) => {
    if (!socialRegistrationPending) {
      return HttpResponse.json(createError('UNAUTHORIZED', '가입 대기 정보가 없습니다.'), {
        status: 401,
      });
    }
    const body = (await request.json()) as SocialRegistrationRequest;
    if (
      body.copyrightAgreementId !== currentCopyrightAgreement.id ||
      body.copyrightAgreed !== true
    ) {
      return HttpResponse.json(
        createError('COPYRIGHT_AGREEMENT_MISMATCH', '현재 동의서를 다시 확인해 주세요.'),
        { status: 409 },
      );
    }
    const user: MockUser = {
      id: users.size + 1,
      email: `social-${users.size + 1}@example.com`,
      nickname: body.nickname,
      password: '',
      status: 'ACTIVE',
      role: 'USER',
      createdAt: new Date().toISOString(),
    };
    users.set(user.email, user);
    currentUser = user;
    socialRegistrationPending = false;
    return HttpResponse.json(toUserResponse(user), { status: 201 });
  }),

  http.post('*/api/v1/email-verifications', async ({ request }) => {
    const body = (await request.json()) as EmailVerificationSendRequest;
    const email = body.email.trim().toLowerCase();
    issuedVerificationCodes.set(email, '123456');
    verifiedTokens.delete(email);

    return HttpResponse.json(
      {
        expiresInSeconds: 300,
        resendCooldownSeconds: 60,
      },
      { status: 202 },
    );
  }),

  http.post('*/api/v1/email-verifications/confirm', async ({ request }) => {
    const body = (await request.json()) as EmailVerificationConfirmRequest;
    const email = body.email.trim().toLowerCase();
    const issuedCode = issuedVerificationCodes.get(email);

    if (!issuedCode) {
      return HttpResponse.json(createError('NOT_FOUND', '인증 코드가 만료되었습니다.'), {
        status: 404,
      });
    }

    if (issuedCode !== body.code) {
      return HttpResponse.json(createError('VALIDATION_ERROR', '인증 코드가 일치하지 않습니다.'), {
        status: 400,
      });
    }

    issuedVerificationCodes.delete(email);
    const verificationToken = `mock-verification-token-${email}`;
    verifiedTokens.set(email, verificationToken);
    return HttpResponse.json({ verificationToken, validForSeconds: 600 });
  }),

  http.post('*/api/v1/users', async ({ request }) => {
    const body = (await request.json()) as SignupRequest;
    const email = body.email.trim().toLowerCase();

    if (
      body.copyrightAgreementId !== currentCopyrightAgreement.id ||
      body.copyrightAgreed !== true
    ) {
      return HttpResponse.json(
        createError(
          'COPYRIGHT_AGREEMENT_MISMATCH',
          '현재 저작권 이용 동의서를 다시 확인해 주세요.',
        ),
        { status: 409 },
      );
    }

    const issuedToken = verifiedTokens.get(email);
    if (!body.verificationToken || issuedToken !== body.verificationToken) {
      return HttpResponse.json(createError('EMAIL_NOT_VERIFIED', '이메일 인증이 필요합니다.'), {
        status: 403,
      });
    }

    if (users.has(email)) {
      return HttpResponse.json(
        createError('CONFLICT', '이미 사용 중인 이메일입니다.', [
          {
            field: 'email',
            reason: '이미 사용 중인 이메일입니다.',
          },
        ]),
        { status: 409 },
      );
    }

    const hasDuplicateNickname = [...users.values()].some(
      (user) => user.nickname === body.nickname,
    );

    if (hasDuplicateNickname) {
      return HttpResponse.json(
        createError('CONFLICT', '이미 사용 중인 닉네임입니다.', [
          {
            field: 'nickname',
            reason: '이미 사용 중인 닉네임입니다.',
          },
        ]),
        { status: 409 },
      );
    }

    const user: MockUser = {
      id: users.size + 1,
      email,
      nickname: body.nickname,
      password: body.password,
      status: 'ACTIVE',
      role: 'USER',
      createdAt: new Date().toISOString(),
    };

    users.set(user.email, user);
    verifiedTokens.delete(email);

    return HttpResponse.json(toUserResponse(user), {
      status: 201,
    });
  }),

  http.post('*/api/v1/auth/sessions', async ({ request }) => {
    const body = (await request.json()) as LoginRequest;
    const user = users.get(body.email);

    if (!user || user.password !== body.password) {
      return HttpResponse.json(
        createError('UNAUTHORIZED', '이메일 또는 비밀번호가 올바르지 않습니다.'),
        { status: 401 },
      );
    }

    currentUser = user;

    return HttpResponse.json(toUserResponse(user));
  }),

  http.get('*/api/v1/users/me', () => {
    if (!currentUser) {
      return HttpResponse.json(createError('UNAUTHORIZED', '로그인이 필요합니다.'), {
        status: 401,
      });
    }

    return HttpResponse.json(toUserResponse(currentUser));
  }),

  http.post('*/api/v1/auth/sessions/current', () => {
    if (!currentUser) {
      return HttpResponse.json(createError('UNAUTHORIZED', '로그인이 필요합니다.'), {
        status: 401,
      });
    }

    currentUser = null;

    return new HttpResponse(null, { status: 204 });
  }),
];
