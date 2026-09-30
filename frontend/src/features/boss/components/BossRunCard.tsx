import { AuthRequiredLink } from '@/features/auth';
import { Badge } from '@/shared/components';
import { calculateBossRemainingHpPercent } from '../progress';
import type { BossRunSummaryResponse } from '../types';

import { BossImage } from './BossImage';

interface BossRunCardProps {
  bossRun: BossRunSummaryResponse;
  /** 내가 이 회차에 문장을 제출한 이력이 있는지 */
  isParticipating?: boolean;
}

export function BossRunCard({ bossRun, isParticipating = false }: BossRunCardProps) {
  const { bossRunId, bossName, currentPhase, moods } = bossRun;
  const remainingHpPercent = calculateBossRemainingHpPercent(
    currentPhase.maxHp,
    currentPhase.remainingHp,
  );
  const themeMoods = moods.filter((mood) => mood.role === 'THEME');

  return (
    <AuthRequiredLink
      to={`/boss/${bossRunId}`}
      aria-label={`${bossName} 상세 보기`}
      className="block rounded-2xl bg-white p-4 shadow-sm transition hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600"
    >
      <article>
        {/* 회차 상태는 목록 상단 탭과 하위 필터로 구분하므로 카드에는 뱃지를 두지 않고,
            참여 여부만 오른쪽에 표시한다.
            뱃지가 없는 카드도 같은 줄 높이를 유지해 그리드에서 이미지 위치를 맞춘다. */}
        <div className="flex min-h-6 items-start justify-end">
          {isParticipating && (
            <Badge tone="brand" className="shrink-0">
              참여중
            </Badge>
          )}
        </div>

        <div className="mt-3 flex justify-center">
          <BossImage imageUrl={currentPhase.imageUrl} alt="" sizeClassName="size-24" />
        </div>

        <h2 className="mt-3 text-lg font-extrabold text-stone-900">{bossName}</h2>

        {themeMoods.length > 0 && (
          <ul aria-label="원정 분위기" className="mt-2 flex flex-wrap gap-1.5">
            {themeMoods.map((mood) => (
              <li
                key={mood.code}
                className="max-w-full break-keep rounded bg-stone-100 px-2 py-0.5 text-xs text-stone-600"
              >
                {mood.displayName}
              </li>
            ))}
          </ul>
        )}

        <div
          role="progressbar"
          aria-label={`${bossName} 남은 HP`}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={Math.round(remainingHpPercent)}
          aria-valuetext={`HP ${Math.round(remainingHpPercent)}%`}
          className="mt-4 h-2 overflow-hidden rounded-full bg-stone-200"
        >
          <div
            className="h-full rounded-full bg-violet-600 transition-[width]"
            style={{ width: `${remainingHpPercent}%` }}
          />
        </div>

        <p className="mt-2 text-xs font-semibold text-stone-600">
          HP {Math.round(remainingHpPercent)}%
        </p>
      </article>
    </AuthRequiredLink>
  );
}
