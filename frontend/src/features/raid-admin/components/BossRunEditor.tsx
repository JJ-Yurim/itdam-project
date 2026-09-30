import { useState, type FormEvent } from 'react';

import { Button, useToast } from '@/shared/components';

import { uploadBossImage } from '../api';
import { getRaidAdminErrorMessage, isConflictError } from '../errors';
import {
  useChangeAdminBossRunPhaseImageMutation,
  useUpdateAdminBossRunMutation,
} from '../mutations';
import type { AdminBossPhase, AdminBossRun } from '../types';
import { adminInputClass } from './adminFormStyles';
import { StatusBadge } from './StatusBadge';

const phaseStatusLabel: Record<AdminBossPhase['status'], string> = {
  WAITING: '대기',
  ACTIVE: '진행 중',
  CLEARED: '완료',
};

/** datetime-local 입력은 초와 오프셋이 없는 로컬 시각 문자열을 쓴다. */
const toLocalInput = (value: string | null) => {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
};

interface BossRunEditorProps {
  run: AdminBossRun;
  onClose: () => void;
}

/**
 * 레이드 상세 편집기. 서버가 상태별로 허용하는 변경이 다르므로 화면도 그에 맞춘다.
 * 예정·진행 중 회차는 종료 시각을 바꾸고(예정은 전체 교체 계약이라 현재 페이즈를 그대로 되돌려
 * 보낸다), 페이즈 이미지는 두 상태에서 모두 개별 교체한다. 종료된 회차는 읽기 전용이다.
 * 보스와 페이즈를 다시 짜는 것은 이번 범위가 아니다 — 취소하고 새로 만드는 흐름을 안내한다.
 */
export function BossRunEditor({ run, onClose }: BossRunEditorProps) {
  const [endsAt, setEndsAt] = useState(toLocalInput(run.endsAt));
  // 목록에서 받은 스냅샷을 그대로 두면 저장 후에도 옛 이미지와 옛 updatedAt이 남는다.
  // 응답을 이어받아 화면과 다음 요청의 expectedUpdatedAt을 함께 갱신한다.
  const [current, setCurrent] = useState(run);
  // 페이즈 이미지 교체는 자체 mutation을 쓰므로 부모가 충돌을 알 방법이 필요하다.
  // 모르면 배너가 뜨지 않고, 옛 expectedUpdatedAt으로 다시 눌러 계속 409가 된다.
  const [hasPhaseConflict, setHasPhaseConflict] = useState(false);
  const scheduleMutation = useUpdateAdminBossRunMutation();
  const { showToast } = useToast();
  const editable = current.status === 'SCHEDULED' || current.status === 'ACTIVE';

  const saveSchedule = (event: FormEvent) => {
    event.preventDefault();
    if (!endsAt) {
      showToast({ message: '종료 시각을 입력해 주세요.', variant: 'error' });
      return;
    }
    scheduleMutation.mutate(
      {
        runId: current.id,
        request: {
          // 서버는 상태별로 받는 필드가 다르다. 진행 중 회차에 bossId·phases를 함께 보내면 409로
          // 거절하고, 반대로 예정 회차는 전체 교체라서 bossId와 phases가 없으면 400이 된다.
          // 예정 회차의 페이즈는 지금 값을 그대로 되돌려 보내 일정만 바뀌게 한다.
          ...(current.status === 'SCHEDULED'
            ? {
                bossId: current.bossId,
                phases: current.phases.map((phase) => ({
                  maxHp: phase.maxHp,
                  hardMinScore: phase.hardMinScore ?? undefined,
                  recommendedScore: phase.recommendedScore ?? undefined,
                  damageMultiplier: phase.damageMultiplier,
                  imageAssetId: phase.imageAssetId!,
                })),
              }
            : {}),
          endsAt: new Date(endsAt).toISOString(),
          expectedUpdatedAt: current.updatedAt,
        },
      },
      {
        onSuccess: (updated) => {
          setCurrent(updated);
          setEndsAt(toLocalInput(updated.endsAt));
          showToast({ message: '레이드 일정을 수정했습니다.', variant: 'success' });
        },
      },
    );
  };

  return (
    <div className="space-y-6">
      {(isConflictError(scheduleMutation.error) || hasPhaseConflict) && (
        <div role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-700">
          <p>다른 관리자가 먼저 이 레이드를 수정했습니다. 최신 내용을 확인한 뒤 다시 시도해 주세요.</p>
          <Button variant="secondary" className="mt-2" onClick={onClose}>
            최신 데이터 다시 불러오기
          </Button>
        </div>
      )}

      <section className="space-y-1 text-sm text-stone-600">
        <p className="font-bold text-stone-900">
          {current.bossName} <span className="text-xs font-normal text-stone-400">#{current.id}</span>
        </p>
        <p>
          현재 페이즈 {current.currentPhaseNo} / {current.phases.length}
        </p>
        {!editable && (
          <p className="rounded-lg bg-stone-100 p-2 text-xs text-stone-600">
            종료된 레이드는 수정할 수 없습니다. 기록 확인만 가능합니다.
          </p>
        )}
      </section>

      {editable && (
        <form onSubmit={saveSchedule} className="space-y-3">
          <h3 className="text-sm font-bold text-stone-900">일정</h3>
          <label className="block text-sm font-semibold text-stone-700">
            종료 시각
            <input
              type="datetime-local"
              required
              value={endsAt}
              onChange={(event) => setEndsAt(event.target.value)}
              className={adminInputClass}
            />
          </label>
          <p className="text-xs text-stone-500">
            {current.status === 'ACTIVE'
              ? '진행 중 레이드는 종료 시각만 바꿀 수 있습니다.'
              : '예정 레이드의 페이즈 구성은 그대로 두고 종료 시각만 바꿉니다. 보스와 페이즈를 다시 짜야 하면 이 회차를 취소하고 새로 만들어 주세요.'}
          </p>
          {scheduleMutation.isError && !isConflictError(scheduleMutation.error) && (
            <p role="alert" className="text-sm text-red-600">
              {getRaidAdminErrorMessage(scheduleMutation.error, '레이드 일정을 수정하지 못했습니다.')}
            </p>
          )}
          <Button type="submit" disabled={scheduleMutation.isPending}>
            {scheduleMutation.isPending ? '저장 중...' : '일정 저장'}
          </Button>
        </form>
      )}

      <section className="space-y-3">
        <h3 className="text-sm font-bold text-stone-900">페이즈</h3>
        {editable && (
          <p className="text-xs text-stone-500">
            완료·진행·대기 페이즈의 이미지를 각각 교체할 수 있습니다. 교체하면 참여자 화면에 바로
            반영되고, HP와 전투 기록은 그대로 유지됩니다.
          </p>
        )}
        <ul className="space-y-3">
          {current.phases.map((phase) => (
            <li key={phase.phaseNo} className="rounded-xl bg-stone-50 p-3">
              <div className="flex items-center justify-between gap-2">
                <strong className="text-sm">페이즈 {phase.phaseNo}</strong>
                <StatusBadge
                  label={phaseStatusLabel[phase.status]}
                  tone={phase.status === 'ACTIVE' ? 'violet' : phase.status === 'CLEARED' ? 'emerald' : 'stone'}
                />
              </div>
              <p className="mt-1 text-xs text-stone-600">
                {phase.remainingHp.toLocaleString()} / {phase.maxHp.toLocaleString()} HP · 배율 ×
                {phase.damageMultiplier}
              </p>
              {editable ? (
                <PhaseImageControl
                  runId={current.id}
                  phase={phase}
                  expectedUpdatedAt={current.updatedAt}
                  onUpdated={(updated) => {
                    setHasPhaseConflict(false);
                    setCurrent(updated);
                  }}
                  onConflict={() => setHasPhaseConflict(true)}
                />
              ) : (
                phase.imageUrl && (
                  <img
                    src={phase.imageUrl}
                    alt={`${phase.phaseNo}번째 페이즈 이미지`}
                    className="mt-2 size-16 rounded-lg object-cover"
                  />
                )
              )}
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}

function PhaseImageControl({
  runId,
  phase,
  expectedUpdatedAt,
  onUpdated,
  onConflict,
}: {
  runId: number;
  phase: AdminBossPhase;
  expectedUpdatedAt: string;
  onUpdated: (run: AdminBossRun) => void;
  onConflict: () => void;
}) {
  const [file, setFile] = useState<File | null>(null);
  // 업로드에 성공한 자산 ID는 남긴다. 교체 호출만 실패해 다시 누를 때 같은 파일을 또 올리면
  // 어디에도 참조되지 않는 자산이 쌓인다.
  const [uploadedAssetId, setUploadedAssetId] = useState<number | null>(null);
  const [inputKey, setInputKey] = useState(0);
  const [isPending, setIsPending] = useState(false);
  const mutation = useChangeAdminBossRunPhaseImageMutation();
  const { showToast } = useToast();

  const replace = async () => {
    if (!file && uploadedAssetId === null) {
      showToast({ message: '교체할 페이즈 이미지를 선택해 주세요.', variant: 'error' });
      return;
    }
    setIsPending(true);
    try {
      let assetId = uploadedAssetId;
      if (assetId === null) {
        try {
          assetId = (await uploadBossImage(file!)).imageAssetId;
          setUploadedAssetId(assetId);
        } catch (error) {
          showToast({
            message: getRaidAdminErrorMessage(error, '페이즈 이미지를 업로드하지 못했습니다.'),
            variant: 'error',
          });
          return;
        }
      }
      try {
        const updated = await mutation.mutateAsync({
          runId,
          phaseNo: phase.phaseNo,
          request: { imageAssetId: assetId, expectedUpdatedAt },
        });
        onUpdated(updated);
      } catch (error) {
        if (isConflictError(error)) onConflict();
        showToast({
          message: getRaidAdminErrorMessage(error, '페이즈 이미지를 교체하지 못했습니다.'),
          variant: 'error',
        });
        return;
      }
      setFile(null);
      setUploadedAssetId(null);
      setInputKey((value) => value + 1);
      showToast({ message: `페이즈 ${phase.phaseNo} 이미지를 교체했습니다.`, variant: 'success' });
    } finally {
      setIsPending(false);
    }
  };

  return (
    <div className="mt-2 flex items-start gap-3">
      {phase.imageUrl && (
        <img
          src={phase.imageUrl}
          alt={`${phase.phaseNo}번째 페이즈 현재 이미지`}
          className="size-16 shrink-0 rounded-lg object-cover"
        />
      )}
      <div className="min-w-0 flex-1">
        <label className="block text-xs font-semibold text-stone-700">
          {`${phase.phaseNo}번째 페이즈 새 이미지`}
          <input
            key={inputKey}
            type="file"
            accept="image/png,image/jpeg,image/webp"
            disabled={isPending}
            onChange={(event) => {
              setFile(event.target.files?.[0] ?? null);
              setUploadedAssetId(null);
            }}
            className="mt-1 block w-full text-sm font-normal"
          />
        </label>
        <Button
          type="button"
          variant="secondary"
          // 패널에 페이즈 수만큼 놓이므로 대상 번호를 접근성 이름에 넣는다.
          aria-label={`${phase.phaseNo}번째 페이즈 이미지 변경`}
          className="mt-2 min-h-9 px-3 py-1 text-xs"
          disabled={isPending}
          onClick={() => void replace()}
        >
          {isPending ? '교체 중...' : '이미지 변경'}
        </Button>
      </div>
    </div>
  );
}
