import { z } from 'zod';

export const signupSchema = z
  .object({
    email: z
      .string()
      .trim()
      .min(1, '이메일을 입력해 주세요.')
      .email('올바른 이메일 형식을 입력해 주세요.')
      .max(255, '이메일은 255자 이하여야 합니다.'),
    password: z
      .string()
      .min(8, '비밀번호는 8자 이상이어야 합니다.')
      .max(72, '비밀번호는 72자 이하여야 합니다.'),
    passwordConfirm: z.string().min(1, '비밀번호를 다시 입력해 주세요.'),
    nickname: z
      .string()
      .trim()
      .min(1, '닉네임을 입력해 주세요.')
      .max(100, '닉네임은 100자 이하여야 합니다.'),
  })
  .refine((values) => values.password === values.passwordConfirm, {
    message: '비밀번호가 일치하지 않습니다.',
    path: ['passwordConfirm'],
  });

export const loginSchema = z.object({
  email: z
    .string()
    .trim()
    .min(1, '이메일을 입력해 주세요.')
    .email('올바른 이메일 형식을 입력해 주세요.'),
  password: z.string().min(1, '비밀번호를 입력해 주세요.'),
});

export const socialSignupSchema = z.object({
  nickname: z
    .string()
    .trim()
    .min(1, '닉네임을 입력해 주세요.')
    .max(100, '닉네임은 100자 이하여야 합니다.'),
});

export type SignupFormValues = z.infer<typeof signupSchema>;
export type LoginFormValues = z.infer<typeof loginSchema>;
export type SocialSignupFormValues = z.infer<typeof socialSignupSchema>;
