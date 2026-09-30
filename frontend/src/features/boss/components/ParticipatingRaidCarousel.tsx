import { useState } from 'react';

import { Link } from 'react-router-dom';

import { FadeInImage } from '@/shared/components';

import { calculateBossRemainingHpPercent } from '../progress';
import type { MyBossRunResponse } from '../types';

interface ParticipatingRaidCarouselProps {
  bossRuns: Array<
    MyBossRunResponse & {
      currentPhaseImageUrl?: string | null;
      currentPhaseMaxHp?: number;
      currentPhaseRemainingHp?: number;
    }
  >;
}

// API가 현재 페이즈에 맞춰 반환한 이미지를 사용하고, 로드 실패 시 대체 이미지를 표시한다.
function resolveImageUrl(imageUrl?: string | null): string | null {
  if (!imageUrl) return null;
  if (!imageUrl.startsWith('/')) return imageUrl;

  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL;
  if (!apiBaseUrl || !URL.canParse(apiBaseUrl)) return imageUrl;

  return new URL(imageUrl, apiBaseUrl).toString();
}

function BossImage({ imageUrl, bossName }: { imageUrl?: string | null; bossName: string }) {
  const [failedImageUrl, setFailedImageUrl] = useState<string | null>(null);
  const resolvedImageUrl = resolveImageUrl(imageUrl);
  const hasError = !resolvedImageUrl || failedImageUrl === resolvedImageUrl;

  if (hasError) {
    return (
      <div
        aria-hidden="true"
        className="flex aspect-square w-full items-center justify-center rounded-lg bg-gradient-to-br from-violet-200 to-violet-300 text-2xl"
      >
        ⚔️
      </div>
    );
  }

  return (
    <FadeInImage
      src={resolvedImageUrl ?? undefined}
      alt={`${bossName} 보스 이미지`}
      className="aspect-square w-full rounded-lg object-cover"
      onError={() => setFailedImageUrl(resolvedImageUrl)}
    />
  );
}

/** 상세 화면과 동일한 기준으로 현재 페이즈의 남은 HP를 표시한다. */
function RaidHpBar({
  bossName,
  maxHp,
  remainingHp,
}: {
  bossName: string;
  maxHp?: number;
  remainingHp?: number;
}) {
  if (maxHp === undefined || remainingHp === undefined) {
    return null;
  }

  const remainingHpPercent = calculateBossRemainingHpPercent(maxHp, remainingHp);

  return (
    <div className="mt-1.5 px-0.5 pb-0.5">
      <p className="mb-1 text-center text-[10px] font-bold text-violet-800">
        HP {Math.round(remainingHpPercent)}%
      </p>
      <div
        role="progressbar"
        aria-label={`${bossName} 남은 HP`}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.round(remainingHpPercent)}
        aria-valuetext={`HP ${Math.round(remainingHpPercent)}%`}
        className="h-1 overflow-hidden rounded-full bg-white/70"
      >
        <div
          className="h-full rounded-full bg-violet-500"
          style={{ width: `${remainingHpPercent}%` }}
        />
      </div>
    </div>
  );
}

// 참여중인 원정을 한 줄 가로 스와이프로 보여준다. 카드 전체가 상세 이동 링크다.
export function ParticipatingRaidCarousel({ bossRuns }: ParticipatingRaidCarouselProps) {
  return (
    <ul
      aria-label="참여중인 원정 목록"
      className="-mx-4 flex snap-x snap-mandatory gap-3 overflow-x-auto px-4 pb-2 lg:mx-0 lg:grid lg:grid-cols-4 lg:gap-5 lg:overflow-visible lg:px-0"
    >
      {bossRuns.map((bossRun) => (
        <li key={bossRun.bossRunId} className="w-24 shrink-0 snap-start lg:w-auto">
          <Link
            to={`/boss/${bossRun.bossRunId}`}
            className="block h-full rounded-xl bg-violet-100 p-1.5 shadow-sm transition hover:bg-violet-200 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600 lg:rounded-2xl lg:p-2"
          >
            <BossImage imageUrl={bossRun.currentPhaseImageUrl} bossName={bossRun.bossName} />

            <h3 className="mt-1.5 overflow-hidden text-ellipsis whitespace-nowrap text-center text-xs font-bold text-stone-900">
              {bossRun.bossName}
            </h3>

            <p className="mt-0.5 text-center text-[10px] font-semibold text-violet-700">
              Phase {bossRun.currentPhaseNo}
            </p>

            <RaidHpBar
              bossName={bossRun.bossName}
              maxHp={bossRun.currentPhaseMaxHp}
              remainingHp={bossRun.currentPhaseRemainingHp}
            />
          </Link>
        </li>
      ))}
    </ul>
  );
}
