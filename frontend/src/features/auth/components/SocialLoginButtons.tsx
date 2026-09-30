const SOCIAL_PROVIDERS = [
  {
    id: 'kakao',
    label: '카카오로 계속하기',
    className: 'bg-[#FEE500] text-[#191919] hover:bg-[#F4DC00]',
    icon: (
      <svg aria-hidden="true" className="h-5 w-5" viewBox="0 0 24 24" fill="currentColor">
        <path d="M12 3C6.48 3 2 6.48 2 10.77c0 2.74 1.83 5.15 4.58 6.53l-.93 3.44c-.08.31.27.56.54.38l4.1-2.72c.56.07 1.13.11 1.71.11 5.52 0 10-3.48 10-7.74C22 6.48 17.52 3 12 3z" />
      </svg>
    ),
  },
  {
    id: 'naver',
    label: '네이버로 계속하기',
    className: 'bg-[#03C75A] text-white hover:bg-[#02B152]',
    icon: (
      <svg aria-hidden="true" className="h-4 w-4" viewBox="0 0 24 24" fill="currentColor">
        <path d="M16.27 12.85 7.38 0H0v24h7.73V11.15L16.62 24H24V0h-7.73v12.85z" />
      </svg>
    ),
  },
  {
    id: 'google',
    label: '구글로 계속하기',
    className: 'border border-stone-300 bg-white text-stone-700 hover:bg-stone-50',
    icon: (
      <svg aria-hidden="true" className="h-5 w-5" viewBox="0 0 24 24">
        <path
          fill="#4285F4"
          d="M23.52 12.27c0-.85-.08-1.66-.22-2.45H12v4.64h6.46a5.52 5.52 0 0 1-2.4 3.62v3h3.88c2.27-2.09 3.58-5.17 3.58-8.81z"
        />
        <path
          fill="#34A853"
          d="M12 24c3.24 0 5.96-1.07 7.94-2.91l-3.88-3.01c-1.07.72-2.45 1.15-4.06 1.15-3.13 0-5.78-2.11-6.72-4.95H1.27v3.11A12 12 0 0 0 12 24z"
        />
        <path
          fill="#FBBC05"
          d="M5.28 14.28A7.2 7.2 0 0 1 4.9 12c0-.79.14-1.56.38-2.28V6.61H1.27a12 12 0 0 0 0 10.78l4.01-3.11z"
        />
        <path
          fill="#EA4335"
          d="M12 4.77c1.76 0 3.34.61 4.59 1.8l3.44-3.44A11.97 11.97 0 0 0 12 0 12 12 0 0 0 1.27 6.61l4.01 3.11C6.22 6.88 8.87 4.77 12 4.77z"
        />
      </svg>
    ),
  },
] as const;

// 소셜 로그인은 XHR이 아니라 전체 페이지 이동으로 시작해야 한다.
// provider 인가 페이지를 거쳐 백엔드 콜백(/api/login/oauth2/code/*)이 세션을 만든 뒤 앱으로 돌아온다.
function socialLoginUrl(providerId: string) {
  // stream.ts와 같은 폴백. ||인 이유: 빈 문자열로 정의된 환경 변수도 폴백해야 한다.
  // 끝 슬래시는 //oauth2/... 가 되어 Spring baseUri 매칭에서 벗어나므로 제거한다.
  const base = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '');
  return `${base}/oauth2/authorization/${providerId}`;
}

export function SocialLoginButtons() {
  return (
    <div>
      <div className="mb-6 flex items-center gap-3" aria-hidden="true">
        <span className="h-px flex-1 bg-stone-200" />
        <span className="text-xs text-stone-400">또는</span>
        <span className="h-px flex-1 bg-stone-200" />
      </div>

      <div className="space-y-3">
        {SOCIAL_PROVIDERS.map((provider) => (
          <a
            key={provider.id}
            href={socialLoginUrl(provider.id)}
            className={`flex min-h-12 w-full items-center justify-center gap-2 rounded-lg px-4 text-sm font-semibold transition ${provider.className}`}
          >
            {provider.icon}
            {provider.label}
          </a>
        ))}
      </div>
    </div>
  );
}
