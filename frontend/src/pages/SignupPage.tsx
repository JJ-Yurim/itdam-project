import { Link, useNavigate } from 'react-router-dom';

import { AuthPageShell, SignupForm, SocialLoginButtons } from '@/features/auth';

export function SignupPage() {
  const navigate = useNavigate();

  return (
    <AuthPageShell title="회원가입" description="계정을 만들고 문장 여행을 시작하세요.">
      <SignupForm onSuccess={() => navigate('/login')} />

      <div className="mt-6">
        <SocialLoginButtons />
      </div>

      <p className="mt-6 text-center text-sm text-stone-600">
        이미 계정이 있나요?{' '}
        <Link className="font-semibold text-brand hover:text-brand-strong" to="/login">
          로그인
        </Link>
      </p>
    </AuthPageShell>
  );
}
