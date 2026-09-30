import { Button } from '@/shared/components';

import { adminInputClass } from './adminFormStyles';
import { createPhaseInput, type PhaseTemplateInput } from './phaseInputs';

interface PhaseEditorProps {
  phases: PhaseTemplateInput[];
  onChange: (phases: PhaseTemplateInput[]) => void;
  /** 이미지 규칙처럼 모드마다 다른 안내. */
  note?: string;
  disabled?: boolean;
}

/**
 * 페이즈 템플릿 목록 편집기. 보스 생성과 템플릿 교체가 같은 입력을 쓰므로 한곳에 둔다.
 * 순서는 페이즈 번호를 그대로 따르므로 위·아래 이동으로만 바꾼다.
 */
export function PhaseEditor({ phases, onChange, note, disabled = false }: PhaseEditorProps) {
  const update = (index: number, values: Partial<PhaseTemplateInput>) =>
    onChange(phases.map((phase, i) => (i === index ? { ...phase, ...values } : phase)));

  const move = (index: number, offset: number) => {
    const target = index + offset;
    if (target < 0 || target >= phases.length) return;
    const reordered = [...phases];
    [reordered[index], reordered[target]] = [reordered[target], reordered[index]];
    onChange(reordered);
  };

  return (
    <fieldset className="space-y-3" disabled={disabled}>
      <legend className="text-sm font-semibold text-stone-700">페이즈 템플릿</legend>
      {note && <p className="text-xs text-stone-500">{note}</p>}

      {phases.map((phase, index) => (
        <div
          key={phase.id}
          role="group"
          aria-label={`${index + 1}번째 페이즈`}
          className="rounded-xl bg-stone-50 p-3"
        >
          <div className="flex items-center justify-between gap-2">
            <strong className="text-sm">페이즈 {index + 1}</strong>
            <div className="flex gap-1">
              <Button
                type="button"
                variant="secondary"
                aria-label={`${index + 1}번째 페이즈를 위로 이동`}
                disabled={index === 0}
                className="min-h-9 px-2 py-1 text-xs"
                onClick={() => move(index, -1)}
              >
                위로
              </Button>
              <Button
                type="button"
                variant="secondary"
                aria-label={`${index + 1}번째 페이즈를 아래로 이동`}
                disabled={index === phases.length - 1}
                className="min-h-9 px-2 py-1 text-xs"
                onClick={() => move(index, 1)}
              >
                아래로
              </Button>
              <Button
                type="button"
                variant="secondary"
                aria-label={`${index + 1}번째 페이즈 삭제`}
                disabled={phases.length === 1}
                className="min-h-9 px-2 py-1 text-xs text-red-600"
                onClick={() => onChange(phases.filter((_, i) => i !== index))}
              >
                삭제
              </Button>
            </div>
          </div>

          <div className="mt-2 flex items-start gap-3">
            {phase.currentImageUrl && (
              <img
                src={phase.currentImageUrl}
                alt={`${index + 1}번째 페이즈 현재 이미지`}
                className="size-16 shrink-0 rounded-lg object-cover"
              />
            )}
            <label className="min-w-0 flex-1 text-xs text-stone-600">
              {`${index + 1}번째 페이즈 이미지`}
              <input
                type="file"
                accept="image/png,image/jpeg,image/webp"
                onChange={(event) =>
                  update(index, { image: event.target.files?.[0] ?? null, uploadedAssetId: null })
                }
                className="mt-1 block w-full text-sm"
              />
            </label>
          </div>

          {/* 안내 문구를 라벨 안에 넣으면 입력의 접근성 이름에 섞여 읽힌다. */}
          <label className="mt-2 block text-xs text-stone-600">
            {`${index + 1}번째 페이즈 최대 HP`}
            <input
              type="number"
              min="1"
              value={phase.maxHp}
              aria-describedby={`${phase.id}-hp-hint`}
              onChange={(event) => update(index, { maxHp: event.target.value })}
              className={adminInputClass}
            />
          </label>
          <p id={`${phase.id}-hp-hint`} className="mt-1 text-xs text-stone-400">
            비우면 서버 기본값으로 산정됩니다.
          </p>
        </div>
      ))}

      <Button type="button" variant="secondary" onClick={() => onChange([...phases, createPhaseInput()])}>
        페이즈 추가
      </Button>
    </fieldset>
  );
}
