import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';

import { Button, ErrorState, LoadingState, useToast } from '@/shared/components';

import { getRaidAdminErrorMessage } from '../errors';
import { useAdminBossRunActionMutation } from '../mutations';
import { useAdminBossOptionsQuery, useAdminBossRunsQuery } from '../queries';
import type { AdminBossRun, BossRunStatus } from '../types';
import { AdminDataTable } from './AdminDataTable';
import { AdminDrawer } from './AdminDrawer';
import { AdminFilters } from './AdminFilters';
import { AdminPagination } from './AdminPagination';
import { BossRunCreateForm } from './BossRunCreateForm';
import { BossRunEditor } from './BossRunEditor';
import { ConfirmDialog } from './ConfirmDialog';
import { StatusBadge } from './StatusBadge';
import { formatAdminDateTime } from './adminTableFormat';
import { readEnumParam, usePageParam } from './usePageParam';

type RunAction = 'activate' | 'cancel' | 'end';

const statusLabel: Record<BossRunStatus, string> = {
  SCHEDULED: '예정',
  ACTIVE: '진행 중',
  CLEARED: '클리어',
  ENDED_UNCLEARED: '미클리어 종료',
  CLOSED: '마감',
  CANCELLED: '취소',
};

const runStatuses = Object.keys(statusLabel) as BossRunStatus[];

const statusTone: Record<BossRunStatus, 'violet' | 'emerald' | 'amber' | 'stone' | 'red'> = {
  SCHEDULED: 'amber',
  ACTIVE: 'violet',
  CLEARED: 'emerald',
  ENDED_UNCLEARED: 'stone',
  CLOSED: 'stone',
  CANCELLED: 'red',
};

const actionCopy: Record<RunAction, { label: string; title: string; consequence: string; destructive: boolean }> = {
  activate: {
    label: '시작',
    title: '레이드 즉시 시작',
    consequence: '지금부터 참여자가 문장을 제출할 수 있습니다.',
    destructive: false,
  },
  cancel: {
    label: '취소',
    title: '레이드 취소',
    consequence: '이 레이드는 참여자 목록에서 사라집니다. 이미 만들어진 기록은 그대로 남습니다.',
    destructive: true,
  },
  end: {
    label: '조기 종료',
    title: '레이드 조기 종료',
    consequence: '미클리어 종료로 바뀌어 더 이상 문장을 받지 않습니다.',
    destructive: true,
  },
};

const remainingHp = (run: AdminBossRun) =>
  run.phases.reduce((total, phase) => total + phase.remainingHp, 0);

export function RaidManagementTab() {
  const [searchParams] = useSearchParams();
  const [page, changePage] = usePageParam();
  // 탭마다 status의 값 domain이 다르므로 허용 목록으로 검증한다. 알 수 없는 값을 그대로 넘기면
  // 셀렉트는 비어 있는데 목록만 걸러진 채로 보인다.
  const statusParam = readEnumParam(searchParams.get('status'), runStatuses);
  const filters = {
    query: searchParams.get('query') ?? undefined,
    statuses: statusParam ? [statusParam] : undefined,
    page,
  };
  const runsQuery = useAdminBossRunsQuery(filters);
  const actionMutation = useAdminBossRunActionMutation();
  const [editorTarget, setEditorTarget] = useState<AdminBossRun | null>(null);
  const [actionTarget, setActionTarget] = useState<{ run: AdminBossRun; action: RunAction } | null>(null);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  // 생성 폼의 보스 선택지는 패널을 열 때만 필요하다.
  const bossOptionsQuery = useAdminBossOptionsQuery(isCreateOpen);
  const { showToast } = useToast();

  const confirmAction = () => {
    if (!actionTarget) return;
    const { run, action } = actionTarget;
    actionMutation.mutate(
      { runId: run.id, action, request: { expectedUpdatedAt: run.updatedAt } },
      {
        onSuccess: () =>
          showToast({ message: `레이드를 ${actionCopy[action].label}했습니다.`, variant: 'success' }),
        onError: (error) =>
          showToast({
            message: getRaidAdminErrorMessage(error, '레이드 상태를 바꾸지 못했습니다.'),
            variant: 'error',
          }),
      },
    );
    setActionTarget(null);
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
              options: runStatuses.map((status) => ({
                value: status,
                label: statusLabel[status],
              })),
            },
          ]}
        />
        <Button onClick={() => setIsCreateOpen(true)}>새 레이드</Button>
      </div>

      {runsQuery.isPending ? (
        <LoadingState title="레이드 목록을 불러오고 있습니다" />
      ) : runsQuery.isError ? (
        <ErrorState
          title="레이드 목록을 불러오지 못했습니다"
          description="잠시 후 다시 시도해 주세요."
          onRetry={() => void runsQuery.refetch()}
        />
      ) : (
        <>
          <p className="text-sm text-stone-500">총 {runsQuery.data.totalElements}개</p>
          <AdminDataTable
            label="레이드 목록"
            rows={runsQuery.data.content}
            rowKey={(run) => run.id}
            emptyTitle="생성된 레이드가 없습니다"
            emptyDescription="검색 조건을 지우거나 레이드 관리 탭에서 새 회차를 만들어 주세요."
            columns={[
              {
                key: 'boss',
                header: '보스',
                render: (run) => (
                  <div className="min-w-0">
                    <p className="font-semibold text-stone-900">{run.bossName}</p>
                    <p className="text-xs text-stone-400">#{run.id}</p>
                  </div>
                ),
              },
              {
                key: 'status',
                header: '상태',
                render: (run) => (
                  <StatusBadge label={statusLabel[run.status]} tone={statusTone[run.status]} />
                ),
              },
              {
                key: 'schedule',
                header: '일정',
                render: (run) => (
                  <span className="text-xs text-stone-600">
                    {formatAdminDateTime(run.startedAt)} – {formatAdminDateTime(run.endsAt)}
                  </span>
                ),
              },
              {
                key: 'phase',
                header: '현재 페이즈',
                align: 'right',
                render: (run) => `${run.currentPhaseNo} / ${run.phases.length}`,
              },
              {
                key: 'hp',
                header: '남은 HP',
                align: 'right',
                render: (run) => remainingHp(run).toLocaleString(),
              },
              {
                key: 'actions',
                header: '관리',
                align: 'right',
                render: (run) => (
                  <RunRowActions
                    run={run}
                    onOpen={() => setEditorTarget(run)}
                    onAction={(action) => setActionTarget({ run, action })}
                  />
                ),
              },
            ]}
            renderCard={(run) => (
              <div className="space-y-2">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="font-semibold text-stone-900">{run.bossName}</p>
                    <p className="text-xs text-stone-400">#{run.id}</p>
                  </div>
                  <StatusBadge label={statusLabel[run.status]} tone={statusTone[run.status]} />
                </div>
                <p className="text-xs text-stone-600">
                  {formatAdminDateTime(run.startedAt)} – {formatAdminDateTime(run.endsAt)}
                </p>
                <p className="text-xs text-stone-500">
                  페이즈 {run.currentPhaseNo} / {run.phases.length} · 남은 HP{' '}
                  {remainingHp(run).toLocaleString()}
                </p>
                <RunRowActions
                  run={run}
                  onOpen={() => setEditorTarget(run)}
                  onAction={(action) => setActionTarget({ run, action })}
                />
              </div>
            )}
          />
          <AdminPagination
            page={runsQuery.data.page}
            totalPages={runsQuery.data.totalPages}
            hasNext={runsQuery.data.hasNext}
            label="레이드 회차"
            onChange={changePage}
          />
        </>
      )}

      <AdminDrawer
        open={editorTarget !== null}
        title="레이드 상세"
        onClose={() => setEditorTarget(null)}
      >
        {/* 대상이 바뀌면 패널 상태를 확실히 초기화한다. */}
        {editorTarget && (
          <BossRunEditor
            key={editorTarget.id}
            run={editorTarget}
            onClose={() => setEditorTarget(null)}
          />
        )}
      </AdminDrawer>

      <AdminDrawer open={isCreateOpen} title="새 레이드 등록" onClose={() => setIsCreateOpen(false)}>
        {bossOptionsQuery.isPending ? (
          <LoadingState title="보스 선택지를 불러오고 있습니다" />
        ) : bossOptionsQuery.isError ? (
          <ErrorState
            title="보스 선택지를 불러오지 못했습니다"
            description="잠시 후 다시 시도해 주세요."
            onRetry={() => void bossOptionsQuery.refetch()}
          />
        ) : (
          <BossRunCreateForm bosses={bossOptionsQuery.data} />
        )}
      </AdminDrawer>

      <ConfirmDialog
        open={actionTarget !== null}
        title={actionTarget ? actionCopy[actionTarget.action].title : ''}
        description={
          actionTarget
            ? `${actionTarget.run.bossName} #${actionTarget.run.id} · ${actionCopy[actionTarget.action].consequence}`
            : ''
        }
        confirmLabel={actionTarget ? actionCopy[actionTarget.action].label : ''}
        destructive={actionTarget ? actionCopy[actionTarget.action].destructive : false}
        onConfirm={confirmAction}
        onCancel={() => setActionTarget(null)}
      />
    </div>
  );
}

function RunRowActions({
  run,
  onOpen,
  onAction,
}: {
  run: AdminBossRun;
  onOpen: () => void;
  onAction: (action: RunAction) => void;
}) {
  // 상태별 허용 동작만 노출한다. 서버가 409로 거절할 버튼을 띄우면 눌러 봐야 실패를 알게 된다.
  const actions: RunAction[] =
    run.status === 'SCHEDULED' ? ['activate', 'cancel'] : run.status === 'ACTIVE' ? ['end'] : [];
  const editable = run.status === 'SCHEDULED' || run.status === 'ACTIVE';

  return (
    <div className="flex flex-wrap justify-end gap-2">
      <Button
        variant="secondary"
        aria-label={`${run.bossName} #${run.id} ${editable ? '수정' : '상세'}`}
        className="min-h-9 px-3 py-1 text-xs"
        onClick={onOpen}
      >
        {editable ? '수정' : '상세'}
      </Button>
      {actions.map((action) => (
        <Button
          key={action}
          variant="secondary"
          aria-label={`${run.bossName} #${run.id} ${actionCopy[action].label}`}
          className="min-h-9 px-3 py-1 text-xs"
          onClick={() => onAction(action)}
        >
          {actionCopy[action].label}
        </Button>
      ))}
    </div>
  );
}
