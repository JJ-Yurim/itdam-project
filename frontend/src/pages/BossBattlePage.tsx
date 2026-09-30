import { useEffect, useRef, useState } from 'react';

import { useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';

import { useCurrentUserQuery } from '@/features/auth';
import {
  BossHitStage,
  BossHpLossTrail,
  BossImage,
  BossLeaderboard,
  BossPhaseClearBanner,
  EligibleSentenceList,
  BossSubmissionHistory,
  SentenceMoodTags,
  bossQueryKeys,
  getBossAttackIntensity,
  getBossRunStatusPolicy,
  useBossHpLoss,
  useBossPhaseAdvance,
  useLatestBossHit,
  useBossRunQuery,
  useBossRunStream,
  useEligibleSentencesQuery,
  useBossSubmissionMutation,
} from '@/features/boss';
import type { BossRunHit } from '@/features/boss';
import { getApiError } from '@/shared/api';
import {
  Badge,
  Button,
  ButtonLink,
  EmptyState,
  ErrorState,
  LoadingState,
  Pagination,
  Skeleton,
  SkeletonSection,
} from '@/shared/components';

const PAGE_SIZE = 10;
const POLLING_FALLBACK_INTERVAL_MS = 3_000;

type BattleTab = 'sentences' | 'history';

const battleTabs: { value: BattleTab; label: string }[] = [
  { value: 'sentences', label: '제출 전 문장' },
  { value: 'history', label: '내 제출 이력' },
];

export function BossBattlePage() {
  const { bossRunId: bossRunIdParam } = useParams();
  const bossRunId = Number(bossRunIdParam);

  if (!Number.isInteger(bossRunId) || bossRunId <= 0) {
    return (
      <ErrorState title="잘못된 원정 주소입니다" description="원정 목록에서 다시 선택해 주세요." />
    );
  }

  return <BossBattleContent bossRunId={bossRunId} />;
}

interface BossBattleContentProps {
  bossRunId: number;
}

function BossBattleContent({ bossRunId }: BossBattleContentProps) {
  const [page, setPage] = useState(0);
  const [selectedSentenceVersionId, setSelectedSentenceVersionId] = useState<number | null>(null);
  // 좁은 화면에 목록 둘을 함께 쌓으면 어느 쪽도 몇 건 못 보여 주므로 탭으로 나눈다.
  const [tab, setTab] = useState<BattleTab>('sentences');
  // 실시간이 끊긴 상태에서 내가 넣은 타격. 제출 성공 시점에만 채운다.
  const [ownHit, setOwnHit] = useState<BossRunHit | null>(null);
  const ownHitCountRef = useRef(0);
  const bossStageRef = useRef<HTMLElement | null>(null);

  const queryClient = useQueryClient();
  // 다른 참가자의 공격을 새로고침 없이 반영한다. 연결이 반복 실패하면 훅이 폴링 강등을
  // 알리고, 그때만 상세 조회에 주기 갱신을 켠다.
  const stream = useBossRunStream(bossRunId, true);
  const bossRunQuery = useBossRunQuery(bossRunId, {
    refetchInterval: stream.isRealtime ? false : POLLING_FALLBACK_INTERVAL_MS,
  });
  const submissionMutation = useBossSubmissionMutation(bossRunId);
  // 리더보드에서 내 행을 강조할 기준. 로그인 정보를 아직 모르면 강조 없이 그린다.
  const currentUserQuery = useCurrentUserQuery();
  const myUserId = currentUserQuery.data?.id ?? null;
  const resetSubmission = submissionMutation.reset;
  const bossRunStatus = bossRunQuery.data?.status;
  // 제출 가능 여부는 상태 정책을 단일 출처로 사용한다.
  const canSubmit = bossRunStatus !== undefined && getBossRunStatusPolicy(bossRunStatus).canSubmit;
  // 오늘 제출 횟수를 다 쓴 사람도 이 화면에서 진행 상황은 계속 볼 수 있어야 한다.
  // 제출 UI만 잠그고 나머지(HP·순위·이력)는 그대로 둔다.
  // 값이 아직 없으면(로딩 중이거나 필드가 빠진 비정상 응답) 제출을 막지 않는다(fail-open).
  // 잘못 열려도 제출 시점에 서버가 최종 판정하지만, 잘못 잠기면 화면만으로 제출이 불가능해진다.
  const remainingSubmissionCount = bossRunQuery.data?.remainingSubmissionCount;
  const canSubmitNow =
    canSubmit && (remainingSubmissionCount === undefined || remainingSubmissionCount > 0);
  // 제출 불가 회차는 제출 가능 문장 조회가 409로 거절되므로 요청하지 않는다.
  const eligibleSentencesQuery = useEligibleSentencesQuery(
    bossRunId,
    {
      page,
      size: PAGE_SIZE,
    },
    { enabled: canSubmitNow },
  );
  const totalPages = eligibleSentencesQuery.data?.totalPages;

  // 전투 연출(HP 잔상·페이즈 돌파)은 훅이므로 조기 반환보다 앞에서 값을 읽는다.
  // 두 훅 모두 서버 상태의 변화를 기준으로 하므로 실시간·주기 갱신 모두에서 동작한다.
  const currentPhase = bossRunQuery.data?.currentPhase;
  // 페이즈가 없거나 최대 HP를 모르면 비교 대상이 없다는 뜻으로 null을 넘긴다.
  // 0으로 넘기면 회차가 끝나는 순간 바 전체가 잃은 것처럼 잔상이 남는다.
  const hpPercent =
    currentPhase && currentPhase.maxHp > 0
      ? Math.max(0, Math.min(100, (currentPhase.remainingHp / currentPhase.maxHp) * 100))
      : null;
  const remainingHpPercent = hpPercent ?? 0;
  const hpLoss = useBossHpLoss(hpPercent);
  const phaseAdvance = useBossPhaseAdvance(currentPhase?.phaseNo);
  // 내 제출은 실시간이 끊겼을 때만 직접 연출한다(아래 handleSubmit에서 결정).
  // 두 출처를 합칠 때 이미 재생한 타격이 되살아나지 않도록 훅이 출처별로 추적한다.
  const battleHit = useLatestBossHit(stream.lastHit, ownHit);
  const attackIntensity = battleHit
    ? getBossAttackIntensity(
        battleHit.baseScore,
        currentPhase?.hardMinScore ?? 0,
        currentPhase?.recommendedScore ?? Number.POSITIVE_INFINITY,
      )
    : 'normal';

  // 전투 중 회차 상태가 제출 불가로 바뀌면 목록·요약 캐시가 이전 상태를
  // 보여주지 않도록 회차 목록 Query를 갱신한다.
  useEffect(() => {
    if (bossRunStatus !== undefined && !canSubmit) {
      void queryClient.invalidateQueries({ queryKey: bossQueryKeys.lists() });
    }
  }, [bossRunStatus, canSubmit, queryClient]);

  useEffect(() => {
    if (totalPages !== undefined && page > 0 && page >= totalPages) {
      const timeoutId = window.setTimeout(() => {
        setPage(Math.max(0, totalPages - 1));
        setSelectedSentenceVersionId(null);
      }, 0);

      return () => window.clearTimeout(timeoutId);
    }
  }, [page, totalPages]);

  if (bossRunQuery.isPending) {
    // 히어로 패널(이미지·이름·HP)과 요약 카드의 골격을 미리 그려 레이아웃 점프를 막는다.
    return (
      <SkeletonSection label="원정 정보를 불러오는 중입니다">
        <div className="mt-12 lg:mt-14 lg:flex lg:items-center lg:gap-8 lg:rounded-panel lg:border lg:border-stone-200 lg:bg-white lg:p-6">
          <Skeleton className="mx-auto size-40 rounded-full lg:mx-0 lg:size-36" />
          <div className="mt-4 lg:mt-0 lg:flex-1">
            <Skeleton className="mx-auto h-7 w-40 lg:mx-0" />
            <Skeleton className="mt-5 h-3 w-full rounded-full" />
            <Skeleton className="mt-3 ml-auto h-3 w-24" />
          </div>
        </div>
        <div className="mt-4 grid grid-cols-3 gap-3">
          {Array.from({ length: 3 }, (_, index) => (
            <Skeleton key={index} className="h-16" />
          ))}
        </div>
      </SkeletonSection>
    );
  }

  if (bossRunQuery.isError) {
    return (
      <ErrorState
        title="원정 정보를 불러오지 못했습니다"
        description="보스전 상태가 변경됐거나 요청에 실패했습니다."
        onRetry={() => void bossRunQuery.refetch()}
      />
    );
  }

  const bossRun = bossRunQuery.data;
  const statusPolicy = getBossRunStatusPolicy(bossRun.status);
  // 제출 가능 문장 목록은 제출할 수 있는 사람에게만 필요하다. 조회가 준비되는 동안에도
  // 원정 정보 자체는 보여줄 수 있지만, 화면이 두 번에 나눠 뜨는 것보다 한 번에 뜨는 쪽이
  // 안정적이라 기존처럼 함께 기다린다.
  if (canSubmitNow && eligibleSentencesQuery.isPending) {
    return (
      <LoadingState
        title="원정 정보를 불러오는 중입니다"
        description="보스 상태와 제출 가능한 문장을 확인하고 있습니다."
      />
    );
  }

  if (canSubmitNow && eligibleSentencesQuery.isError) {
    return (
      <ErrorState
        title="원정 정보를 불러오지 못했습니다"
        description="보스전 상태가 변경됐거나 요청에 실패했습니다."
        onRetry={() => void eligibleSentencesQuery.refetch()}
      />
    );
  }

  // 시작 전·종료된 회차는 현재 페이즈가 없을 수 있다. 페이즈 의존 블록은 전부 이 값으로 가드한다.
  const phase = bossRun.currentPhase ?? null;
  const sentencePage = canSubmitNow ? eligibleSentencesQuery.data : undefined;
  const selectedSentence = sentencePage?.content.find(
    (sentence) => sentence.sentenceVersionId === selectedSentenceVersionId,
  );
  const selectedSentenceVersionIdForSubmission =
    selectedSentence !== undefined &&
    (phase?.hardMinScore == null || selectedSentence.baseScore >= phase.hardMinScore)
      ? selectedSentence.sentenceVersionId
      : null;

  const handleSelectionChange = (sentenceVersionId: number | null) => {
    setSelectedSentenceVersionId(sentenceVersionId);
    resetSubmission();
  };

  const handlePageChange = (nextPage: number) => {
    setPage(nextPage);
    setSelectedSentenceVersionId(null);
    resetSubmission();
  };

  const handleSubmit = () => {
    if (selectedSentenceVersionIdForSubmission === null) {
      return;
    }

    // 부드러운 스크롤은 완료 전에 응답과 타격 이벤트가 도착할 수 있다. 요청 전에
    // 보스와 HP를 즉시 화면에 고정해 충전부터 피격까지 연출을 놓치지 않게 한다.
    if (typeof bossStageRef.current?.scrollIntoView === 'function') {
      bossStageRef.current.scrollIntoView({ behavior: 'auto', block: 'start' });
    }

    submissionMutation.mutate(
      {
        sentenceVersionId: selectedSentenceVersionIdForSubmission,
        // 백엔드는 이 값이 false면 제출을 거부한다. 제출 시점의 별도 동의 절차는
        // 회원가입의 저작권 이용 동의로 대체됐으므로 항상 동의로 보낸다.
        submissionConsentAccepted: true,
      },
      {
        onSuccess: (result) => {
          setSelectedSentenceVersionId(null);

          // 실시간이 살아 있으면 같은 타격이 곧 SSE로 되돌아온다. 그때는 그 이벤트만
          // 연출해 한 번의 공격이 두 번 재생되지 않게 한다.
          if (!stream.isRealtime) {
            ownHitCountRef.current += 1;
            setOwnHit({
              damage: result.damage.finalDamage,
              baseScore: result.damage.aiBaseScore,
              key: ownHitCountRef.current,
            });
          }
        },
      },
    );
  };

  const submissionApiError = submissionMutation.isError
    ? getApiError(submissionMutation.error)
    : null;
  const submissionErrorMessage = submissionApiError
    ? getSubmissionErrorMessage(submissionApiError.code)
    : null;

  return (
    <div className={sentencePage !== undefined && tab === 'sentences' ? 'pb-28 lg:pb-0' : ''}>
      <BackToBossListLink />

      {/* 웹에서는 보스 옆 빈 공간에 실시간 순위를 두고, 모바일에서는 보스 바로 아래에
          상위 3명과 내 순위만 짧게 보여 전투 흐름을 끊지 않는다. */}
      <div className="lg:grid lg:grid-cols-[minmax(0,1fr)_320px] lg:items-start lg:gap-4">
        <section
          ref={bossStageRef}
          id="boss-battle-stage"
          aria-label="보스 정보"
          className="relative mt-2 scroll-mt-24 rounded-3xl border border-violet-200 bg-white p-4 shadow-sm lg:mt-4 lg:flex lg:min-h-[304px] lg:items-center lg:gap-10 lg:p-8"
        >
          <div className="relative mt-2 flex justify-center lg:mt-0 lg:shrink-0">
            {/* 페이즈를 넘긴 순간을 보스 위에 알린다. 클릭을 통과시키고 스스로 사라진다. */}
            <BossPhaseClearBanner advance={phaseAdvance} />

            {/* 타격이 오면 보스가 반동하고 충격 링과 데미지 수치가 함께 뜬다. */}
            <BossHitStage
              hit={battleHit}
              intensity={attackIntensity}
              charging={submissionMutation.isPending}
            >
              <BossImage
                imageUrl={phase?.imageUrl ?? null}
                alt=""
                sizeClassName="size-32 lg:size-64"
              />
            </BossHitStage>
          </div>

          <div className="mt-4 min-w-0 lg:mt-0 lg:flex-1">
            {/* 예정·진행 중은 아래 안내로 드러나므로 결과 구분이 필요한 종료 회차에만 뱃지를 단다. */}
            {statusPolicy.isEnded && (
              <p className="mb-2 text-center lg:text-left">
                <span
                  className={[
                    'inline-block rounded-full px-3 py-1 text-xs font-semibold',
                    statusPolicy.badgeClassName,
                  ].join(' ')}
                >
                  {statusPolicy.label}
                </span>
              </p>
            )}

            <h1 className="text-center text-2xl font-extrabold text-stone-900 lg:text-left lg:text-3xl">
              {bossRun.bossName}
            </h1>

            {/* 계산식은 숨기되 어떤 분위기가 보너스와 약점에 대응하는지는 구분한다. */}
            {bossRun.moods && bossRun.moods.length > 0 && (
              <dl
                aria-label="원정 분위기와 약점"
                className="mt-3 flex flex-wrap justify-center gap-1.5 lg:justify-start lg:text-base"
              >
                {bossRun.moods.map((mood) => (
                  <div
                    key={`${mood.role}-${mood.code}`}
                    className={[
                      'rounded-full border bg-white px-3 py-2 text-xs shadow-sm lg:px-4 lg:py-3 lg:text-base',
                      mood.role === 'WEAKNESS' ? 'border-amber-200' : 'border-violet-200',
                    ].join(' ')}
                  >
                    <dt className="inline text-stone-500">
                      {mood.role === 'WEAKNESS' ? '약점' : '분위기'}{' '}
                    </dt>
                    <dd
                      className={[
                        'inline font-extrabold',
                        mood.role === 'WEAKNESS' ? 'text-amber-600' : 'text-violet-700',
                      ].join(' ')}
                    >
                      {mood.displayName}
                    </dd>
                  </div>
                ))}
              </dl>
            )}

            {/* 현재 페이즈가 없는 회차(시작 전 등)는 HP 표시 자체를 생략한다. */}
            {phase && (
              <>
                <div className="mt-5 flex items-center justify-between gap-3 lg:mt-4">
                  <div className="flex items-center gap-2">
                    <h2 className="text-sm font-semibold text-stone-700 lg:text-base">
                      보스 HP
                      <span
                        data-numeric
                        className="ml-2 text-xs font-semibold text-brand-strong lg:text-sm"
                      >
                        Phase {phase.phaseNo}
                      </span>
                    </h2>
                    {/* 실시간이 끊겨 폴링으로 강등되면 화면이 늦게 갱신될 수 있음을 드러낸다. */}
                    <span role="status">
                      <Badge tone={stream.isRealtime ? 'success' : 'warning'} size="sm">
                        {stream.isRealtime ? '실시간' : '주기 갱신'}
                      </Badge>
                    </span>
                  </div>
                  <span data-numeric className="text-sm font-bold text-brand-strong lg:text-base">
                    HP {remainingHpPercent.toFixed(0)}%
                  </span>
                </div>

                <div
                  role="progressbar"
                  aria-label="보스 HP"
                  aria-valuemin={0}
                  aria-valuemax={100}
                  aria-valuenow={Math.round(remainingHpPercent)}
                  aria-valuetext={`HP ${remainingHpPercent.toFixed(0)}%`}
                  className="relative mt-2 h-3 overflow-hidden rounded-full bg-stone-200"
                >
                  <div
                    className="h-full rounded-full bg-gradient-to-r from-violet-500 to-blue-400 transition-[width] duration-700 ease-out motion-reduce:transition-none"
                    style={{ width: `${remainingHpPercent}%` }}
                  />
                  {/* 방금 깎인 구간을 잔상으로 남겨 피해량을 눈으로 읽게 한다. */}
                  <BossHpLossTrail loss={hpLoss} />
                </div>

                <p data-numeric className="mt-2 text-right text-xs text-stone-500 lg:text-sm">
                  {phase.remainingHp.toLocaleString()} / {phase.maxHp.toLocaleString()}
                </p>
              </>
            )}

            {/* 혼자 싸우는 게 아니라는 감각: 참가자 전체가 함께 깎은 피해와
              방금 도착한 타격(내 것 포함)을 함께 보여준다. */}
            <div className="mt-3 flex items-center justify-between gap-3 border-t border-stone-100 pt-3 text-xs lg:text-base">
              <span className="text-stone-600">
                함께 입힌 피해{' '}
                <strong data-numeric className="font-bold text-stone-900">
                  {(bossRun.accumulatedDamage ?? 0).toLocaleString()}
                </strong>
              </span>
              <LatestHitChip hit={battleHit} />
            </div>
          </div>
        </section>

        <div className="lg:mt-4">
          <BossLeaderboard
            bossRunId={bossRunId}
            myUserId={myUserId}
            isMyUserPending={currentUserQuery.isPending}
            realtime={stream.isRealtime}
            variant="compact"
          />
        </div>
      </div>

      <dl className="mt-3 flex flex-wrap items-center gap-2 text-xs lg:text-base">
        <div className="rounded-full bg-white px-3 py-2 shadow-sm lg:px-4 lg:py-3">
          <dt className="inline text-stone-500">남은 시간 </dt>
          <dd data-numeric className="inline font-bold text-stone-800">
            {statusPolicy.canSubmit ? formatRemainingTime(bossRun.endsAt) : '-'}
          </dd>
        </div>
        <div className="rounded-full bg-white px-3 py-2 shadow-sm lg:px-4 lg:py-3">
          <dt className="inline text-stone-500">남은 제출 </dt>
          <dd data-numeric className="inline font-bold text-violet-700">
            {bossRun.remainingSubmissionCount ?? 0}회
          </dd>
        </div>
      </dl>

      {/* 종료된 회차: 제출 UI 대신 사유와 다음 행동을 안내한다. */}
      {statusPolicy.blockedMessage && (
        <div
          role="status"
          className="mt-6 rounded-control bg-stone-100 px-4 py-3 text-center text-sm text-stone-600"
        >
          {statusPolicy.blockedMessage}
        </div>
      )}

      {bossRun.status === 'CLEARED' && (
        <ButtonLink
          to={`/boss/${bossRunId}/clear`}
          variant="success"
          size="lg"
          className="mt-3 w-full"
        >
          클리어 결과 보기
        </ButtonLink>
      )}

      {/* 오늘 제출 기회를 다 쓴 사람도 진행 상황은 계속 보게 하고, 제출 영역만 안내로 바꾼다. */}
      {statusPolicy.canSubmit && !canSubmitNow && (
        <div
          role="status"
          className="mt-6 rounded-control bg-brand-soft px-4 py-3 text-center text-sm leading-6 text-brand-deep"
        >
          오늘 제출할 수 있는 횟수를 모두 사용했어요.
          <br />
          원정 진행 상황과 순위는 이 화면에서 계속 확인할 수 있어요.
        </div>
      )}

      {/* 제출 UI(탭·제출 레일)와 단독 이력은 배타적이다. sentencePage는 제출 가능할 때만
          채워지므로, 이 값 하나로 두 분기가 갈린다. */}
      {sentencePage === undefined ? (
        bossRun.mySubmissions.length > 0 && (
          <BossSubmissionHistory submissions={bossRun.mySubmissions} />
        )
      ) : (
        <>
          <div
            role="tablist"
            aria-label="전투 화면 목록 구분"
            className="mt-6 grid grid-cols-2 gap-1 rounded-2xl border border-stone-200 bg-white p-1 text-center text-sm font-semibold lg:text-base"
          >
            {battleTabs.map(({ value, label }) => (
              <button
                key={value}
                type="button"
                role="tab"
                id={`battle-tab-${value}`}
                aria-selected={tab === value}
                aria-controls="battle-tabpanel"
                tabIndex={tab === value ? 0 : -1}
                onClick={() => setTab(value)}
                className={[
                  'min-h-11 rounded-xl px-2 transition',
                  tab === value
                    ? 'bg-violet-100 text-violet-700'
                    : 'text-stone-500 hover:bg-stone-50',
                ].join(' ')}
              >
                {label}
              </button>
            ))}
          </div>

          {/* 제출 영역이 없는 이력 탭에서는 그리드를 걸지 않는다. 고정 320px 트랙은 콘텐츠와
              무관하게 폭을 유지하므로, 그대로 두면 오른쪽이 빈 채 본문만 좁아진다. */}
          <div
            className={
              tab === 'sentences'
                ? 'lg:grid lg:grid-cols-[minmax(0,1fr)_320px] lg:items-start lg:gap-6'
                : ''
            }
          >
            <div
              role="tabpanel"
              id="battle-tabpanel"
              aria-labelledby={`battle-tab-${tab}`}
              className="mt-4 min-w-0"
            >
              {tab === 'sentences' ? (
                <>
                  <section
                    aria-labelledby="eligible-sentences-title"
                    aria-busy={eligibleSentencesQuery.isFetching}
                  >
                    <div className="flex items-center justify-between gap-3">
                      {/* 탭 라벨이 제목을 대신하므로 화면에서는 감추고 이름만 남긴다. */}
                      <h2 id="eligible-sentences-title" className="sr-only">
                        제출 전 문장
                      </h2>

                      <span className="ml-auto text-xs text-stone-500 lg:text-base">
                        문장 에너지 높은 순 · 총 {sentencePage.totalElements}개
                      </span>
                    </div>

                    {phase && (
                      <dl
                        aria-label="문장 제출 점수 기준"
                        className="mt-3 grid grid-cols-2 gap-2 lg:text-base"
                      >
                        <div className="flex items-center justify-between rounded-xl bg-stone-100 px-3 py-2">
                          <dt className="text-xs font-semibold text-stone-600 lg:text-base">
                            최소 점수
                          </dt>
                          <dd
                            data-numeric
                            className="text-sm font-extrabold text-stone-900 lg:text-lg"
                          >
                            {phase.hardMinScore ?? '제한 없음'}
                          </dd>
                        </div>
                        <div className="flex items-center justify-between rounded-xl bg-violet-50 px-3 py-2">
                          <dt className="text-xs font-semibold text-violet-700 lg:text-base">
                            권장 점수
                          </dt>
                          <dd
                            data-numeric
                            className="text-sm font-extrabold text-violet-800 lg:text-lg"
                          >
                            {phase.recommendedScore ?? '기준 없음'}
                          </dd>
                        </div>
                      </dl>
                    )}

                    {eligibleSentencesQuery.isFetching && (
                      <p role="status" className="mt-2 text-xs text-stone-500">
                        문장 목록을 불러오는 중입니다.
                      </p>
                    )}

                    {sentencePage.content.length === 0 ? (
                      <div className="mt-3">
                        <EmptyState
                          title="제출 가능한 문장이 없어요"
                          description="AI 평가가 완료된 문장을 만든 뒤 다시 확인해 주세요."
                          action={
                            <ButtonLink to="/sentences/new" className="text-sm">
                              문장 만들러 가기
                            </ButtonLink>
                          }
                        />
                      </div>
                    ) : (
                      <div className="mt-3">
                        <EligibleSentenceList
                          items={sentencePage.content}
                          selectedSentenceVersionId={selectedSentenceVersionIdForSubmission}
                          onSelectionChange={handleSelectionChange}
                          minimumScore={phase?.hardMinScore}
                          renderExtra={(sentence) => (
                            <SentenceMoodTags sentenceVersionId={sentence.sentenceVersionId} />
                          )}
                        />
                      </div>
                    )}
                  </section>

                  <Pagination
                    className="mt-6"
                    ariaLabel="제출 가능 문장 페이지"
                    page={page}
                    totalPages={sentencePage.totalPages}
                    hasNext={sentencePage.hasNext}
                    disabled={eligibleSentencesQuery.isFetching}
                    onPrev={() => handlePageChange(page - 1)}
                    onNext={() => handlePageChange(page + 1)}
                  />
                </>
              ) : (
                <BossSubmissionHistory submissions={bossRun.mySubmissions} headingHidden />
              )}
            </div>

            {/* 제출 영역은 문장을 고르는 탭에서만 둔다. 이력 탭에서는 고른 문장이 보이지 않아
                무엇을 제출하는지 확인할 수 없다. 선택은 탭을 옮겨도 유지된다. */}
            <aside
              aria-live="polite"
              hidden={tab !== 'sentences'}
              className="fixed inset-x-3 bottom-[calc(env(safe-area-inset-bottom)+5.5rem)] z-30 mx-auto max-w-[454px] rounded-2xl border border-violet-200 bg-white/95 p-3 shadow-xl backdrop-blur lg:sticky lg:inset-x-auto lg:top-28 lg:bottom-auto lg:z-auto lg:mx-0 lg:mt-4 lg:max-w-none lg:p-5"
            >
              <p
                className={
                  selectedSentenceVersionIdForSubmission === null
                    ? 'text-sm text-stone-600 lg:text-base'
                    : 'text-sm font-semibold text-violet-700 lg:text-base'
                }
              >
                {selectedSentenceVersionIdForSubmission === null
                  ? '보스전에 사용할 문장을 선택해 주세요.'
                  : '문장 선택이 완료됐습니다.'}
              </p>

              {/* 제출 동의는 회원가입에서 받으므로 별도 확인 절차 없이 제출한다.
                  취소 불가와 수록 후보 안내만 남겨 제출 전에 한 번 더 보이게 한다. */}
              <p className="mt-1 hidden text-xs leading-5 text-stone-500 lg:block lg:text-sm">
                제출한 문장은 공동 책 수록 후보가 되며, 제출 후에는 취소할 수 없어요.
              </p>

              {submissionErrorMessage && (
                <p role="alert" className="mt-2 text-sm font-semibold text-red-600">
                  {submissionErrorMessage}
                </p>
              )}

              {submissionApiError?.code === 'UNAUTHORIZED' ? (
                <ButtonLink to="/login" size="lg" className="mt-2 w-full lg:mt-3">
                  로그인하기
                </ButtonLink>
              ) : (
                <Button
                  size="lg"
                  className="mt-2 w-full lg:mt-3"
                  disabled={selectedSentenceVersionIdForSubmission === null}
                  loading={submissionMutation.isPending}
                  onClick={handleSubmit}
                >
                  {submissionMutation.isPending ? '제출 중…' : '이 문장으로 제출하기'}
                </Button>
              )}
            </aside>
          </div>
        </>
      )}

      <details className="group mt-6 rounded-2xl border border-stone-200 bg-white px-4 py-3">
        <summary className="flex min-h-10 cursor-pointer list-none items-center justify-between gap-3 font-semibold text-stone-800">
          <span>원정 상세 정보</span>
          <span aria-hidden="true" className="text-stone-400 transition group-open:rotate-180">
            ↓
          </span>
        </summary>
        <div className="border-t border-stone-100 pt-4">
          {bossRun.bossDescription && (
            <p className="text-sm leading-6 text-stone-600">{bossRun.bossDescription}</p>
          )}

          <dl className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
            <div className="rounded-control bg-stone-50 p-3 text-center">
              <dt className="text-xs font-semibold text-stone-500">내 제출</dt>
              <dd data-numeric className="mt-1 font-bold text-stone-900">
                {bossRun.mySubmissionCount ?? 0}회
              </dd>
            </div>
            <div className="rounded-control bg-stone-50 p-3 text-center">
              <dt className="text-xs font-semibold text-stone-500">누적 피해</dt>
              <dd data-numeric className="mt-1 font-bold text-stone-900">
                {(bossRun.accumulatedDamage ?? 0).toLocaleString()}
              </dd>
            </div>
          </dl>
        </div>
      </details>

      <details className="group mt-3 rounded-2xl border border-stone-200 bg-white px-4 py-3">
        <summary className="flex min-h-10 cursor-pointer list-none items-center justify-between gap-3 font-semibold text-stone-800">
          <span>전체 기여도 순위</span>
          <span aria-hidden="true" className="text-stone-400 transition group-open:rotate-180">
            ↓
          </span>
        </summary>
        <div className="border-t border-stone-100 pt-1">
          <BossLeaderboard
            bossRunId={bossRunId}
            myUserId={myUserId}
            isMyUserPending={currentUserQuery.isPending}
            realtime={stream.isRealtime}
            headingHidden
          />
        </div>
      </details>
    </div>
  );
}

/** endsAt까지 남은 시간을 "1일 23시간" 형태로 표기한다. 이미 지난 시각이면 "종료"를 돌려준다. */
function formatRemainingTime(endsAt: string) {
  const remainingMs = new Date(endsAt).getTime() - Date.now();

  if (Number.isNaN(remainingMs) || remainingMs <= 0) {
    return '종료';
  }

  const totalMinutes = Math.floor(remainingMs / 60_000);
  const days = Math.floor(totalMinutes / (60 * 24));
  const hours = Math.floor((totalMinutes % (60 * 24)) / 60);
  const minutes = totalMinutes % 60;

  if (days > 0) {
    return `${days}일 ${hours}시간`;
  }

  if (hours > 0) {
    return `${hours}시간 ${minutes}분`;
  }

  return `${Math.max(1, minutes)}분`;
}

/**
 * 마지막으로 도착한 타격을 HP 아래에 남겨 둔다. 데미지 연출(DamageFloat)은 사라지지만
 * 이 칩은 다음 타격까지 남아, 원정이 지금 움직이고 있음을 보여준다.
 * 스크린리더 안내는 DamageFloat가 담당하므로 여기서는 중복 낭독을 피해 숨긴다.
 */
function LatestHitChip({ hit }: { hit: BossRunHit | null }) {
  if (hit === null) {
    return null;
  }

  return (
    <span
      aria-hidden="true"
      className="inline-flex items-center gap-1.5 font-semibold text-brand-strong"
    >
      <span className="relative flex size-2">
        <span className="absolute inline-flex size-full animate-ping rounded-full bg-violet-400 opacity-75 motion-reduce:hidden" />
        <span className="relative inline-flex size-2 rounded-full bg-violet-500" />
      </span>
      방금 <span data-numeric>-{hit.damage.toLocaleString()}</span> 타격
    </span>
  );
}

/** 상세 화면이 이 화면으로 통합되면서 뒤로 가기는 원정 목록을 향한다. */
function BackToBossListLink() {
  return (
    <Link
      to="/boss"
      aria-label="원정 목록으로 돌아가기"
      className="inline-flex size-10 items-center justify-center rounded-full text-xl text-stone-700 transition hover:bg-stone-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
    >
      <span aria-hidden="true">←</span>
    </Link>
  );
}

function getSubmissionErrorMessage(code: string) {
  switch (code) {
    case 'UNAUTHORIZED':
      return '로그인이 만료됐습니다. 다시 로그인한 뒤 제출해 주세요.';
    case 'FORBIDDEN':
      return '이 원정에 문장을 제출할 권한이 없습니다.';
    case 'POLICY_REJECTED':
      return '제출 정책을 만족하지 못했습니다. 문장 점수와 유사도 조건을 확인해 주세요.';
    case 'VALIDATION_ERROR':
      return '제출 정보가 올바르지 않습니다. 입력 내용을 확인해 주세요.';
    case 'INVALID_REQUEST':
      return '요청을 처리할 수 없습니다. 공동 책 이용동의 여부를 확인해 주세요.';
    case 'CONFLICT':
      return '원정 상태가 변경됐거나 이미 제출한 문장입니다. 정보를 새로 확인해 주세요.';
    case 'RATE_LIMITED':
      return '오늘 제출할 수 있는 횟수를 모두 사용했습니다.';
    case 'NOT_FOUND':
      return '원정 또는 선택한 문장을 찾을 수 없습니다.';
    case 'INTERNAL_ERROR':
      return '서버에서 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.';
    default:
      return '문장을 제출하지 못했습니다. 잠시 후 다시 시도해 주세요.';
  }
}
