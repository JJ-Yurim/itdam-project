import {
  createBrowserRouter,
  Navigate,
  RouterProvider,
  useLocation,
  useParams,
} from 'react-router-dom';

import { PlaceholderPage } from '@/shared/components/PlaceholderPage';
import { AdminLayout } from '@/shared/layout/AdminLayout';
import { AppLayout } from '@/shared/layout/AppLayout';
import { RequireAuthentication } from '@/features/auth';
import { FirstVisitTutorialGate } from '@/features/tutorial';

import { BossBattlePage } from '@/pages/BossBattlePage';
import { BossClearPage } from '@/pages/BossClearPage';
import { ArchiveBookmarksPage } from '@/pages/ArchiveBookmarksPage';
import { ArchiveDetailPage } from '@/pages/ArchiveDetailPage';
import { ArchivePage } from '@/pages/ArchivePage';
import { BossListPage } from '@/pages/BossListPage';
import { BookDetailPage } from '@/pages/BookDetailPage';
import { BookReaderPage } from '@/pages/BookReaderPage';
import { HallOfFamePage } from '@/pages/HallOfFamePage';
import { HomePage } from '@/pages/HomePage';
import { LibraryPage } from '@/pages/LibraryPage';
import { LoginPage } from '@/pages/LoginPage';
import { MyPage } from '@/pages/MyPage';
import { NotificationSettingsPage } from '@/pages/NotificationSettingsPage';
import { PrivacyPolicyPage } from '@/pages/PrivacyPolicyPage';
import { ServiceSettingsPage } from '@/pages/ServiceSettingsPage';
import { TermsPage } from '@/pages/TermsPage';
import { SentenceCreatePage } from '@/pages/SentenceCreatePage';
import { SignupPage } from '@/pages/SignupPage';
import { SocialSignupPage } from '@/pages/SocialSignupPage';
import { TutorialPage } from '@/pages/TutorialPage';
import { RaidAdminPage } from '@/pages/RaidAdminPage';

/** 통합 전의 /boss/:id/battle 주소를 통합된 원정 화면으로 보낸다. */
function BossBattleRedirect() {
  const { bossRunId } = useParams();
  return <Navigate to={`/boss/${bossRunId}`} replace />;
}

/** 기존 문장 공방 주소로 저장한 초안 링크도 새 개인 문장 생성 화면에서 이어서 연다. */
function LegacyWorkshopRedirect() {
  const { search } = useLocation();
  return <Navigate to={`/sentences/new${search}`} replace />;
}

const router = createBrowserRouter([
  {
    element: (
      <FirstVisitTutorialGate>
        <AppLayout />
      </FirstVisitTutorialGate>
    ),
    children: [
      {
        index: true,
        element: <HomePage />,
      },
      {
        path: 'workshop',
        element: <LegacyWorkshopRedirect />,
      },
      {
        path: 'sentences/new',
        element: (
          <RequireAuthentication>
            <SentenceCreatePage />
          </RequireAuthentication>
        ),
      },
      {
        path: 'boss',
        element: <BossListPage />,
      },
      {
        // 상세와 전투 화면을 하나로 합쳤다. 이 주소가 원정의 단일 화면이다.
        path: 'boss/:bossRunId',
        element: (
          <RequireAuthentication>
            <BossBattlePage />
          </RequireAuthentication>
        ),
      },
      {
        // 통합 전 주소를 저장해 둔 북마크·공유 링크가 깨지지 않도록 남겨 둔 리다이렉트.
        path: 'boss/:bossRunId/battle',
        element: <BossBattleRedirect />,
      },
      {
        path: 'boss/:bossRunId/clear',
        element: (
          <RequireAuthentication>
            <BossClearPage />
          </RequireAuthentication>
        ),
      },
      {
        path: 'library',
        element: <LibraryPage />,
      },
      {
        path: 'library/books/:bookId',
        element: <BookDetailPage />,
      },
      {
        path: 'library/books/:bookId/read',
        element: (
          <RequireAuthentication>
            <BookReaderPage />
          </RequireAuthentication>
        ),
      },
      {
        path: 'archive',
        element: (
          <RequireAuthentication>
            <ArchivePage />
          </RequireAuthentication>
        ),
      },
      {
        path: 'archive/bookmarks',
        element: (
          <RequireAuthentication>
            <ArchiveBookmarksPage />
          </RequireAuthentication>
        ),
      },
      {
        path: 'archive/:sentenceVersionId',
        element: (
          <RequireAuthentication>
            <ArchiveDetailPage />
          </RequireAuthentication>
        ),
      },
      {
        path: 'my-page',
        element: (
          <RequireAuthentication>
            <MyPage />
          </RequireAuthentication>
        ),
      },
      {
        path: 'settings/notifications',
        element: (
          <RequireAuthentication>
            <NotificationSettingsPage />
          </RequireAuthentication>
        ),
      },
      {
        path: 'settings/service',
        element: (
          <RequireAuthentication>
            <ServiceSettingsPage />
          </RequireAuthentication>
        ),
      },
      {
        path: 'terms',
        element: <TermsPage />,
      },
      {
        path: 'privacy',
        element: <PrivacyPolicyPage />,
      },
      {
        path: 'hall-of-fame',
        element: (
          <RequireAuthentication>
            <HallOfFamePage />
          </RequireAuthentication>
        ),
      },
    ],
  },
  {
    // 관리 콘솔은 앱 셸과 폭·네비게이션이 다르므로 별도 레이아웃 아래에 둔다.
    // 권한 게이트는 AdminLayout이 담당한다.
    element: <AdminLayout />,
    children: [
      {
        path: 'admin/raids',
        element: <RaidAdminPage />,
      },
    ],
  },
  {
    path: '/tutorial',
    element: <TutorialPage />,
  },
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/signup',
    element: <SignupPage />,
  },
  {
    path: '/oauth/signup',
    element: <SocialSignupPage />,
  },
  {
    path: '*',
    element: <PlaceholderPage title="페이지를 찾을 수 없습니다" />,
  },
]);

export function AppRouter() {
  return <RouterProvider router={router} />;
}
