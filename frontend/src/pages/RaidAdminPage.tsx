import { Navigate } from 'react-router-dom';

import { useCurrentUserQuery } from '@/features/auth';
import { AdminTabs } from '@/features/raid-admin/components/AdminTabs';
import { BookManagementTab } from '@/features/raid-admin/components/BookManagementTab';
import { BossManagementTab } from '@/features/raid-admin/components/BossManagementTab';
import { RaidManagementTab } from '@/features/raid-admin/components/RaidManagementTab';
import { LoadingState } from '@/shared/components';

const adminTabs = [
  { id: 'bosses', label: '보스 관리' },
  { id: 'runs', label: '레이드 관리' },
  { id: 'books', label: '책 관리' },
];

export function RaidAdminPage() {
  const userQuery = useCurrentUserQuery();

  if (userQuery.isPending) return <LoadingState title="관리자 권한을 확인하고 있습니다" />;
  if (!userQuery.data) return <Navigate to="/login" replace />;
  if (userQuery.data.role !== 'ADMIN') return <Navigate to="/" replace />;

  return (
    <section className="space-y-6">
      <header>
        <h1 className="text-2xl font-extrabold text-violet-800">레이드 관리</h1>
        <p className="mt-2 text-sm text-stone-500">
          보스 원형과 실제 운영할 레이드 회차, 완성된 책의 표지와 공개 상태를 관리합니다.
        </p>
      </header>

      {/* 각 탭이 자기 목록만 조회한다. 보이지 않는 탭까지 함께 불러오면 요청이 세 배로 늘고,
          한 목록의 실패가 다른 탭을 함께 막는다. */}
      <AdminTabs
        label="관리 메뉴"
        tabs={adminTabs}
        renderPanel={(tabId) =>
          tabId === 'runs' ? (
            <RaidManagementTab />
          ) : tabId === 'books' ? (
            <BookManagementTab />
          ) : (
            <BossManagementTab />
          )
        }
      />
    </section>
  );
}
