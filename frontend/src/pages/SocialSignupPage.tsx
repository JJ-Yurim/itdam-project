import { Navigate, useNavigate } from 'react-router-dom';

import { SocialSignupForm, useSocialRegistrationPendingQuery } from '@/features/auth';
import { ErrorState, LoadingState } from '@/shared/components';

export function SocialSignupPage() {
  const navigate = useNavigate();
  const pendingQuery = useSocialRegistrationPendingQuery();

  if (pendingQuery.isPending) {
    return (
      <main className="flex min-h-screen items-center justify-center px-4 py-10">
        <LoadingState
          title="소셜 가입 정보를 확인하고 있습니다"
          description="잠시만 기다려 주세요."
        />
      </main>
    );
  }

  if (pendingQuery.isError) {
    return (
      <main className="flex min-h-screen items-center justify-center px-4 py-10">
        <ErrorState
          title="소셜 가입 정보를 확인하지 못했습니다"
          description="잠시 후 다시 시도해 주세요."
          onRetry={() => void pendingQuery.refetch()}
        />
      </main>
    );
  }

  if (!pendingQuery.data.pending) {
    return <Navigate to="/login?socialError=social_signup_expired" replace />;
  }

  const restartLogin = () =>
    void navigate('/login?socialError=social_signup_expired', { replace: true });

  return (
    <main className="flex min-h-screen items-center justify-center px-4 py-10">
      <section className="w-full max-w-md rounded-2xl bg-white p-6 shadow-sm sm:p-8">
        <header className="mb-8 text-center">
          <h1 className="text-3xl font-bold text-stone-900">소셜 회원가입 완료</h1>
          <p className="mt-2 text-sm leading-6 text-stone-600">
            서비스에서 사용할 닉네임을 정하고 저작권 이용 동의서를 확인해 주세요.
          </p>
        </header>

        <SocialSignupForm
          onSuccess={() => void navigate('/', { replace: true })}
          onSessionExpired={restartLogin}
        />
      </section>
    </main>
  );
}
