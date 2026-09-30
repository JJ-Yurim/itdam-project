import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';

interface AuthPageShellProps {
  title: string;
  description: string;
  children: ReactNode;
}

/**
 * 로그인·회원가입 공용 셸. 앱 셸 밖의 화면이라도 잇담의 배경과 로고를 유지해
 * 서비스에 들어와 있다는 감각이 끊기지 않게 한다.
 */
export function AuthPageShell({ title, description, children }: AuthPageShellProps) {
  return (
    <main className="relative flex min-h-dvh items-center justify-center px-4 py-10">
      {/* AppLayout과 같은 배경 레이어를 써서 화면 간 브랜드 연속성을 유지한다. */}
      <div
        aria-hidden="true"
        className="app-background fixed inset-0 -z-10 bg-[#f8f3ff] bg-[url('/itdam-background.webp')] bg-cover bg-center bg-no-repeat"
      />

      <section className="w-full max-w-md rounded-panel bg-white p-6 shadow-xl shadow-violet-200/40 sm:p-8">
        <header className="mb-8 text-center">
          <Link
            to="/"
            className="inline-flex rounded-control focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-brand"
          >
            <img src="/homepage_logo.png" alt="잇담" className="h-9 w-auto" />
          </Link>
          <h1 className="mt-5 text-2xl font-extrabold text-stone-900">{title}</h1>
          <p className="mt-2 text-sm text-stone-600">{description}</p>
        </header>

        {children}
      </section>
    </main>
  );
}
