import { useEffect } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';

import {
  AuthPageShell,
  getSocialLoginErrorMessage,
  LoginForm,
  SocialLoginButtons,
} from '@/features/auth';

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const querySocialError = searchParams.get('socialError');
  const socialError =
    querySocialError ??
    (typeof location.state?.socialError === 'string' ? location.state.socialError : null);
  const returnTo = typeof location.state?.from === 'string' ? location.state.from : '/';

  useEffect(() => {
    if (!querySocialError) return;

    const nextSearchParams = new URLSearchParams(searchParams);
    nextSearchParams.delete('socialError');
    setSearchParams(nextSearchParams, {
      replace: true,
      state: { ...location.state, socialError: querySocialError },
    });
  }, [location.state, querySocialError, searchParams, setSearchParams]);

  return (
    <AuthPageShell title="로그인" description="다시 만나 반가워요.">
      {socialError && (
        <div className="mb-5 rounded-control bg-red-50 p-3 text-sm text-red-700" role="alert">
          <p className="font-semibold">소셜 로그인에 실패했어요</p>
          <p className="mt-1">{getSocialLoginErrorMessage(socialError)}</p>
        </div>
      )}

      <LoginForm onSuccess={() => navigate(returnTo, { replace: true })} />

      <div className="mt-6">
        <SocialLoginButtons />
      </div>

      <p className="mt-6 text-center text-sm text-stone-600">
        아직 계정이 없나요?{' '}
        <Link className="font-semibold text-brand hover:text-brand-strong" to="/signup">
          회원가입
        </Link>
      </p>
    </AuthPageShell>
  );
}
