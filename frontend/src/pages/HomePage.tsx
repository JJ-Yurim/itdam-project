import { Link } from 'react-router-dom';
import { AuthRequiredLink, useCurrentUserQuery } from '@/features/auth';
import { ParticipatingRaidCarousel, useBossRunsQuery, useMyBossRunsQuery } from '@/features/boss';
import {
  DailyWordList,
  useDailyWordsPreviewQuery,
  useRevealedWords,
  useTodayDailyWordsQuery,
} from '@/features/daily-words';
import { EvaluationUsageSummary, useTodayEvaluationUsageQuery } from '@/features/sentences';
import {
  ButtonLink,
  EmptyState,
  ErrorState,
  LoadingState,
  buttonClasses,
} from '@/shared/components';

import type { BossRunListParams, MyBossRunListParams } from '@/features/boss';

// 참여중인 원정은 동시 진행 수가 많지 않아 첫 페이지 한 번으로 충분히 담는다.
const PARTICIPATING_RAID_PARAMS = {
  status: 'ACTIVE',
  page: 0,
  size: 10,
} as const satisfies MyBossRunListParams;

const ACTIVE_RAID_PARAMS = {
  status: ['ACTIVE'],
  page: 0,
  size: 100,
} as const satisfies BossRunListParams;

const CREATE_SENTENCE_LABEL = '✨ 단어 활용해서 문장 생성하기';
// 주 CTA는 공용 버튼 스타일을 그대로 쓴다. 화면별로 CTA 모양이 갈라지지 않게 한다.
const CREATE_SENTENCE_CTA_CLASS = buttonClasses({ size: 'lg' }, 'mt-6 w-full');

export function HomePage() {
  const currentUserQuery = useCurrentUserQuery();
  // /users/me가 401이면 null(비회원)로 성공 처리되고, 서버 오류 등은 isError로 남는다.
  // 오류일 때는 회원일 수도 있으므로 비회원 화면으로 단정하지 않는다.
  const isMember = currentUserQuery.data != null;
  const isUnauthenticated = currentUserQuery.isSuccess && currentUserQuery.data === null;

  const dailyWordsQuery = useTodayDailyWordsQuery({ enabled: isMember });
  const previewQuery = useDailyWordsPreviewQuery({ enabled: isUnauthenticated });
  const evaluationUsageQuery = useTodayEvaluationUsageQuery({ enabled: isMember });
  // 참여중인 원정 = 문장을 1회 이상 제출한 ACTIVE 보스런. 비회원은 조회하지 않는다.
  const participatingRaidsQuery = useMyBossRunsQuery(PARTICIPATING_RAID_PARAMS, isMember);
  // 기존 원정 목록 API의 currentPhase.imageUrl을 참여 이력과 bossRunId로 결합한다.
  const activeRaidsQuery = useBossRunsQuery(ACTIVE_RAID_PARAMS, isMember);
  const activeRaidsById = new Map(
    (activeRaidsQuery.data?.content ?? []).map((bossRun) => [bossRun.bossRunId, bossRun]),
  );
  const participatingRaids = (participatingRaidsQuery.data?.content ?? []).map((bossRun) => {
    const activeRaid = activeRaidsById.get(bossRun.bossRunId);

    return {
      ...bossRun,
      currentPhaseImageUrl: activeRaid?.currentPhase.imageUrl,
      currentPhaseMaxHp: activeRaid?.currentPhase.maxHp,
      currentPhaseRemainingHp: activeRaid?.currentPhase.remainingHp,
    };
  });
  const isParticipatingRaidsPending =
    participatingRaidsQuery.isPending || activeRaidsQuery.isPending;
  const isParticipatingRaidsError = participatingRaidsQuery.isError || activeRaidsQuery.isError;
  const isParticipatingRaidsSuccess =
    participatingRaidsQuery.isSuccess && activeRaidsQuery.isSuccess;

  // 회원은 개인 단어 세트, 비회원은 열람 전용 미리보기를 사용한다.
  const wordsQuery = isMember ? dailyWordsQuery : previewQuery;
  const isWordsPending =
    currentUserQuery.isPending || (currentUserQuery.isSuccess && wordsQuery.isPending);

  // 개봉 상태는 새로고침 후에도 유지하고 개인 문장 생성 화면과 공유한다.
  const revealOwner = currentUserQuery.isSuccess
    ? (currentUserQuery.data?.id ?? ('guest' as const))
    : undefined;
  const { revealedWordIds, revealWord } = useRevealedWords(
    revealOwner,
    wordsQuery.data?.serviceDate,
  );

  return (
    <div className="space-y-12 lg:space-y-8">
      <section
        aria-labelledby="daily-words-title"
        className="min-w-0 lg:rounded-3xl lg:border lg:border-stone-200 lg:bg-white lg:p-7 lg:shadow-sm"
      >
        <div className="mb-4 flex items-end justify-between gap-3">
          <div>
            <h1 id="daily-words-title" className="text-2xl font-extrabold text-violet-800">
              오늘의 단어팩
            </h1>
          </div>

          {wordsQuery.isSuccess && (
            <time dateTime={wordsQuery.data.serviceDate} className="text-xs text-stone-500">
              {wordsQuery.data.serviceDate}
            </time>
          )}
        </div>

        <p className="mb-6 text-sm leading-6 text-stone-600">
          {isUnauthenticated ? (
            <>
              카드를 한 장씩 뒤집어 오늘의 단어를 구경해 보세요.
              <br />
              문장 만들기는 로그인 후 이용할 수 있어요.
            </>
          ) : (
            '카드를 한 장씩 뒤집어 오늘의 이야깃거리를 확인해 보세요.'
          )}
        </p>

        {isWordsPending && (
          <LoadingState
            title="오늘의 단어를 준비하고 있어요"
            description="새로운 이야기의 재료를 가져오는 중입니다."
          />
        )}

        {currentUserQuery.isError && (
          <ErrorState
            title="사용자 정보를 확인하지 못했어요"
            description="일시적인 오류로 화면을 준비하지 못했어요. 잠시 후 다시 시도해 주세요."
            onRetry={() => void currentUserQuery.refetch()}
          />
        )}

        {currentUserQuery.isSuccess && wordsQuery.isError && (
          <ErrorState
            title="오늘의 단어를 불러오지 못했어요"
            description="잠시 후 다시 시도해 주세요."
            onRetry={() => void wordsQuery.refetch()}
          />
        )}

        {wordsQuery.isSuccess && wordsQuery.data.items.length === 0 && (
          <EmptyState
            title="아직 준비된 단어가 없어요"
            description="오늘의 단어가 등록되면 이곳에 표시됩니다."
          />
        )}

        {wordsQuery.isSuccess && wordsQuery.data.items.length > 0 && (
          <DailyWordList
            items={wordsQuery.data.items}
            revealedWordIds={revealedWordIds}
            onReveal={revealWord}
          />
        )}

        {isMember && (
          <div className="mt-10">
            {evaluationUsageQuery.isPending && (
              <div
                role="status"
                aria-live="polite"
                className="rounded-xl bg-violet-50 px-4 py-3 text-sm text-stone-600"
              >
                AI 평가 횟수를 확인하고 있어요.
              </div>
            )}

            {evaluationUsageQuery.isError && (
              <div
                role="alert"
                className="flex items-center justify-between gap-3 rounded-xl bg-red-50 px-4 py-3"
              >
                <span className="text-sm text-red-700">평가 횟수를 불러오지 못했어요.</span>
                <button
                  type="button"
                  onClick={() => void evaluationUsageQuery.refetch()}
                  className="shrink-0 text-sm font-semibold text-red-700 underline underline-offset-2"
                >
                  다시 시도
                </button>
              </div>
            )}

            {evaluationUsageQuery.isSuccess && (
              <EvaluationUsageSummary usage={evaluationUsageQuery.data} />
            )}
          </div>
        )}

        {isMember && (
          <Link to="/sentences/new" className={CREATE_SENTENCE_CTA_CLASS}>
            {CREATE_SENTENCE_LABEL}
          </Link>
        )}

        {isUnauthenticated && (
          <AuthRequiredLink to="/sentences/new" className={CREATE_SENTENCE_CTA_CLASS}>
            {CREATE_SENTENCE_LABEL}
          </AuthRequiredLink>
        )}
      </section>
      <section
        aria-labelledby="participating-raid-title"
        className="min-w-0 lg:rounded-3xl lg:border lg:border-stone-200 lg:bg-white lg:p-7 lg:shadow-sm"
      >
        <h2 id="participating-raid-title" className="mb-4 text-[15px] font-semibold text-stone-900">
          참여중인 원정
        </h2>

        {isUnauthenticated && (
          <div className="rounded-2xl border border-stone-200 bg-white p-7 text-center">
            <p className="font-semibold text-stone-700">로그인하면 참여중인 원정이 여기에 보여요</p>
            <p className="mt-2 text-sm text-stone-500">
              어떤 원정이 진행 중인지 먼저 둘러볼 수 있어요.
            </p>
            <ButtonLink to="/boss" variant="secondary" className="mt-4 text-sm">
              원정 구경하러 가기
            </ButtonLink>
          </div>
        )}

        {isMember && isParticipatingRaidsPending && (
          <LoadingState
            title="참여중인 원정을 확인하고 있어요"
            description="내가 문장을 제출한 원정을 가져오는 중입니다."
          />
        )}

        {isMember && isParticipatingRaidsError && (
          <ErrorState
            title="참여중인 원정을 불러오지 못했어요"
            description="잠시 후 다시 시도해 주세요."
            onRetry={() => {
              void participatingRaidsQuery.refetch();
              void activeRaidsQuery.refetch();
            }}
          />
        )}

        {isMember && isParticipatingRaidsSuccess && participatingRaids.length === 0 && (
          <div className="rounded-2xl border border-stone-200 bg-white p-7 text-center">
            <p className="font-semibold text-stone-700">아직 참여중인 원정이 없어요</p>
            <p className="mt-2 text-sm text-stone-500">원정에 문장을 제출하면 이곳에 표시됩니다.</p>
            <ButtonLink to="/boss" className="mt-4 text-sm">
              원정 보러 가기
            </ButtonLink>
          </div>
        )}

        {isMember && isParticipatingRaidsSuccess && participatingRaids.length > 0 && (
          <ParticipatingRaidCarousel bossRuns={participatingRaids} />
        )}
      </section>
    </div>
  );
}
