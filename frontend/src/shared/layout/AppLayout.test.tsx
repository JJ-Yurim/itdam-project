import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, MemoryRouter, RouterProvider } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { AppLayout, ServiceShell } from './AppLayout';

function renderAppLayout() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(
    [
      {
        element: <AppLayout />,
        children: [{ index: true, element: <div>홈 콘텐츠</div> }],
      },
    ],
    { initialEntries: ['/'] },
  );

  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}

function renderServiceShell(activeNavigationTo: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/tutorial']}>
        <ServiceShell sessionControl={null} activeNavigationTo={activeNavigationTo}>
          튜토리얼 콘텐츠
        </ServiceShell>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('AppLayout', () => {
  it('강제로 강조한 메뉴와 aria-current 상태를 같은 항목에 표시한다', () => {
    renderServiceShell('/boss');

    const bossLink = screen.getByRole('link', { name: '이야기 원정' });
    const homeLink = screen.getByRole('link', { name: '홈' });

    expect(bossLink).toHaveClass('bg-violet-50', 'text-violet-800');
    expect(bossLink).toHaveAttribute('aria-current', 'page');
    expect(homeLink).not.toHaveAttribute('aria-current');
  });

  it('메뉴 아이콘과 잇담 홈 로고를 표시하고 현재 위치를 강조한다', async () => {
    renderAppLayout();

    const navigation = await screen.findByRole('navigation');

    expect(navigation.querySelector('a[href="/sentences/new"] svg')).toBeInTheDocument();
    expect(navigation.querySelector('a[href="/boss"] svg')).toBeInTheDocument();
    expect(navigation.querySelector('a[href="/library"] svg')).toBeInTheDocument();
    expect(navigation.querySelector('a[href="/hall-of-fame"] svg')).toBeInTheDocument();

    const homeLink = navigation.querySelector('a[href="/"]');
    expect(homeLink).toHaveClass('bg-violet-50', 'text-violet-800');
    expect(homeLink?.querySelector('img')).toHaveAttribute('src', '/homepage_logo.png');
    const tutorialLink = screen.getByRole('link', { name: '튜토리얼 열기' });
    expect(tutorialLink).toHaveAttribute('href', '/tutorial');
    expect(tutorialLink).toHaveTextContent('튜토리얼');
  });

  it('모바일 스크롤 비용이 큰 고정 배경과 backdrop blur를 사용하지 않는다', async () => {
    renderAppLayout();

    const main = await screen.findByRole('main');
    const appShell = main.parentElement;
    const background = appShell?.parentElement;
    const navigation = screen.getByRole('navigation', { name: '주요 메뉴' });
    const header = appShell?.querySelector('header');

    expect(background).not.toHaveClass('bg-fixed');
    expect(appShell?.className).not.toContain('backdrop-blur');
    expect(header?.className).not.toContain('backdrop-blur');
    expect(navigation.className).not.toContain('backdrop-blur');
  });

  it('모바일 동적 뷰포트와 safe area에 맞춰 고정 영역을 배치한다', async () => {
    renderAppLayout();

    const main = await screen.findByRole('main');
    const appShell = main.parentElement;
    const viewport = appShell?.parentElement;
    const header = appShell?.querySelector('header');
    const navigation = screen.getByRole('navigation', { name: '주요 메뉴' });

    expect(viewport).toHaveClass('min-h-dvh');
    expect(appShell).toHaveClass('min-h-dvh');
    expect(header).toHaveClass('bg-white', 'shrink-0');
    expect(header).not.toHaveClass('bg-white/95');
    expect(navigation).toHaveClass('app-bottom-navigation', 'bg-white', 'shrink-0');
    expect(navigation).not.toHaveClass('bg-white/95');
  });

  it('하단 네비게이션에 이야기의 전당이 있고 내 서고는 없다', async () => {
    renderAppLayout();

    const navigation = await screen.findByRole('navigation', { name: '주요 메뉴' });

    expect(screen.getByRole('link', { name: '문장 생성' })).toHaveAttribute(
      'href',
      '/sentences/new',
    );
    expect(screen.getByRole('link', { name: '이야기 원정' })).toHaveAttribute('href', '/boss');
    expect(screen.getByRole('link', { name: '도서관' })).toHaveAttribute('href', '/library');
    expect(screen.getByRole('link', { name: '이야기의 전당' })).toHaveAttribute(
      'href',
      '/hall-of-fame',
    );
    expect(navigation).not.toHaveTextContent('내 서고');
  });

  it('비회원이 인증 전용 메뉴를 누르면 이동하지 않고 로그인 팝업을 표시한다', async () => {
    const user = userEvent.setup();
    renderAppLayout();

    await user.click(await screen.findByRole('link', { name: '문장 생성' }));

    expect(screen.getByRole('dialog')).toHaveTextContent('로그인이 필요한 기능이에요');
    expect(screen.getByText('홈 콘텐츠')).toBeVisible();
    expect(screen.getByRole('link', { name: '로그인하러 가기' })).toHaveAttribute('href', '/login');
  });
});
