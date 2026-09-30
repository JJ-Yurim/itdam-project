import {
  AuthGateProvider,
  AuthSessionControl,
  useAuthGate,
  useCurrentUserQuery,
} from '@/features/auth';
import type { ReactNode } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';

// 내 서고는 헤더 프로필 드로어로 진입하고, 네비게이션 맨 오른쪽은 이야기의 전당이 차지한다.
type NavigationIconName = 'sentence' | 'boss' | 'home' | 'library' | 'hall' | 'admin';

const navigationItems: {
  to: string;
  label: string;
  /** 좁은 하단 탭에서 잘리지 않도록 모바일에서만 쓰는 짧은 이름. 없으면 label을 그대로 쓴다. */
  shortLabel?: string;
  icon?: NavigationIconName;
  requiresAuth?: boolean;
}[] = [
  {
    to: '/sentences/new',
    label: '문장 생성',
    icon: 'sentence',
    requiresAuth: true,
  },
  { to: '/boss', label: '이야기 원정', shortLabel: '원정', icon: 'boss' },
  { to: '/', label: '홈', icon: 'home' },
  { to: '/library', label: '도서관', icon: 'library' },
  {
    to: '/hall-of-fame',
    label: '이야기의 전당',
    shortLabel: '전당',
    icon: 'hall',
    requiresAuth: true,
  },
];

function NavigationIcon({ name }: { name: NavigationIconName }) {
  const commonProps = {
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 1.8,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    className: 'h-5 w-5',
    'aria-hidden': true,
  };

  if (name === 'sentence') {
    return (
      <svg {...commonProps}>
        <path d="m4 20 4.5-1 10-10a2.1 2.1 0 0 0-3-3l-10 10L4 20Z" />
        <path d="m14 7 3 3" />
      </svg>
    );
  }
  if (name === 'boss') {
    return (
      <svg {...commonProps}>
        <path d="m5 4 6 6-2 2-6-6 2-2Z" />
        <path d="m19 4-6 6 2 2 6-6-2-2Z" />
        <path d="m7 14-3 3 3 3 3-3" />
        <path d="m17 14 3 3-3 3-3-3" />
      </svg>
    );
  }
  // 데스크톱 상단 메뉴에서는 헤더 로고와 홈 아이콘이 나란히 놓여 같은 워드마크가 두 번 보인다.
  // 홈은 집 아이콘으로 대신하고, 로고는 헤더 왼쪽 한 곳에서만 쓴다.
  if (name === 'home') {
    return (
      <svg {...commonProps}>
        <path d="m3.5 10.8 8.5-6.6 8.5 6.6" />
        <path d="M6 9.4V20h12V9.4" />
        <path d="M10 20v-4.6h4V20" />
      </svg>
    );
  }
  if (name === 'library') {
    return (
      <svg {...commonProps}>
        <path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H11v17H6.5A2.5 2.5 0 0 0 4 22V5.5Z" />
        <path d="M20 5.5A2.5 2.5 0 0 0 17.5 3H13v17h4.5A2.5 2.5 0 0 1 20 22V5.5Z" />
      </svg>
    );
  }
  if (name === 'hall') {
    return (
      <svg {...commonProps}>
        <circle cx="12" cy="9" r="5" />
        <path d="m9 13-1 8 4-2 4 2-1-8" />
        <path d="m12 6 .8 1.6 1.7.3-1.2 1.2.3 1.7-1.6-.8-1.6.8.3-1.7-1.2-1.2 1.7-.3L12 6Z" />
      </svg>
    );
  }

  return (
    <svg {...commonProps}>
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1A1.7 1.7 0 0 0 9 4.6a1.7 1.7 0 0 0 1-1.6v-.2h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1Z" />
    </svg>
  );
}

export function AppLayout() {
  return (
    <AuthGateProvider>
      <AppLayoutContent />
    </AuthGateProvider>
  );
}

function AppLayoutContent() {
  const currentUserQuery = useCurrentUserQuery();
  const { isUnauthenticated, requestLogin } = useAuthGate();
  return (
    <ServiceShell
      isAdmin={currentUserQuery.data?.role === 'ADMIN'}
      isUnauthenticated={isUnauthenticated}
      requestLogin={requestLogin}
      sessionControl={<AuthSessionControl />}
    >
      <Outlet />
    </ServiceShell>
  );
}

interface ServiceShellProps {
  children: ReactNode;
  sessionControl: ReactNode;
  overlay?: ReactNode;
  isAdmin?: boolean;
  isUnauthenticated?: boolean;
  requestLogin?: (returnTo?: string) => void;
  /** 튜토리얼처럼 실제 경로 밖에서 서비스 화면을 재현할 때 강조할 메뉴. */
  activeNavigationTo?: string;
  /** 실제 읽기 경로가 아닌 튜토리얼에서도 책 읽기 전용 셸 크기를 재사용한다. */
  readerMode?: boolean;
}

/** 서비스의 헤더·배경·반응형 내비게이션을 실제 화면과 정적 체험이 함께 사용한다. */
export function ServiceShell({
  children,
  sessionControl,
  overlay,
  isAdmin = false,
  isUnauthenticated = false,
  requestLogin = () => undefined,
  activeNavigationTo,
  readerMode = false,
}: ServiceShellProps) {
  const location = useLocation();
  const isBookReader = readerMode || /^\/library\/books\/[^/]+\/read$/.test(location.pathname);
  const visibleNavigationItems = isAdmin
    ? [
        ...navigationItems,
        { to: '/admin/raids', label: '관리', icon: 'admin' as const, requiresAuth: true },
      ]
    : navigationItems;

  return (
    <div
      // 모바일 브라우저 주소창이 접히거나 펼쳐져도 현재 보이는 영역을 기준으로
      // 앱 셸 높이를 다시 계산해 하단에 빈 공간이 남지 않도록 한다.
      className={isBookReader ? 'h-dvh overflow-hidden' : 'min-h-dvh'}
    >
      {/* 배경을 콘텐츠 높이만큼 늘어나는 컨테이너에 그리면 페이지 길이에 따라
          확대 배율이 달라지고 스크롤을 따라 움직인다. 뷰포트 고정 레이어로 분리해
          어느 페이지에서나 같은 배경이 같은 위치에 보이게 한다. */}
      <div
        aria-hidden="true"
        className="app-background fixed inset-0 -z-10 bg-[#f8f3ff] bg-[url('/itdam-background.webp')] bg-cover bg-center bg-no-repeat"
      />
      <div
        className={[
          // 모바일 셸은 뷰포트를 꽉 채우므로 불투명하게 두면 배경이 아예 보이지 않는다.
          // 기존의 은은한 비침을 유지하고, 배경이 좌우로 드러나는 데스크톱에서만 불투명하게 한다.
          'relative mx-auto flex w-full max-w-[480px] flex-col bg-white/88 shadow-sm transition-[max-width] lg:max-w-[1180px] lg:bg-white lg:shadow-xl',
          isBookReader ? 'h-dvh overflow-hidden' : 'min-h-dvh',
        ].join(' ')}
      >
        <header className="sticky top-0 z-40 flex min-h-16 shrink-0 items-center justify-between border-b border-stone-200 bg-white px-3 min-[360px]:px-4 min-[400px]:px-5 lg:min-h-20 lg:px-8">
          <NavLink to="/" className="flex items-center">
            <img src="/homepage_logo.png" alt="잇담" className="h-7 w-auto lg:h-8" />
          </NavLink>

          <div className="flex min-w-0 items-center gap-1.5 min-[400px]:gap-2">
            <Link
              to="/tutorial"
              aria-label="튜토리얼 열기"
              className="inline-flex min-h-10 shrink-0 items-center gap-1.5 rounded-xl px-2 text-sm font-bold text-violet-700 transition hover:bg-violet-50 min-[400px]:px-3"
            >
              <svg
                aria-hidden="true"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                className="size-5"
              >
                <circle cx="12" cy="12" r="9" />
                <path d="M9.8 9a2.3 2.3 0 1 1 3.7 1.8c-.9.6-1.5 1-1.5 2.2" />
                <path d="M12 17h.01" />
              </svg>
              <span className="hidden min-[400px]:inline">튜토리얼</span>
            </Link>
            {sessionControl}
          </div>
        </header>

        {/* pathname 키로 라우트가 바뀔 때마다 콘텐츠가 한 번 부드럽게 나타난다.
            컷 전환의 갑작스러움을 줄이는 용도라 200ms 한 번이면 충분하다. */}
        <main
          key={location.pathname}
          className={[
            'motion-rise',
            isBookReader
              ? 'min-h-0 flex-1 px-3 py-3 lg:px-8 lg:py-6'
              : 'flex-1 px-3 py-5 pb-24 min-[360px]:px-4 min-[400px]:px-5 min-[400px]:py-6 lg:px-8 lg:py-10 lg:pb-10',
          ].join(' ')}
        >
          {children}
        </main>

        {/* 모바일은 하단 고정 탭바, 데스크톱은 헤더에 겹쳐 놓는 상단 메뉴로 쓴다.
            데스크톱에서 absolute를 쓰면 뷰포트가 아니라 앱 셸에 붙어 스크롤과 함께 밀려 올라가고,
            그때는 하단 탭바도 없어 메뉴에 닿을 방법이 사라진다. 그래서 fixed로 화면에 고정한다. */}
        <nav
          aria-label="주요 메뉴"
          className="app-bottom-navigation sticky bottom-0 z-40 grid min-h-20 shrink-0 gap-0.5 border-t border-stone-200 bg-white px-0.5 pt-1.5 min-[360px]:gap-1 min-[360px]:px-1 lg:fixed lg:top-0 lg:left-1/2 lg:h-20 lg:min-h-0 lg:w-auto lg:-translate-x-1/2 lg:flex lg:items-center lg:gap-1 lg:border-0 lg:bg-transparent lg:p-0"
          style={{
            gridTemplateColumns: `repeat(${visibleNavigationItems.length}, minmax(0, 1fr))`,
          }}
        >
          {visibleNavigationItems.map(({ to, label, shortLabel, icon, requiresAuth }) => {
            const isRouteActive =
              to === '/'
                ? location.pathname === '/'
                : location.pathname === to || location.pathname.startsWith(`${to}/`);
            const isDisplayedActive =
              activeNavigationTo !== undefined ? activeNavigationTo === to : isRouteActive;

            return (
              <Link
                key={to}
                to={to}
                aria-current={isDisplayedActive ? 'page' : undefined}
                onClick={(event) => {
                  if (!requiresAuth || !isUnauthenticated) return;
                  event.preventDefault();
                  requestLogin(to);
                }}
                className={[
                  'relative flex min-w-0 flex-col items-center justify-center gap-1 rounded-xl px-0.5 py-2.5 text-[10px] leading-none transition-colors focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-violet-600 min-[360px]:gap-1.5 min-[360px]:px-1 min-[400px]:text-[11px] lg:min-h-11 lg:flex-row lg:gap-2 lg:px-3 lg:py-2 lg:text-sm',
                  isDisplayedActive
                    ? 'bg-violet-50 font-bold text-violet-800'
                    : 'text-stone-500 hover:bg-stone-50 hover:text-stone-800',
                ].join(' ')}
              >
                {isDisplayedActive && (
                  <span
                    aria-hidden="true"
                    className="absolute inset-x-3 top-0 h-0.5 rounded-full bg-violet-600 lg:inset-x-2 lg:top-auto lg:bottom-0"
                  />
                )}
                <span
                  className="flex h-6 items-center justify-center min-[360px]:h-7 lg:h-6"
                  aria-hidden="true"
                >
                  {to === '/' && (
                    <img
                      src="/homepage_logo.png"
                      alt=""
                      className={[
                        'h-6 w-auto transition-opacity lg:hidden',
                        isDisplayedActive ? '' : 'opacity-45',
                      ].join(' ')}
                    />
                  )}
                  {icon && (
                    <span className={to === '/' ? 'hidden lg:flex' : 'flex'}>
                      <NavigationIcon name={icon} />
                    </span>
                  )}
                </span>
                {/* 홈은 모바일 하단바에서 로고만으로 알아볼 수 있어 라벨을 숨기지만,
                    접근성 이름은 남겨야 하므로 sr-only로 두고 데스크톱에서만 드러낸다.
                    긴 이름은 모바일에서 잘리는 대신 짧은 이름으로 바꿔 보여주고,
                    접근성 이름은 항상 전체 이름으로 유지한다. */}
                {to === '/' ? (
                  <span className="sr-only lg:not-sr-only">{label}</span>
                ) : (
                  <>
                    {shortLabel && (
                      <span aria-hidden="true" className="max-w-full px-0.5 lg:hidden">
                        {shortLabel}
                      </span>
                    )}
                    <span
                      className={
                        shortLabel
                          ? 'sr-only lg:not-sr-only lg:max-w-full lg:truncate lg:px-0.5'
                          : 'max-w-full truncate px-0.5'
                      }
                    >
                      {label}
                    </span>
                  </>
                )}
              </Link>
            );
          })}
        </nav>
        {overlay}
      </div>
    </div>
  );
}
