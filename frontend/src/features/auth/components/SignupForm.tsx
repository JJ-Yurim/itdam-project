import { zodResolver } from '@hookform/resolvers/zod';
import type { ChangeEvent } from 'react';
import { useEffect, useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';

import { getApiError } from '@/shared/api';
import { Button } from '@/shared/components';

import {
  useConfirmEmailVerificationMutation,
  useSendEmailVerificationMutation,
  useSignupMutation,
} from '../mutations';
import { useCurrentCopyrightAgreementQuery } from '../queries';
import { signupSchema, type SignupFormValues } from '../schemas';
import { CopyrightAgreementField } from './CopyrightAgreementField';

interface SignupFormProps {
  onSuccess: () => void;
}

type VerificationMessageTone = 'info' | 'error' | 'success';

interface VerificationMessage {
  tone: VerificationMessageTone;
  text: string;
}

const EXPIRED_MESSAGE = '인증 코드가 만료되었습니다. 새 코드를 발송해 주세요.';

const verificationMessageClasses: Record<VerificationMessageTone, string> = {
  info: 'bg-violet-50 text-violet-700',
  error: 'bg-red-50 text-red-700',
  success: 'bg-green-50 text-green-700',
};

export function SignupForm({ onSuccess }: SignupFormProps) {
  const copyrightAgreementQuery = useCurrentCopyrightAgreementQuery();
  const signupMutation = useSignupMutation();
  const sendVerificationMutation = useSendEmailVerificationMutation();
  const confirmVerificationMutation = useConfirmEmailVerificationMutation();
  const [verificationCode, setVerificationCode] = useState('');
  const [verificationEmail, setVerificationEmail] = useState<string | null>(null);
  const [verificationToken, setVerificationToken] = useState<string | null>(null);
  const [isEmailVerified, setIsEmailVerified] = useState(false);
  const [expiresAt, setExpiresAt] = useState(0);
  const [cooldownUntil, setCooldownUntil] = useState(0);
  const [now, setNow] = useState(() => Date.now());
  const [verificationMessage, setVerificationMessage] = useState<VerificationMessage | null>(null);
  const [agreedCopyrightAgreementId, setAgreedCopyrightAgreementId] = useState<number | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    clearErrors,
    getValues,
    setFocus,
    trigger,
    control,
    formState: { errors },
  } = useForm<SignupFormValues>({
    resolver: zodResolver(signupSchema),
    defaultValues: {
      email: '',
      password: '',
      passwordConfirm: '',
      nickname: '',
    },
  });

  const email = useWatch({ control, name: 'email' });
  const normalizedEmail = email.trim().toLowerCase();
  const emailRegistration = register('email');
  const hasSentCode = verificationEmail !== null && verificationEmail === normalizedEmail;
  const remainingSeconds = Math.max(0, Math.ceil((expiresAt - now) / 1000));
  const cooldownSeconds = Math.max(0, Math.ceil((cooldownUntil - now) / 1000));
  const isExpired = hasSentCode && !isEmailVerified && remainingSeconds === 0;
  const currentCopyrightAgreement = copyrightAgreementQuery.data;
  const copyrightAgreed =
    currentCopyrightAgreement !== undefined &&
    agreedCopyrightAgreementId === currentCopyrightAgreement.id;

  useEffect(() => {
    if (!hasSentCode || isEmailVerified || (remainingSeconds === 0 && cooldownSeconds === 0)) {
      return;
    }

    const timerId = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timerId);
  }, [cooldownSeconds, hasSentCode, isEmailVerified, remainingSeconds]);

  const resetVerification = (message: VerificationMessage) => {
    setVerificationEmail(null);
    setVerificationToken(null);
    setIsEmailVerified(false);
    setVerificationCode('');
    setExpiresAt(0);
    setCooldownUntil(0);
    setVerificationMessage(message);
  };

  const handleEmailChange = (event: ChangeEvent<HTMLInputElement>) => {
    void emailRegistration.onChange(event);
    const nextEmail = event.target.value.trim().toLowerCase();
    if (verificationEmail && verificationEmail !== nextEmail) {
      resetVerification({ tone: 'info', text: '이메일이 변경되어 다시 인증해야 합니다.' });
    }
  };

  const handleSendVerification = async () => {
    if (!(await trigger('email'))) {
      return;
    }

    const targetEmail = getValues('email').trim().toLowerCase();
    clearErrors('root.server');
    setVerificationMessage(null);

    try {
      const response = await sendVerificationMutation.mutateAsync({ email: targetEmail });
      const requestedAt = Date.now();
      setNow(requestedAt);
      setVerificationEmail(targetEmail);
      setVerificationToken(null);
      setIsEmailVerified(false);
      setVerificationCode('');
      setExpiresAt(requestedAt + response.expiresInSeconds * 1000);
      setCooldownUntil(requestedAt + response.resendCooldownSeconds * 1000);
      setVerificationMessage({ tone: 'info', text: '인증 코드를 이메일로 발송했습니다.' });
    } catch (error) {
      const apiError = getApiError(error);
      setVerificationMessage({
        tone: 'error',
        text:
          apiError.code === 'RATE_LIMITED'
            ? '요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.'
            : apiError.message || '인증 코드 발송에 실패했습니다.',
      });
    }
  };

  const handleConfirmVerification = async () => {
    if (!verificationEmail) {
      setVerificationMessage({ tone: 'error', text: '인증 코드를 먼저 발송해 주세요.' });
      return;
    }

    if (isExpired) {
      setVerificationMessage({ tone: 'error', text: EXPIRED_MESSAGE });
      return;
    }

    if (!/^\d{6}$/.test(verificationCode)) {
      setVerificationMessage({ tone: 'error', text: '인증 코드 6자리를 입력해 주세요.' });
      return;
    }

    setVerificationMessage(null);
    try {
      const response = await confirmVerificationMutation.mutateAsync({
        email: verificationEmail,
        code: verificationCode,
      });
      setVerificationToken(response.verificationToken);
      setIsEmailVerified(true);
      setVerificationMessage({ tone: 'success', text: '이메일 인증이 완료되었습니다.' });
    } catch (error) {
      const apiError = getApiError(error);
      const message =
        apiError.code === 'NOT_FOUND'
          ? EXPIRED_MESSAGE
          : apiError.code === 'RATE_LIMITED'
            ? '인증 시도 횟수를 초과했습니다. 새 코드를 발송해 주세요.'
            : apiError.code === 'VALIDATION_ERROR'
              ? '인증 코드가 일치하지 않습니다.'
              : apiError.message || '이메일 인증에 실패했습니다.';
      setVerificationMessage({ tone: 'error', text: message });
    }
  };

  const handleEditEmail = () => {
    resetVerification({ tone: 'info', text: '이메일을 수정한 뒤 다시 인증해 주세요.' });
    window.setTimeout(() => setFocus('email'), 0);
  };

  const onSubmit = handleSubmit(async (values) => {
    const normalizedEmail = values.email.trim().toLowerCase();
    if (!isEmailVerified || !verificationToken || verificationEmail !== normalizedEmail) {
      setVerificationMessage({
        tone: 'error',
        text: '회원가입 전에 이메일 인증을 완료해 주세요.',
      });
      return;
    }

    if (!currentCopyrightAgreement || !copyrightAgreed) {
      setError('root.server', {
        type: 'client',
        message: '저작권 이용 동의서를 확인하고 필수 동의해 주세요.',
      });
      return;
    }

    try {
      await signupMutation.mutateAsync({
        email: normalizedEmail,
        password: values.password,
        nickname: values.nickname.trim(),
        verificationToken,
        copyrightAgreementId: currentCopyrightAgreement.id,
        copyrightAgreed: true,
      });

      onSuccess();
    } catch (error) {
      const apiError = getApiError(error);

      if (apiError.code === 'EMAIL_NOT_VERIFIED') {
        clearErrors('root.server');
        resetVerification({
          tone: 'error',
          text: '이메일 인증이 만료되었거나 유효하지 않습니다. 다시 인증해 주세요.',
        });
        return;
      }

      if (apiError.code === 'COPYRIGHT_AGREEMENT_MISMATCH') {
        setAgreedCopyrightAgreementId(null);
        await copyrightAgreementQuery.refetch();
        setError('root.server', {
          type: 'server',
          message: '동의서가 변경되었습니다. 최신 내용을 확인하고 다시 동의해 주세요.',
        });
        return;
      }

      if (apiError.code === 'COPYRIGHT_AGREEMENT_UNAVAILABLE') {
        setAgreedCopyrightAgreementId(null);
        setError('root.server', {
          type: 'server',
          message: '현재 동의서를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.',
        });
        return;
      }

      let hasMappedFieldError = false;

      for (const fieldError of apiError.fieldErrors) {
        if (fieldError.field === 'copyrightAgreementId' || fieldError.field === 'copyrightAgreed') {
          setAgreedCopyrightAgreementId(null);
          setError('root.server', {
            type: 'server',
            message: '저작권 이용 동의서를 확인하고 다시 동의해 주세요.',
          });
          hasMappedFieldError = true;
          continue;
        }

        if (
          fieldError.field === 'email' ||
          fieldError.field === 'password' ||
          fieldError.field === 'nickname'
        ) {
          setError(fieldError.field, {
            type: 'server',
            message: fieldError.reason,
          });
          hasMappedFieldError = true;
        }
      }

      if (!hasMappedFieldError) {
        setError('root.server', {
          type: 'server',
          message: apiError.message || '회원가입에 실패했습니다.',
        });
      }
    }
  });

  return (
    <form className="space-y-5" onSubmit={onSubmit} noValidate>
      <div>
        <label className="mb-2 block text-sm font-semibold text-stone-700" htmlFor="email">
          이메일
        </label>
        <div className="flex gap-2">
          <input
            id="email"
            type="email"
            autoComplete="email"
            readOnly={isEmailVerified}
            className="min-h-12 min-w-0 flex-1 rounded-lg border border-stone-300 bg-white px-4 outline-none transition read-only:bg-stone-100 focus:border-violet-500 focus:ring-2 focus:ring-violet-200"
            aria-invalid={Boolean(errors.email)}
            {...emailRegistration}
            onChange={handleEmailChange}
          />
          {isEmailVerified ? (
            <Button type="button" variant="secondary" onClick={handleEditEmail}>
              이메일 수정
            </Button>
          ) : (
            <Button
              type="button"
              variant="secondary"
              disabled={sendVerificationMutation.isPending || (hasSentCode && cooldownSeconds > 0)}
              onClick={handleSendVerification}
            >
              {sendVerificationMutation.isPending
                ? '발송 중...'
                : hasSentCode && cooldownSeconds > 0
                  ? `재발송 ${cooldownSeconds}초`
                  : hasSentCode
                    ? '재발송'
                    : '인증 코드 발송'}
            </Button>
          )}
        </div>
        {errors.email && (
          <p className="mt-1 text-sm text-red-600" role="alert">
            {errors.email.message}
          </p>
        )}
      </div>

      {hasSentCode && !isEmailVerified && (
        <div>
          <label
            className="mb-2 block text-sm font-semibold text-stone-700"
            htmlFor="verificationCode"
          >
            인증 코드
          </label>
          <div className="flex gap-2">
            <input
              id="verificationCode"
              type="text"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              value={verificationCode}
              onChange={(event) => setVerificationCode(event.target.value.replace(/\D/g, ''))}
              className="min-h-12 min-w-0 flex-1 rounded-lg border border-stone-300 bg-white px-4 outline-none transition focus:border-violet-500 focus:ring-2 focus:ring-violet-200"
            />
            <Button
              type="button"
              disabled={confirmVerificationMutation.isPending || isExpired}
              onClick={handleConfirmVerification}
            >
              {confirmVerificationMutation.isPending ? '확인 중...' : '인증 확인'}
            </Button>
          </div>
          <p className={`mt-1 text-sm ${isExpired ? 'text-red-600' : 'text-stone-600'}`}>
            {isExpired
              ? EXPIRED_MESSAGE
              : `남은 유효시간 ${String(Math.floor(remainingSeconds / 60)).padStart(2, '0')}:${String(remainingSeconds % 60).padStart(2, '0')}`}
          </p>
        </div>
      )}

      {verificationMessage && (
        <p
          className={`rounded-lg p-3 text-sm ${verificationMessageClasses[verificationMessage.tone]}`}
          role={verificationMessage.tone === 'error' ? 'alert' : 'status'}
        >
          {verificationMessage.text}
        </p>
      )}

      <div>
        <label className="mb-2 block text-sm font-semibold text-stone-700" htmlFor="nickname">
          닉네임
        </label>
        <input
          id="nickname"
          type="text"
          autoComplete="nickname"
          className="min-h-12 w-full rounded-lg border border-stone-300 bg-white px-4 outline-none transition focus:border-violet-500 focus:ring-2 focus:ring-violet-200"
          aria-invalid={Boolean(errors.nickname)}
          {...register('nickname')}
        />
        {errors.nickname && (
          <p className="mt-1 text-sm text-red-600" role="alert">
            {errors.nickname.message}
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
          autoComplete="new-password"
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

      <div>
        <label
          className="mb-2 block text-sm font-semibold text-stone-700"
          htmlFor="passwordConfirm"
        >
          비밀번호 확인
        </label>
        <input
          id="passwordConfirm"
          type="password"
          autoComplete="new-password"
          className="min-h-12 w-full rounded-lg border border-stone-300 bg-white px-4 outline-none transition focus:border-violet-500 focus:ring-2 focus:ring-violet-200"
          aria-invalid={Boolean(errors.passwordConfirm)}
          {...register('passwordConfirm')}
        />
        {errors.passwordConfirm && (
          <p className="mt-1 text-sm text-red-600" role="alert">
            {errors.passwordConfirm.message}
          </p>
        )}
      </div>

      <CopyrightAgreementField
        agreement={currentCopyrightAgreement}
        isPending={copyrightAgreementQuery.isPending}
        isError={copyrightAgreementQuery.isError}
        isFetching={copyrightAgreementQuery.isFetching}
        checked={copyrightAgreed}
        onCheckedChange={(checked) => {
          clearErrors('root.server');
          setAgreedCopyrightAgreementId(
            checked && currentCopyrightAgreement ? currentCopyrightAgreement.id : null,
          );
        }}
        onRetry={() => void copyrightAgreementQuery.refetch()}
      />

      {errors.root?.server && (
        <p className="rounded-lg bg-red-50 p-3 text-sm text-red-700" role="alert">
          {errors.root.server.message}
        </p>
      )}

      <Button
        className="w-full"
        type="submit"
        disabled={
          !isEmailVerified ||
          !copyrightAgreed ||
          copyrightAgreementQuery.isError ||
          signupMutation.isPending
        }
      >
        {signupMutation.isPending ? '가입 중...' : '회원가입'}
      </Button>
    </form>
  );
}
