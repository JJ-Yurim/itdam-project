import { describe, expect, it } from 'vitest';

import { loginSchema, signupSchema } from './schemas';

describe('signupSchema', () => {
  it('올바른 회원가입 입력을 허용한다', () => {
    const result = signupSchema.safeParse({
      email: 'writer@example.com',
      nickname: '문장여행자',
      password: 'password123',
      passwordConfirm: 'password123',
    });

    expect(result.success).toBe(true);
  });

  it('비밀번호 확인이 다르면 오류를 반환한다', () => {
    const result = signupSchema.safeParse({
      email: 'writer@example.com',
      nickname: '문장여행자',
      password: 'password123',
      passwordConfirm: 'different123',
    });

    expect(result.success).toBe(false);

    if (!result.success) {
      expect(result.error.flatten().fieldErrors.passwordConfirm).toContain(
        '비밀번호가 일치하지 않습니다.',
      );
    }
  });
});

describe('loginSchema', () => {
  it('올바르지 않은 이메일 형식을 거부한다', () => {
    const result = loginSchema.safeParse({
      email: 'demo',
      password: 'password123',
    });

    expect(result.success).toBe(false);

    if (!result.success) {
      expect(result.error.flatten().fieldErrors.email).toContain(
        '올바른 이메일 형식을 입력해 주세요.',
      );
    }
  });
});
