import { useState, type FormEvent } from 'react';

import { Button, useToast } from '@/shared/components';
import { moodCodes, moodLabels, type MoodCode } from '@/shared/moods';

import { uploadBookCoverImage, uploadBossImage } from '../api';
import { getRaidAdminErrorMessage } from '../errors';
import {
  useChangeBossBookCoverMutation,
  useCreateAdminBossMutation,
  useUpdateAdminBossMutation,
  useUpdateAdminBossPhaseTemplatesMutation,
} from '../mutations';
import type { AdminBoss, MoodRole } from '../types';
import { adminInputClass } from './adminFormStyles';
import { CoverReplaceControl } from './CoverReplaceControl';
import { PhaseEditor } from './PhaseEditor';
import { createPhaseInput, type PhaseTemplateInput } from './phaseInputs';

interface MoodInput {
  id: string;
  code: MoodCode;
  role: MoodRole;
  weight: string;
}

let nextMoodId = 0;
const createMood = (values: Partial<MoodInput> = {}): MoodInput => ({
  id: `mood-${nextMoodId++}`,
  code: 'JOY',
  role: 'THEME',
  weight: '1',
  ...values,
});

interface BossEditorProps {
  /** 없으면 생성, 있으면 수정 모드다. */
  boss?: AdminBoss;
  onClose: () => void;
}

/**
 * 보스 생성과 수정을 함께 담는 편집기.
 *
 * 수정은 서버 계약이 정보·페이즈 템플릿·표지로 나뉘어 있고 각각 별도의 동시 수정 검증을
 * 거치므로, 한 번의 저장으로 묶지 않고 구획마다 따로 저장한다. 하나가 실패해도 나머지
 * 작업과 입력값이 남는다.
 */
export function BossEditor({ boss, onClose }: BossEditorProps) {
  return boss ? <BossUpdateForms boss={boss} onClose={onClose} /> : <BossCreateForm onClose={onClose} />;
}

function BossCreateForm({ onClose }: { onClose: () => void }) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [moods, setMoods] = useState<MoodInput[]>([createMood()]);
  const [phases, setPhases] = useState<PhaseTemplateInput[]>([createPhaseInput()]);
  const [bookCover, setBookCover] = useState<File | null>(null);
  // 업로드에 성공한 자산 ID는 남겨 둔다. 일부 이미지만 실패해 다시 제출할 때 이미 올라간
  // 파일을 또 올리면 참조되지 않는 자산이 쌓인다.
  const [bookCoverAssetId, setBookCoverAssetId] = useState<number | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const mutation = useCreateAdminBossMutation();
  const { showToast } = useToast();

  // 이미지 입력에는 required를 쓰지 않는다. 브라우저 기본 검증이 먼저 막으면 아래 안내
  // 문구가 뜰 일이 없고, 어느 이미지가 빠졌는지도 알려 주지 못한다.
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!bookCover) {
      showToast({ message: '책 표지 이미지를 선택해 주세요.', variant: 'error' });
      return;
    }
    if (phases.some((phase) => !phase.image)) {
      showToast({ message: '모든 페이즈에 이미지를 선택해 주세요.', variant: 'error' });
      return;
    }
    if (!validateMoods(moods, showToast)) return;

    setIsUploading(true);
    const [coverResult, ...uploadResults] = await Promise.allSettled([
      bookCoverAssetId
        ? Promise.resolve({ imageAssetId: bookCoverAssetId })
        : uploadBookCoverImage(bookCover),
      ...phases.map((phase) =>
        phase.uploadedAssetId
          ? Promise.resolve({ imageAssetId: phase.uploadedAssetId })
          : uploadBossImage(phase.image!),
      ),
    ]);
    const phasesWithUploads = phases.map((phase, index) => {
      const result = uploadResults[index];
      return result?.status === 'fulfilled'
        ? { ...phase, uploadedAssetId: result.value.imageAssetId }
        : phase;
    });
    setPhases(phasesWithUploads);
    const uploadedCoverId = coverResult?.status === 'fulfilled' ? coverResult.value.imageAssetId : null;
    setBookCoverAssetId(uploadedCoverId);
    setIsUploading(false);
    if (uploadedCoverId === null) {
      showToast({ message: '책 표지 이미지 업로드에 실패했습니다. 다시 시도해 주세요.', variant: 'error' });
      return;
    }
    if (uploadResults.some((result) => result.status === 'rejected')) {
      showToast({ message: '일부 페이즈 이미지 업로드에 실패했습니다. 다시 시도해 주세요.', variant: 'error' });
      return;
    }

    mutation.mutate(
      {
        name: name.trim(),
        description: description.trim(),
        bookCoverImageAssetId: uploadedCoverId,
        moods: moods.map(({ code, role, weight }) => ({ code, role, weight: Number(weight) })),
        phaseTemplates: phasesWithUploads.map((phase) => ({
          maxHp: phase.maxHp ? Number(phase.maxHp) : undefined,
          imageAssetId: phase.uploadedAssetId!,
        })),
      },
      {
        onSuccess: () => {
          showToast({ message: '보스 원형을 생성했습니다.', variant: 'success' });
          onClose();
        },
      },
    );
  };

  return (
    <form onSubmit={submit} className="space-y-4">
      <NameAndDescriptionFields
        name={name}
        description={description}
        onNameChange={setName}
        onDescriptionChange={setDescription}
      />

      <label className="block text-sm font-semibold text-stone-700">
        책 표지 이미지
        <input
          type="file"
          accept="image/png,image/jpeg,image/webp"
          onChange={(event) => {
            setBookCover(event.target.files?.[0] ?? null);
            setBookCoverAssetId(null);
          }}
          className="mt-1 block w-full text-sm font-normal"
        />
        <span className="mt-1 block text-xs font-normal text-stone-500">
          이 보스의 레이드가 끝나고 만들어지는 책의 기본 표지입니다.
        </span>
      </label>

      <MoodFields moods={moods} onChange={setMoods} />
      <PhaseEditor phases={phases} onChange={setPhases} />

      {mutation.isError && (
        <p role="alert" className="text-sm text-red-600">
          {getRaidAdminErrorMessage(mutation.error, '보스를 생성하지 못했습니다.')}
        </p>
      )}
      <Button type="submit" className="w-full" disabled={mutation.isPending || isUploading}>
        {isUploading ? '이미지 업로드 중...' : mutation.isPending ? '생성 중...' : '보스 생성'}
      </Button>
    </form>
  );
}

function BossUpdateForms({ boss, onClose }: { boss: AdminBoss; onClose: () => void }) {
  const [name, setName] = useState(boss.name);
  const [description, setDescription] = useState(boss.description);
  const [moods, setMoods] = useState<MoodInput[]>(
    boss.moods.map((mood) =>
      createMood({ code: mood.code as MoodCode, role: mood.role, weight: String(mood.weight) }),
    ),
  );
  const [phases, setPhases] = useState<PhaseTemplateInput[]>(
    boss.phaseTemplates.map((template) =>
      createPhaseInput({ maxHp: String(template.maxHp), currentImageUrl: template.imageUrl }),
    ),
  );
  const [isUploading, setIsUploading] = useState(false);
  // 구획을 하나 저장하면 서버의 updatedAt이 바뀐다. 목록에서 받아 온 스냅샷을 계속 쓰면
  // 두 번째 저장이 자기 자신 때문에 409가 되므로, 응답의 새 값을 이어받는다.
  // 세 갈래(정보·페이즈 템플릿·표지) 모두 updatedAt을 갱신하며 갱신된 보스를 반환한다.
  const [expectedUpdatedAt, setExpectedUpdatedAt] = useState(boss.updatedAt);
  const infoMutation = useUpdateAdminBossMutation();
  const templatesMutation = useUpdateAdminBossPhaseTemplatesMutation();
  const coverMutation = useChangeBossBookCoverMutation();
  const { showToast } = useToast();

  const saveInfo = (event: FormEvent) => {
    event.preventDefault();
    if (!validateMoods(moods, showToast)) return;
    infoMutation.mutate(
      {
        bossId: boss.id,
        request: {
          name: name.trim(),
          description: description.trim(),
          moods: moods.map(({ code, role, weight }) => ({ code, role, weight: Number(weight) })),
          expectedUpdatedAt,
        },
      },
      {
        onSuccess: (updated) => {
          setExpectedUpdatedAt(updated.updatedAt);
          showToast({ message: '보스 정보를 수정했습니다.', variant: 'success' });
        },
      },
    );
  };

  const saveTemplates = async (event: FormEvent) => {
    event.preventDefault();
    // 서버는 새로 지정하는 페이즈 이미지를 방금 올린 자산으로만 받는다. 기존 이미지를 그대로
    // 두는 교체는 계약상 불가능하므로 모든 페이즈에 새 파일이 필요하다.
    if (phases.some((phase) => !phase.image && !phase.uploadedAssetId)) {
      showToast({ message: '모든 페이즈에 이미지를 선택해 주세요.', variant: 'error' });
      return;
    }

    setIsUploading(true);
    const results = await Promise.allSettled(
      phases.map((phase) =>
        phase.uploadedAssetId
          ? Promise.resolve({ imageAssetId: phase.uploadedAssetId })
          : uploadBossImage(phase.image!),
      ),
    );
    const uploaded = phases.map((phase, index) => {
      const result = results[index];
      return result?.status === 'fulfilled'
        ? { ...phase, uploadedAssetId: result.value.imageAssetId }
        : phase;
    });
    setPhases(uploaded);
    setIsUploading(false);
    if (results.some((result) => result.status === 'rejected')) {
      showToast({ message: '일부 페이즈 이미지 업로드에 실패했습니다. 다시 시도해 주세요.', variant: 'error' });
      return;
    }

    templatesMutation.mutate(
      {
        bossId: boss.id,
        request: {
          phaseTemplates: uploaded.map((phase) => ({
            maxHp: phase.maxHp ? Number(phase.maxHp) : undefined,
            imageAssetId: phase.uploadedAssetId!,
          })),
          expectedUpdatedAt,
        },
      },
      {
        onSuccess: (updated) => {
          setExpectedUpdatedAt(updated.updatedAt);
          // 방금 올린 이미지는 서버에서 ACTIVE가 되어 다시 쓸 수 없다. 새 파일을 고르게 비운다.
          setPhases(
            updated.phaseTemplates.map((template) =>
              createPhaseInput({
                maxHp: String(template.maxHp),
                currentImageUrl: template.imageUrl,
              }),
            ),
          );
          showToast({ message: '페이즈 템플릿을 교체했습니다.', variant: 'success' });
        },
      },
    );
  };

  const staleError = [infoMutation.error, templatesMutation.error].find(isConflict);

  return (
    <div className="space-y-8">
      {staleError && (
        <div role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-700">
          <p>다른 관리자가 먼저 이 보스를 수정했습니다. 최신 내용을 확인한 뒤 다시 시도해 주세요.</p>
          <Button variant="secondary" className="mt-2" onClick={onClose}>
            최신 데이터 다시 불러오기
          </Button>
        </div>
      )}

      <form onSubmit={saveInfo} className="space-y-4">
        <h3 className="text-sm font-bold text-stone-900">기본 정보</h3>
        <NameAndDescriptionFields
          name={name}
          description={description}
          onNameChange={setName}
          onDescriptionChange={setDescription}
        />
        <MoodFields moods={moods} onChange={setMoods} />
        {infoMutation.isError && !isConflict(infoMutation.error) && (
          <p role="alert" className="text-sm text-red-600">
            {getRaidAdminErrorMessage(infoMutation.error, '보스 정보를 수정하지 못했습니다.')}
          </p>
        )}
        <Button type="submit" disabled={infoMutation.isPending}>
          {infoMutation.isPending ? '저장 중...' : '기본 정보 저장'}
        </Button>
      </form>

      <section>
        <h3 className="text-sm font-bold text-stone-900">기본 책 표지</h3>
        <CoverReplaceControl
          currentImageUrl={boss.bookCoverImageUrl}
          targetName={boss.name}
          note="앞으로 만들어지는 책에만 적용됩니다. 이미 만들어진 책은 책 표지 관리 탭에서 따로 바꿔 주세요."
          onReplace={async (imageAssetId) => {
            // 표지 교체도 보스의 updatedAt을 올리므로 다음 구획 저장을 위해 이어받는다.
            const updated = await coverMutation.mutateAsync({ bossId: boss.id, imageAssetId });
            setExpectedUpdatedAt(updated.updatedAt);
          }}
        />
      </section>

      <form onSubmit={saveTemplates} className="space-y-4">
        <PhaseEditor
          phases={phases}
          onChange={setPhases}
          note="템플릿은 전체 교체입니다. 저장하려면 모든 페이즈의 이미지를 다시 선택해 주세요. 이미 진행 중인 레이드는 바뀌지 않습니다."
        />
        {templatesMutation.isError && !isConflict(templatesMutation.error) && (
          <p role="alert" className="text-sm text-red-600">
            {getRaidAdminErrorMessage(templatesMutation.error, '페이즈 템플릿을 교체하지 못했습니다.')}
          </p>
        )}
        <Button type="submit" disabled={templatesMutation.isPending || isUploading}>
          {isUploading
            ? '이미지 업로드 중...'
            : templatesMutation.isPending
              ? '저장 중...'
              : '페이즈 템플릿 저장'}
        </Button>
      </form>
    </div>
  );
}

function NameAndDescriptionFields({
  name,
  description,
  onNameChange,
  onDescriptionChange,
}: {
  name: string;
  description: string;
  onNameChange: (value: string) => void;
  onDescriptionChange: (value: string) => void;
}) {
  return (
    <>
      <label className="block text-sm font-semibold text-stone-700">
        이름
        <input
          required
          maxLength={150}
          value={name}
          onChange={(event) => onNameChange(event.target.value)}
          className={adminInputClass}
        />
      </label>
      <label className="block text-sm font-semibold text-stone-700">
        설명
        <textarea
          required
          rows={3}
          value={description}
          onChange={(event) => onDescriptionChange(event.target.value)}
          className={`${adminInputClass} py-2`}
        />
      </label>
    </>
  );
}

function MoodFields({
  moods,
  onChange,
}: {
  moods: MoodInput[];
  onChange: (moods: MoodInput[]) => void;
}) {
  const update = (index: number, values: Partial<MoodInput>) =>
    onChange(moods.map((mood, i) => (i === index ? { ...mood, ...values } : mood)));

  return (
    <fieldset className="space-y-3">
      <legend className="text-sm font-semibold text-stone-700">분위기 및 약점</legend>
      {moods.map((mood, index) => (
        <div key={mood.id} className="grid grid-cols-[1fr_1fr_80px_auto] items-end gap-2">
          <label className="text-xs text-stone-600">
            종류
            <select
              value={mood.code}
              onChange={(event) => update(index, { code: event.target.value as MoodCode })}
              className={adminInputClass}
            >
              {moodCodes.map((code) => (
                <option key={code} value={code}>
                  {moodLabels[code]}
                </option>
              ))}
            </select>
          </label>
          <label className="text-xs text-stone-600">
            역할
            <select
              value={mood.role}
              onChange={(event) => update(index, { role: event.target.value as MoodRole })}
              className={adminInputClass}
            >
              <option value="THEME">주제</option>
              <option value="WEAKNESS">약점</option>
            </select>
          </label>
          <label className="text-xs text-stone-600">
            가중치
            <input
              type="number"
              required
              min="0.001"
              max="999.999"
              step="0.001"
              value={mood.weight}
              onChange={(event) => update(index, { weight: event.target.value })}
              className={adminInputClass}
            />
          </label>
          <Button
            type="button"
            variant="secondary"
            aria-label={`${index + 1}번째 분위기 삭제`}
            disabled={moods.length === 1}
            onClick={() => onChange(moods.filter((_, i) => i !== index))}
          >
            삭제
          </Button>
        </div>
      ))}
      <Button type="button" variant="secondary" onClick={() => onChange([...moods, createMood()])}>
        분위기 추가
      </Button>
    </fieldset>
  );
}

function validateMoods(
  moods: MoodInput[],
  showToast: (toast: { message: string; variant: 'error' }) => void,
) {
  const keys = moods.map((mood) => `${mood.code}:${mood.role}`);
  if (new Set(keys).size !== keys.length) {
    showToast({ message: '같은 분위기와 역할을 중복으로 추가할 수 없습니다.', variant: 'error' });
    return false;
  }
  return true;
}

function isConflict(error: unknown) {
  return (
    typeof error === 'object' &&
    error !== null &&
    'response' in error &&
    (error as { response?: { status?: number } }).response?.status === 409
  );
}
