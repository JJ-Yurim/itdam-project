import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';

import { getApiError } from '@/shared/api';
import { Button } from '@/shared/components';

import { useCompleteSocialRegistrationMutation } from '../mutations';
import { useCurrentCopyrightAgreementQuery } from '../queries';
import { socialSignupSchema, type SocialSignupFormValues } from '../schemas';
import { CopyrightAgreementField } from './CopyrightAgreementField';

interface SocialSignupFormProps {
  onSuccess: () => void;
  onSessionExpired: () => void;
}

export function SocialSignupForm({ onSuccess, onSessionExpired }: SocialSignupFormProps) {
  const agreementQuery = useCurrentCopyrightAgreementQuery();
  const completeMutation = useCompleteSocialRegistrationMutation();
  const [agreedAgreementId, setAgreedAgreementId] = useState<number | null>(null);
  const agreement = agreementQuery.data;
  const isAgreed = agreement !== undefined && agreedAgreementId === agreement.id;
  const {
    register,
    handleSubmit,
    setError,
    clearErrors,
    formState: { errors },
  } = useForm<SocialSignupFormValues>({
    resolver: zodResolver(socialSignupSchema),
    defaultValues: { nickname: '' },
  });

  const onSubmit = handleSubmit(async ({ nickname }) => {
    if (!agreement || !isAgreed) {
      setError('root.server', { message: '저작권 이용 동의서를 확인하고 동의해 주세요.' });
      return;
    }

    try {
      await completeMutation.mutateAsync({
        nickname: nickname.trim(),
        copyrightAgreementId: agreement.id,
        copyrightAgreed: true,
      });
      onSuccess();
    } catch (error) {
      const apiError = getApiError(error);
      if (apiError.code === 'UNAUTHORIZED') {
        onSessionExpired();
        return;
      }
      if (apiError.code === 'COPYRIGHT_AGREEMENT_MISMATCH') {
        setAgreedAgreementId(null);
        await agreementQuery.refetch();
        setError('root.server', {
          message: '동의서가 변경되었습니다. 최신 내용을 확인하고 다시 동의해 주세요.',
        });
        return;
      }
      if (apiError.code === 'COPYRIGHT_AGREEMENT_UNAVAILABLE') {
        setAgreedAgreementId(null);
        setError('root.server', {
          message: '현재 동의서를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.',
        });
        return;
      }
      const nicknameError = apiError.fieldErrors.find(({ field }) => field === 'nickname');
      if (apiError.code === 'CONFLICT' || nicknameError) {
        setError('nickname', {
          type: 'server',
          message: nicknameError?.reason ?? '이미 사용 중인 닉네임입니다.',
        });
        return;
      }
      setError('root.server', {
        type: 'server',
        message: apiError.message || '소셜 회원가입을 완료하지 못했습니다.',
      });
    }
  });

  return (
    <form className="space-y-5" onSubmit={onSubmit} noValidate>
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

      <CopyrightAgreementField
        agreement={agreement}
        isPending={agreementQuery.isPending}
        isError={agreementQuery.isError}
        isFetching={agreementQuery.isFetching}
        checked={isAgreed}
        onCheckedChange={(checked) => {
          clearErrors('root.server');
          setAgreedAgreementId(checked && agreement ? agreement.id : null);
        }}
        onRetry={() => void agreementQuery.refetch()}
      />

      {errors.root?.server && (
        <p className="rounded-lg bg-red-50 p-3 text-sm text-red-700" role="alert">
          {errors.root.server.message}
        </p>
      )}

      <Button
        className="w-full"
        type="submit"
        disabled={!agreement || !isAgreed || completeMutation.isPending}
      >
        {completeMutation.isPending ? '가입 처리 중...' : '가입 완료'}
      </Button>
    </form>
  );
}
