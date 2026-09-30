import { useState } from 'react';

import { useBossRunLeaderboardQuery } from '../queries';

import type { BossRunLeaderboardEntry, BossRunLeaderboardMyRank } from '../types';

/** SSE가 끊겨 폴링으로 강등됐을 때의 갱신 주기. 순위는 HP보다 덜 급해 조금 느리게 잡는다. */
const POLLING_FALLBACK_INTERVAL_MS = 5_000;
/** 행 이동 애니메이션을 위해 각 행을 절대 위치로 두므로 높이가 고정이어야 한다. */
const ROW_HEIGHT_PX = 52;

interface BossLeaderboardProps {
  bossRunId: number;
  /** 로그인 사용자의 id. 목록에서 내 행을 강조하는 데 쓴다. 비로그인이면 null. */
  myUserId: number | null;
  /**
   * 사용자 조회가 아직 진행 중인지. 판별 전에는 myUserId가 null이라 "목록 밖"으로 오인해
   * 고정 영역이 잠깐 떴다 사라지는 깜빡임이 생기므로, true면 내 순위 표시를 보류한다.
   */
  isMyUserPending: boolean;
  /** false면 SSE가 끊긴 상태다. 쿼리 주기 갱신으로 강등한다. */
  realtime: boolean;
  /** 접이식 영역의 summary가 제목을 대신할 때 화면 제목만 감춘다. */
  headingHidden?: boolean;
  /** compact는 전투 화면 옆에서 상위 5명(모바일 3명)과 내 순위만 짧게 보여 준다. */
  variant?: 'full' | 'compact';
}

/**
 * 회차 기여도 순위. 실시간이면 SSE leaderboard 스냅숏이 쿼리 캐시를 대체해 자동으로
 * 리렌더되고, 폴링 강등 시에만 주기 재조회를 켠다.
 *
 * 이 섹션의 로딩·오류는 섹션 안에서 처리한다. 순위를 못 불러와도 HP·제출 화면은
 * 정상 동작해야 하기 때문이다.
 */
export function BossLeaderboard({
  bossRunId,
  myUserId,
  isMyUserPending,
  realtime,
  headingHidden = false,
  variant = 'full',
}: BossLeaderboardProps) {
  const [isCompactExpanded, setIsCompactExpanded] = useState(true);
  const leaderboardQuery = useBossRunLeaderboardQuery(bossRunId, {
    refetchInterval: realtime ? false : POLLING_FALLBACK_INTERVAL_MS,
  });

  const titleId = `boss-leaderboard-${variant}-title`;

  return (
    <section aria-labelledby={titleId} className={variant === 'compact' ? 'mt-3 lg:mt-0' : 'mt-6'}>
      {variant === 'full' && (
        <h2
          id={titleId}
          className={headingHidden ? 'sr-only' : 'text-sm font-semibold text-stone-700'}
        >
          기여도 순위
        </h2>
      )}

      <div
        className={
          variant === 'compact'
            ? 'rounded-2xl border border-violet-200 bg-white p-3 shadow-sm lg:p-5'
            : 'mt-2 rounded-2xl border border-stone-200 bg-white p-3'
        }
      >
        {variant === 'compact' && (
          <div
            className={[
              'flex items-center justify-between gap-3',
              isCompactExpanded ? 'mb-2 border-b border-stone-100 pb-2 lg:mb-3 lg:pb-3' : '',
            ].join(' ')}
          >
            <h2 id={titleId} className="text-sm font-extrabold text-stone-800 lg:text-xl">
              실시간 기여도
            </h2>
            <div className="flex items-center gap-2">
              <span
                aria-label={realtime ? '실시간 순위 갱신 중' : '순위 자동 갱신 중'}
                className={[
                  'flex items-center gap-1.5 text-[11px] font-bold lg:text-sm',
                  realtime ? 'text-emerald-600' : 'text-amber-600',
                ].join(' ')}
              >
                <span
                  aria-hidden="true"
                  className={[
                    'size-2 rounded-full lg:size-3',
                    realtime ? 'animate-pulse bg-emerald-500' : 'bg-amber-500',
                  ].join(' ')}
                />
                {realtime ? 'LIVE' : 'AUTO'}
              </span>
              <button
                type="button"
                aria-expanded={isCompactExpanded}
                aria-controls={`${titleId}-content`}
                onClick={() => setIsCompactExpanded((expanded) => !expanded)}
                className="inline-flex min-h-9 items-center gap-1 rounded-lg px-2 text-xs font-bold text-stone-600 transition hover:bg-stone-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600 lg:min-h-10 lg:px-3 lg:text-sm"
              >
                {isCompactExpanded ? '접기' : '펼치기'}
                <span
                  aria-hidden="true"
                  className={[
                    'text-stone-400 transition-transform',
                    isCompactExpanded ? 'rotate-180' : '',
                  ].join(' ')}
                >
                  ↓
                </span>
              </button>
            </div>
          </div>
        )}

        {(variant !== 'compact' || isCompactExpanded) && (
          <div id={variant === 'compact' ? `${titleId}-content` : undefined}>
            <LeaderboardBody
              // isPending은 disabled 쿼리에서도 true로 남아 로딩이 영원히 끝나지 않는다.
              // 실제 요청이 진행 중일 때만 참인 isLoading으로 분기한다.
              isLoading={leaderboardQuery.isLoading}
              isError={leaderboardQuery.isError}
              onRetry={() => void leaderboardQuery.refetch()}
              entries={leaderboardQuery.data?.entries ?? []}
              me={leaderboardQuery.data?.me ?? null}
              myUserId={myUserId}
              isMyUserPending={isMyUserPending}
              variant={variant}
            />
          </div>
        )}
      </div>
    </section>
  );
}

interface LeaderboardBodyProps {
  isLoading: boolean;
  isError: boolean;
  onRetry: () => void;
  entries: BossRunLeaderboardEntry[];
  me: BossRunLeaderboardMyRank | null;
  myUserId: number | null;
  isMyUserPending: boolean;
  variant: 'full' | 'compact';
}

function LeaderboardBody({
  isLoading,
  isError,
  onRetry,
  entries,
  me,
  myUserId,
  isMyUserPending,
  variant,
}: LeaderboardBodyProps) {
  if (isLoading) {
    return (
      <p role="status" className="py-3 text-center text-sm text-stone-500">
        기여도 순위를 불러오는 중입니다.
      </p>
    );
  }

  if (isError) {
    return (
      <div className="py-3 text-center">
        <p className="text-sm text-stone-600">기여도 순위를 불러오지 못했습니다.</p>
        <button
          type="button"
          onClick={onRetry}
          className="mt-2 min-h-11 rounded-xl border border-stone-200 bg-white px-4 py-2 text-sm font-semibold text-stone-700 transition hover:bg-stone-50"
        >
          다시 시도
        </button>
      </div>
    );
  }

  if (entries.length === 0) {
    return (
      <p className="py-3 text-center text-sm text-stone-500">
        아직 제출한 참가자가 없어요. 첫 번째 공격의 주인공이 되어 보세요!
      </p>
    );
  }

  const myEntry =
    myUserId === null ? undefined : entries.find((entry) => entry.userId === myUserId);
  // 내 최신 값은 목록에 있으면 목록이, 밖이면 마지막 조회의 me가 말해 준다.
  // 사용자 판별 전에는 목록 안/밖을 결정할 수 없으므로 고정 영역을 보류한다 — 판별이 끝난 뒤
  // 목록의 내 행 강조와 고정 영역이 중복으로 떴다 사라지는 깜빡임을 막는다.
  const myRank = myEntry
    ? { rank: myEntry.rank, totalDamage: myEntry.totalDamage }
    : isMyUserPending
      ? null
      : me;

  if (variant === 'compact') {
    return <CompactLeaderboardBody entries={entries} myRank={myRank} myUserId={myUserId} />;
  }

  return (
    <>
      {/* 각 행을 순위 위치로 translate하고 transform에 전환을 걸어, 순위가 바뀌면 행이
          미끄러져 이동하는 것이 보인다. React가 userId 키로 DOM을 재사용해야 성립한다. */}
      <ol
        aria-label="기여도 순위 목록"
        className="relative"
        style={{ height: entries.length * ROW_HEIGHT_PX }}
      >
        {entries.map((entry, index) => {
          const isMine = myUserId !== null && entry.userId === myUserId;
          return (
            <li
              key={entry.userId}
              className={[
                'absolute inset-x-0 flex items-center gap-3 rounded-xl px-3 transition-transform duration-500 ease-out',
                isMine ? 'bg-violet-50' : '',
              ].join(' ')}
              style={{
                height: ROW_HEIGHT_PX,
                transform: `translateY(${index * ROW_HEIGHT_PX}px)`,
              }}
            >
              <span
                aria-hidden="true"
                className={[
                  'w-7 shrink-0 text-center text-sm font-extrabold',
                  entry.rank <= 3 ? 'text-violet-700' : 'text-stone-400',
                ].join(' ')}
              >
                {entry.rank}
              </span>
              {/* 시각 요소는 전부 낭독에서 감추고 sr-only 문장 하나만 남긴다. 둘 다 남기면
                  같은 내용이 두 번 낭독된다. */}
              <span
                aria-hidden="true"
                className={[
                  'min-w-0 flex-1 truncate text-sm',
                  isMine ? 'font-bold text-violet-700' : 'font-semibold text-stone-800',
                ].join(' ')}
              >
                {entry.nickname}
                {isMine && <span className="ml-1 text-xs font-semibold text-violet-500">나</span>}
              </span>
              <span aria-hidden="true" className="shrink-0 text-sm font-bold text-stone-700">
                {entry.totalDamage.toLocaleString()}
              </span>
              <span className="sr-only">
                {entry.rank}위 {entry.nickname}
                {isMine ? ' 나' : ''} 누적 데미지 {entry.totalDamage.toLocaleString()}
              </span>
            </li>
          );
        })}
      </ol>

      {/* 상위 목록 밖이어도 내 순위는 항상 보여야 한다. 목록에 있으면 강조로 대신한다. */}
      {myRank !== null && !myEntry && (
        <p
          role="status"
          className="mt-2 flex items-center justify-between rounded-xl bg-violet-50 px-3 py-2.5 text-sm font-semibold text-violet-700"
        >
          <span>내 순위 {myRank.rank}위</span>
          <span>누적 데미지 {myRank.totalDamage.toLocaleString()}</span>
        </p>
      )}
    </>
  );
}

interface CompactLeaderboardBodyProps {
  entries: BossRunLeaderboardEntry[];
  myRank: BossRunLeaderboardMyRank | null;
  myUserId: number | null;
}

function CompactLeaderboardBody({ entries, myRank, myUserId }: CompactLeaderboardBodyProps) {
  const topEntries = entries.slice(0, 5);
  const myTopIndex = topEntries.findIndex((entry) => entry.userId === myUserId);
  const showMyRankOnMobile = myTopIndex >= 3;
  const showMyRankEverywhere = myTopIndex === -1 && myRank !== null;

  return (
    <>
      <ol aria-label="실시간 기여도 미리보기" className="space-y-0.5 lg:space-y-1">
        {topEntries.map((entry, index) => {
          const isMine = myUserId !== null && entry.userId === myUserId;

          return (
            <li
              key={entry.userId}
              className={[
                'min-h-8 items-center gap-2 rounded-lg px-2 text-xs lg:min-h-10 lg:gap-3 lg:px-3 lg:text-base',
                index >= 3 ? 'hidden lg:flex' : 'flex',
                isMine ? 'bg-violet-50 text-violet-700' : 'text-stone-700',
              ].join(' ')}
            >
              <span
                aria-hidden="true"
                className={[
                  'w-5 shrink-0 text-center font-extrabold lg:w-7',
                  entry.rank <= 3 ? 'text-violet-700' : 'text-stone-400',
                ].join(' ')}
              >
                {entry.rank}
              </span>
              <span
                aria-hidden="true"
                className={[
                  'min-w-0 flex-1 truncate',
                  isMine ? 'font-extrabold' : 'font-semibold',
                ].join(' ')}
              >
                {entry.nickname}
                {isMine && <span className="ml-1 text-[10px] text-violet-500 lg:text-xs">나</span>}
              </span>
              <span aria-hidden="true" className="shrink-0 font-bold">
                {entry.totalDamage.toLocaleString()}
              </span>
              <span className="sr-only">
                {entry.rank}위 {entry.nickname}
                {isMine ? ' 나' : ''} 누적 데미지 {entry.totalDamage.toLocaleString()}
              </span>
            </li>
          );
        })}
      </ol>

      {myRank !== null && (showMyRankEverywhere || showMyRankOnMobile) && (
        <p
          role="status"
          className={[
            'mt-2 items-center justify-between rounded-lg bg-violet-100 px-2.5 py-2 text-xs font-extrabold text-violet-700 lg:px-3 lg:py-3 lg:text-base',
            showMyRankEverywhere ? 'flex' : 'flex lg:hidden',
          ].join(' ')}
        >
          <span>내 순위 {myRank.rank}위</span>
          <span>{myRank.totalDamage.toLocaleString()} 피해</span>
        </p>
      )}
    </>
  );
}
