import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';

import { Button, ErrorState, LoadingState, useToast } from '@/shared/components';

import { useAdminBossesQuery } from '../queries';
import { useUpdateAdminBossStatusMutation } from '../mutations';
import { getRaidAdminErrorMessage } from '../errors';
import type { AdminBoss, BossStatus } from '../types';
import { AdminDataTable } from './AdminDataTable';
import { AdminDrawer } from './AdminDrawer';
import { AdminFilters } from './AdminFilters';
import { AdminPagination } from './AdminPagination';
import { BossEditor } from './BossEditor';
import { ConfirmDialog } from './ConfirmDialog';
import { StatusBadge } from './StatusBadge';

const statusLabel: Record<BossStatus, string> = { ACTIVE: '활성', INACTIVE: '비활성' };

const dateTimeFormatter = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'short',
  timeStyle: 'short',
});

// Intl은 잘못된 Date에 RangeError를 던진다. updatedAt은 서버 계약에 새로 들어온 필드라
// 배포 순서에 따라 빠질 수 있고, 그때 목록이 렌더 중에 죽으면 오류 화면조차 못 보여 준다.
const formatDateTime = (value: string | null | undefined) => {
  const date = value ? new Date(value) : null;
  return date && !Number.isNaN(date.getTime()) ? dateTimeFormatter.format(date) : '-';
};

const moodSummary = (boss: AdminBoss) =>
  boss.moods.map((mood) => `${mood.displayName}(${mood.role === 'THEME' ? '주제' : '약점'})`).join(', ');

/** 편집 대상. 'new'는 생성 패널이고 보스 객체는 수정 패널이다. */
type EditorTarget = { mode: 'create' } | { mode: 'update'; boss: AdminBoss };

export function BossManagementTab() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = Math.max(Number(searchParams.get('page') ?? 0) || 0, 0);
  const filters = {
    query: searchParams.get('query') ?? undefined,
    status: (searchParams.get('status') as BossStatus | null) ?? undefined,
    page,
  };
  const bossesQuery = useAdminBossesQuery(filters);
  const statusMutation = useUpdateAdminBossStatusMutation();
  const [editorTarget, setEditorTarget] = useState<EditorTarget | null>(null);
  const [statusTarget, setStatusTarget] = useState<AdminBoss | null>(null);
  const { showToast } = useToast();

  const changePage = (next: number) => {
    const params = new URLSearchParams(searchParams);
    if (next > 0) params.set('page', String(next));
    else params.delete('page');
    setSearchParams(params, { replace: true });
  };

  const confirmStatusChange = () => {
    if (!statusTarget) return;
    const nextStatus: BossStatus = statusTarget.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    statusMutation.mutate(
      {
        bossId: statusTarget.id,
        request: { status: nextStatus, expectedUpdatedAt: statusTarget.updatedAt },
      },
      {
        onSuccess: () =>
          showToast({
            message: nextStatus === 'ACTIVE' ? '보스를 활성화했습니다.' : '보스를 비활성화했습니다.',
            variant: 'success',
          }),
        onError: (error) =>
          showToast({
            message: getRaidAdminErrorMessage(error, '보스 상태를 바꾸지 못했습니다.'),
            variant: 'error',
          }),
      },
    );
    setStatusTarget(null);
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <AdminFilters
          searchLabel="보스 이름 검색"
          selects={[
            {
              key: 'status',
              label: '상태',
              emptyLabel: '전체 상태',
              options: [
                { value: 'ACTIVE', label: '활성' },
                { value: 'INACTIVE', label: '비활성' },
              ],
            },
          ]}
        />
        <Button onClick={() => setEditorTarget({ mode: 'create' })}>새 보스</Button>
      </div>

      {bossesQuery.isPending ? (
        <LoadingState title="보스 목록을 불러오고 있습니다" />
      ) : bossesQuery.isError ? (
        <ErrorState
          title="보스 목록을 불러오지 못했습니다"
          description="잠시 후 다시 시도해 주세요."
          onRetry={() => void bossesQuery.refetch()}
        />
      ) : (
        <>
          <p className="text-sm text-stone-500">총 {bossesQuery.data.totalElements}개</p>
          <AdminDataTable
            label="보스 목록"
            rows={bossesQuery.data.content}
            rowKey={(boss) => boss.id}
            emptyTitle="등록된 보스가 없습니다"
            emptyDescription="검색 조건을 지우거나 새 보스를 등록해 주세요."
            columns={[
              { key: 'cover', header: '표지', render: (boss) => <BossCover boss={boss} /> },
              {
                key: 'name',
                header: '이름',
                render: (boss) => (
                  <div className="min-w-0">
                    <p className="font-semibold text-stone-900">{boss.name}</p>
                    <p className="truncate text-xs text-stone-500">{boss.description}</p>
                  </div>
                ),
              },
              {
                key: 'status',
                header: '상태',
                render: (boss) => (
                  <StatusBadge
                    label={statusLabel[boss.status]}
                    tone={boss.status === 'ACTIVE' ? 'emerald' : 'stone'}
                  />
                ),
              },
              {
                key: 'moods',
                header: '분위기',
                render: (boss) => <span className="text-xs text-stone-600">{moodSummary(boss)}</span>,
              },
              {
                key: 'phases',
                header: '페이즈',
                align: 'right',
                render: (boss) => `${boss.phaseTemplates.length}개`,
              },
              {
                key: 'updatedAt',
                header: '수정 시각',
                align: 'right',
                render: (boss) => (
                  <span className="text-xs text-stone-500">{formatDateTime(boss.updatedAt)}</span>
                ),
              },
              {
                key: 'actions',
                header: '관리',
                align: 'right',
                render: (boss) => (
                  <BossRowActions
                    boss={boss}
                    onEdit={() => setEditorTarget({ mode: 'update', boss })}
                    onToggleStatus={() => setStatusTarget(boss)}
                  />
                ),
              },
            ]}
            renderCard={(boss) => (
              <div className="space-y-2">
                <div className="flex items-start justify-between gap-2">
                  <div className="flex min-w-0 items-center gap-2">
                    <BossCover boss={boss} />
                    <div className="min-w-0">
                      <p className="font-semibold text-stone-900">{boss.name}</p>
                      <p className="truncate text-xs text-stone-500">{boss.description}</p>
                    </div>
                  </div>
                  <StatusBadge
                    label={statusLabel[boss.status]}
                    tone={boss.status === 'ACTIVE' ? 'emerald' : 'stone'}
                  />
                </div>
                <p className="text-xs text-stone-600">{moodSummary(boss)}</p>
                <p className="text-xs text-stone-500">
                  페이즈 {boss.phaseTemplates.length}개 · 수정 {formatDateTime(boss.updatedAt)}
                </p>
                <BossRowActions
                  boss={boss}
                  onEdit={() => setEditorTarget({ mode: 'update', boss })}
                  onToggleStatus={() => setStatusTarget(boss)}
                />
              </div>
            )}
          />
          <AdminPagination
            page={bossesQuery.data.page}
            totalPages={bossesQuery.data.totalPages}
            hasNext={bossesQuery.data.hasNext}
            label="보스 원형"
            onChange={changePage}
          />
        </>
      )}

      <AdminDrawer
        open={editorTarget !== null}
        title={editorTarget?.mode === 'update' ? '보스 수정' : '새 보스 등록'}
        onClose={() => setEditorTarget(null)}
      >
        {editorTarget && (
          <BossEditor
            boss={editorTarget.mode === 'update' ? editorTarget.boss : undefined}
            onClose={() => setEditorTarget(null)}
          />
        )}
      </AdminDrawer>

      <ConfirmDialog
        open={statusTarget !== null}
        title={statusTarget?.status === 'ACTIVE' ? '보스 비활성화' : '보스 활성화'}
        description={
          statusTarget?.status === 'ACTIVE'
            ? `${statusTarget.name}이(가) 새 레이드 선택지에서 빠집니다. 이미 만들어진 레이드와 책은 그대로 남습니다.`
            : `${statusTarget?.name ?? ''}을(를) 새 레이드에 다시 사용할 수 있게 됩니다.`
        }
        confirmLabel={statusTarget?.status === 'ACTIVE' ? '비활성화' : '활성화'}
        destructive={statusTarget?.status === 'ACTIVE'}
        onConfirm={confirmStatusChange}
        onCancel={() => setStatusTarget(null)}
      />
    </div>
  );
}

function BossCover({ boss }: { boss: AdminBoss }) {
  return boss.bookCoverImageUrl ? (
    <img
      src={boss.bookCoverImageUrl}
      alt={`${boss.name} 기본 표지`}
      className="size-12 shrink-0 rounded-lg object-cover"
    />
  ) : (
    <div className="flex size-12 shrink-0 items-center justify-center rounded-lg bg-stone-200 text-[10px] text-stone-500">
      표지 없음
    </div>
  );
}

function BossRowActions({
  boss,
  onEdit,
  onToggleStatus,
}: {
  boss: AdminBoss;
  onEdit: () => void;
  onToggleStatus: () => void;
}) {
  return (
    // 한 화면에 같은 이름의 버튼이 행마다 놓이므로 대상 이름을 접근성 이름에 넣는다.
    <div className="flex justify-end gap-2">
      <Button
        variant="secondary"
        aria-label={`${boss.name} 수정`}
        className="min-h-9 px-3 py-1 text-xs"
        onClick={onEdit}
      >
        수정
      </Button>
      <Button
        variant="secondary"
        aria-label={`${boss.name} ${boss.status === 'ACTIVE' ? '비활성화' : '활성화'}`}
        className="min-h-9 px-3 py-1 text-xs"
        onClick={onToggleStatus}
      >
        {boss.status === 'ACTIVE' ? '비활성화' : '활성화'}
      </Button>
    </div>
  );
}
