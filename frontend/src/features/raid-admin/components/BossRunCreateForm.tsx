import { useState, type FormEvent } from 'react';

import { Button, useToast } from '@/shared/components';

import { useCreateAdminBossRunMutation } from '../mutations';
import { getRaidAdminErrorMessage } from '../errors';
import type { AdminBoss } from '../types';

interface Props { bosses: AdminBoss[] }
interface PhaseInput { id: string; maxHp: string; hardMinScore: string; recommendedScore: string; damageMultiplier: string; imageAssetId: number }
type EditablePhaseField = Exclude<keyof PhaseInput, 'id' | 'imageAssetId'>;

let nextPhaseId = 0;
const emptyPhase = (): PhaseInput => ({ id: `run-phase-${nextPhaseId++}`, maxHp: '', hardMinScore: '', recommendedScore: '', damageMultiplier: '1', imageAssetId: 0 });
const inputClass = 'mt-1 min-h-11 w-full rounded-lg border border-stone-300 bg-white px-3 text-stone-900 focus:border-violet-500 focus:outline-none';
const optionalNumber = (value: string) => value === '' ? undefined : Number(value);

export function BossRunCreateForm({ bosses }: Props) {
  const [bossId, setBossId] = useState('');
  const [startsAt, setStartsAt] = useState('');
  const [endsAt, setEndsAt] = useState('');
  const [activate, setActivate] = useState(false);
  const [phases, setPhases] = useState<PhaseInput[]>([emptyPhase()]);
  const mutation = useCreateAdminBossRunMutation();
  const { showToast } = useToast();
  const selectedBoss = bosses.find((boss) => boss.id === Number(bossId));

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const now = new Date();
    const start = startsAt ? new Date(startsAt) : now;
    const end = new Date(endsAt);
    if (end <= now || end <= start) {
      showToast({ message: '종료 시각은 현재와 시작 시각보다 이후여야 합니다.', variant: 'error' });
      return;
    }
    if (activate && startsAt && start > now) {
      showToast({ message: '즉시 활성화할 레이드는 미래에 시작할 수 없습니다.', variant: 'error' });
      return;
    }
    mutation.mutate({
      bossId: Number(bossId),
      startsAt: startsAt ? new Date(startsAt).toISOString() : undefined,
      endsAt: new Date(endsAt).toISOString(),
      activate,
      phases: phases.map((phase) => ({
        maxHp: optionalNumber(phase.maxHp),
        hardMinScore: optionalNumber(phase.hardMinScore),
        recommendedScore: optionalNumber(phase.recommendedScore),
        damageMultiplier: optionalNumber(phase.damageMultiplier),
        imageAssetId: phase.imageAssetId,
      })),
    }, { onSuccess: () => { setBossId(''); setEndsAt(''); setStartsAt(''); setActivate(false); setPhases([emptyPhase()]); showToast({ message: '레이드 회차를 생성했습니다.', variant: 'success' }); } });
  };

  const updatePhase = (index: number, field: EditablePhaseField, value: string) =>
    setPhases((items) => items.map((item, i) => i === index ? { ...item, [field]: value } : item));

  return (
    <form onSubmit={submit} className="space-y-4 rounded-2xl border border-stone-200 p-4">
      <h2 className="text-lg font-bold text-stone-900">레이드 회차 생성</h2>
      <label className="block text-sm font-semibold text-stone-700">보스
        <select required value={selectedBoss ? bossId : ''} onChange={(e) => {
          const nextId = e.target.value;
          setBossId(nextId);
          const boss = bosses.find((item) => item.id === Number(nextId));
          setPhases(boss?.phaseTemplates.map((phase) => ({
            id: `run-template-${boss.id}-${phase.id}`,
            maxHp: String(phase.maxHp),
            hardMinScore: phase.hardMinScore == null ? '' : String(phase.hardMinScore),
            recommendedScore: phase.recommendedScore == null ? '' : String(phase.recommendedScore),
            damageMultiplier: String(phase.damageMultiplier),
            imageAssetId: phase.imageAssetId,
          })) ?? [emptyPhase()]);
        }} className={inputClass}>
          <option value="">선택하세요</option>
          {bosses.filter((boss) => boss.status === 'ACTIVE' && boss.phaseTemplates.length > 0).map((boss) => <option key={boss.id} value={boss.id}>{boss.name}</option>)}
        </select>
      </label>
      <div className="grid grid-cols-2 gap-3">
        <label className="text-sm font-semibold text-stone-700">시작 시각 (선택)<input type="datetime-local" value={startsAt} onChange={(e) => setStartsAt(e.target.value)} className={inputClass} /></label>
        <label className="text-sm font-semibold text-stone-700">종료 시각<input required type="datetime-local" value={endsAt} onChange={(e) => setEndsAt(e.target.value)} className={inputClass} /></label>
      </div>
      <label className="flex items-center gap-2 text-sm font-semibold text-stone-700"><input type="checkbox" checked={activate} onChange={(e) => setActivate(e.target.checked)} />즉시 활성화</label>

      <fieldset className="space-y-3">
        <legend className="text-sm font-semibold text-stone-700">페이즈</legend>
        {phases.map((phase, index) => (
          <div key={phase.id} className="rounded-xl bg-stone-50 p-3">
            <div className="flex items-center justify-between"><strong className="text-sm">페이즈 {index + 1}</strong><button type="button" disabled={phases.length === 1} onClick={() => setPhases((items) => items.filter((_, i) => i !== index))} className="text-xs text-red-600 disabled:opacity-40">삭제</button></div>
            <div className="mt-2 grid grid-cols-2 gap-2">
              <label className="text-xs text-stone-600">최대 HP (자동 산정)<input type="number" min="1" value={phase.maxHp} onChange={(e) => updatePhase(index, 'maxHp', e.target.value)} className={inputClass} /></label>
              <label className="text-xs text-stone-600">데미지 배율<input type="number" min="0.001" max="999.999" step="0.001" value={phase.damageMultiplier} onChange={(e) => updatePhase(index, 'damageMultiplier', e.target.value)} className={inputClass} /></label>
              <label className="text-xs text-stone-600">최소 점수<input type="number" min="0" max="9999.99" step="0.01" value={phase.hardMinScore} onChange={(e) => updatePhase(index, 'hardMinScore', e.target.value)} className={inputClass} /></label>
              <label className="text-xs text-stone-600">권장 점수<input type="number" min="0" max="9999.99" step="0.01" value={phase.recommendedScore} onChange={(e) => updatePhase(index, 'recommendedScore', e.target.value)} className={inputClass} /></label>
            </div>
          </div>
        ))}
        {!bossId && <p className="text-xs text-stone-500">보스를 선택하면 저장된 페이즈 템플릿을 불러옵니다.</p>}
        {bosses.some((boss) => boss.status === 'ACTIVE' && boss.phaseTemplates.length === 0) && <p className="text-xs text-amber-700">페이즈 템플릿이 없는 보스는 선택할 수 없습니다.</p>}
      </fieldset>
      {mutation.isError && <p role="alert" className="text-sm text-red-600">{getRaidAdminErrorMessage(mutation.error, '레이드를 생성하지 못했습니다.')}</p>}
      <Button type="submit" className="w-full" disabled={mutation.isPending || !selectedBoss || phases.some((phase) => phase.imageAssetId === 0)}>{mutation.isPending ? '생성 중...' : '레이드 생성'}</Button>
    </form>
  );
}
