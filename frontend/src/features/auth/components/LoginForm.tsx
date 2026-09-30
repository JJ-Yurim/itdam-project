import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';

import { Button } from '@/shared/components';
import { getApiError } from '@/shared/api';

import { getLoginErrorMessage } from '../loginErrors';
import { useLoginMutation } from '../mutations';
import { loginSchema, type LoginFormValues } from '../schemas';

interface LoginFormProps {
  onSuccess: () => void;
}

export function LoginForm({ onSuccess }: LoginFormProps) {
  const loginMutation = useLoginMutation();

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: '',
      password: '',
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    try {
      await loginMutation.mutateAsync({
        email: values.email.trim(),
        password: values.password,
      });

      onSuccess();
    } catch (error) {
      const apiError = getApiError(error);

      if (apiError.code === 'VALIDATION_ERROR' || apiError.code === 'INVALID_REQUEST') {
        let hasFieldError = false;

        for (const fieldError of apiError.fieldErrors) {
          if (fieldError.field === 'email') {
            setError('email', {
              type: 'server',
              message: '이메일 입력값을 확인해 주세요.',
            });
            hasFieldError = true;
          }
          if (fieldError.field === 'password') {
            setError('password', {
              type: 'server',
              message: '비밀번호 입력값을 확인해 주세요.',
            });
            hasFieldError = true;
          }
        }

        if (hasFieldError) return;
      }

      setError('root.server', {
        type: 'server',
        message: getLoginErrorMessage(error),
      });
    }
  });

  return (
    <form className="space-y-5" onSubmit={onSubmit} noValidate>
      <div>
        <label className="mb-2 block text-sm font-semibold text-stone-700" htmlFor="email">
          이메일
        </label>
        <input
          id="email"
          type="email"
          autoComplete="email"
          className="min-h-12 w-full rounded-lg border border-stone-300 bg-white px-4 outline-none transition focus:border-violet-500 focus:ring-2 focus:ring-violet-200"
          aria-invalid={Boolean(errors.email)}
          {...register('email')}
        />
        {errors.email && (
          <p className="mt-1 text-sm text-red-600" role="alert">
            {errors.email.message}
          </p>
        )}
      </div>

      <div>
        <label className="mb-2 block text-sm font-semibold text-stone-700" htmlFor="password">
          비밀번호
        </label>
        <input
          id="password"
          type="password"
          autoComplete="current-password"
          className="min-h-12 w-full rounded-lg border border-stone-300 bg-white px-4 outline-none transition focus:border-violet-500 focus:ring-2 focus:ring-violet-200"
          aria-invalid={Boolean(errors.password)}
          {...register('password')}
        />
        {errors.password && (
          <p className="mt-1 text-sm text-red-600" role="alert">
            {errors.password.message}
          </p>
        )}
      </div>

      {errors.root?.server && (
        <div className="rounded-lg bg-red-50 p-3 text-sm text-red-700" role="alert">
          <p className="font-semibold">로그인하지 못했어요</p>
          <p className="mt-1">{errors.root.server.message}</p>
        </div>
      )}

      <Button className="w-full" type="submit" loading={loginMutation.isPending}>
        {loginMutation.isPending ? '로그인 중...' : '로그인'}
      </Button>
    </form>
  );
}
