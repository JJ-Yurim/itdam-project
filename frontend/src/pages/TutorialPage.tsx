import { useEffect, useMemo, useReducer, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import {
  BossClearResult,
  BossHitStage,
  BossImage,
  EligibleSentenceCard,
  getBossAttackIntensity,
} from '@/features/boss';
import { BookReaderExperience } from '@/features/book-reader/components/BookReaderExperience';
import { BookGenerationStatusContent } from '@/features/book-generation';
import { DailyWordList } from '@/features/daily-words';
import { BookContributorList, LibraryBookCover } from '@/features/library';
import {
  DraftWordPack,
  EvaluationUsageSummary,
  getMatchedDailyWordIds,
  SentenceEvaluationDetails,
  SentenceEvaluationResultCard,
  SentenceEvaluationScoreSummary,
} from '@/features/sentences';
import {
  DamiGuide,
  initialTutorialState,
  markTutorialSeen,
  TutorialCoachMark,
  TutorialCoachMarkLabel,
  tutorialBookBody,
  tutorialBookCoverImageUrl,
  tutorialBookContents,
  tutorialBookPages,
  tutorialBoss,
  tutorialBossImageUrl,
  tutorialClearSubmissions,
  tutorialEligibleSentence,
  tutorialEvaluation,
  tutorialReducer,
  tutorialSubmissionResult,
  TUTORIAL_STEP_LABELS,
  tutorialWords,
} from '@/features/tutorial';
import { Button, ButtonLink, Modal, Spinner } from '@/shared/components';
import { ServiceShell } from '@/shared/layout/AppLayout';

import type { TutorialState } from '@/features/tutorial';

import './BookReaderPage.css';

const EVALUATION_DELAY_MS = 700;
const ATTACK_DELAY_MS = 850;
const BOSS_DEFEAT_EFFECT_MS = 1_900;
const TUTORIAL_COMPLETION_STEP = TUTORIAL_STEP_LABELS.length - 1;
const TUTORIAL_READER_STEP = TUTORIAL_COMPLETION_STEP - 1;
const TUTORIAL_BOOK_STEP = TUTORIAL_READER_STEP - 1;
const TUTORIAL_COMPOSE_GUIDE_LABEL = '이번에는 내가 도와줄게! 예시 문장을 눌러봐';

const tutorialLeaderboard = [
  { rank: 1, nickname: '달빛서기', totalDamage: 184 },
  { rank: 2, nickname: '문장수집가', totalDamage: 172 },
  { rank: 3, nickname: '별빛여우', totalDamage: 161 },
  { rank: 4, nickname: '윤슬지기', totalDamage: 149 },
  { rank: 5, nickname: '새벽작가', totalDamage: 142 },
] as const;

const tutorialMyRank = { rank: 6, totalDamage: tutorialBoss.damage } as const;
const tutorialAttackHit = {
  damage: tutorialBoss.damage,
  baseScore: tutorialEvaluation.scores?.baseScore ?? 0,
  key: 1,
} as const;
const tutorialAttackIntensity = getBossAttackIntensity(
  tutorialAttackHit.baseScore,
  tutorialBoss.hardMinScore,
  tutorialBoss.recommendedScore,
);

const guideContent = [
  {
    title: '안녕, 나는 이야기 마법사 다미야!',
    description:
      '지금 보이는 실제 서비스 화면을 함께 둘러볼게. 모든 내용은 튜토리얼용이라 마음껏 눌러봐도 괜찮아!',
    imageSrc: '/tutorial-assets/dami-welcome.png',
    imagePosition: 'left',
  },
  {
    title: '오늘의 단어를 먼저 만나볼까?',
    description:
      '카드를 모두 열어 오늘의 이야기 재료를 살펴봐. 마음에 드는 단어는 다음 문장에서 활용할 수 있어!',
    imageSrc: '/tutorial-assets/dami-words.png',
    imagePosition: 'center',
  },
  {
    title: '이제 너만의 문장을 만들어봐!',
    description: '열어본 단어를 활용해 문장을 만들어봐. 직접 쓰거나 예시 문장으로 시작해도 좋아.',
    imageSrc: '/tutorial-assets/dami-sentence.png',
    imagePosition: 'right',
  },
  {
    title: '문장에 담긴 힘을 읽고 있어!',
    description:
      'AI가 표현과 분위기를 살펴보고 점수와 공격력으로 바꿔줘. 이 결과는 튜토리얼용 예시야.',
    imageSrc: '/tutorial-assets/dami-evaluation.png',
    imagePosition: 'left',
  },
  {
    title: '평가된 문장으로 원정에 참여해봐!',
    description:
      '문장을 선택해서 제출하면 문장의 힘만큼 수호자의 HP가 줄어들어. 동료들과 함께하는 거야!',
    imageSrc: '/tutorial-assets/dami-boss.png',
    imagePosition: 'center',
  },
  {
    title: '완성된 책의 상세 화면을 살펴볼까?',
    description:
      '표지와 작품 소개, 참여 작가를 확인하고 공동 소설집 전체 읽기 버튼으로 책을 펼칠 수 있어!',
    imageSrc: '/tutorial-assets/dami-book.png',
    imagePosition: 'center',
  },
  {
    title: '이제 완성된 이야기를 직접 펼쳐봐!',
    description:
      '보라색과 주황색으로 강조된 문장은 회원이 만든 문장이야. 문장을 눌러 작성자와 보스전 기여 정보를 확인하고, 오른쪽 페이지를 눌러 이야기를 넘겨봐.',
    imageSrc: '/tutorial-assets/dami-book.png',
    imagePosition: 'left',
  },
] as const;

const bossGuideContent = [
  {
    label: '보스의 남은 체력을 확인해보세요',
    targetId: 'tutorial-boss-hero',
  },
  {
    label: '실시간 기여도와 내 순위예요',
    targetId: 'tutorial-boss-leaderboard',
  },
  {
    label: '분위기와 약점에 맞추면 더 강해져요',
    targetId: 'tutorial-boss-moods',
  },
  {
    label: '오늘 남은 제출 횟수예요',
    targetId: 'tutorial-boss-submission-limit',
  },
  {
    label: '공격에 사용할 문장을 선택하세요',
    targetId: 'tutorial-coach-select',
  },
] as const;

type EvaluationGuideStep = 0 | 1 | 2;

const evaluationGuideContent = [
  {
    label: '기본 점수와 보너스, 문장 에너지를 확인해보세요',
    targetId: 'tutorial-evaluation-summary',
  },
  {
    label: '세부 점수와 감지된 분위기를 확인해보세요',
    targetId: 'tutorial-evaluation-details',
  },
  {
    label: '평가된 문장으로 원정에 참여하세요',
    targetId: 'tutorial-coach-expedition',
  },
] as const;

const evaluationUsage = {
  serviceDate: '2026-08-07',
  usedCount: 8,
  limit: 15,
  remainingCount: 7,
};

const tutorialBookGenerationStatus = {
  bossRunId: 700,
  bookId: 900,
  bookStatus: 'COMPLETED',
  jobId: 1200,
  jobStatus: 'SUCCESS',
  requestedAt: '2026-08-07T09:10:00+09:00',
  completedAt: '2026-08-07T09:12:00+09:00',
  updatedAt: '2026-08-07T09:12:00+09:00',
} as const;

function TutorialSessionControl() {
  return (
    <div className="flex items-center gap-2 rounded-full bg-violet-50 py-1 pr-1 pl-3">
      <span className="hidden text-xs font-bold text-violet-700 min-[400px]:inline">
        다미와 튜토리얼 중
      </span>
      <span
        aria-hidden="true"
        className="flex size-9 items-center justify-center rounded-full bg-gradient-to-br from-violet-200 to-violet-400"
      >
        🐾
      </span>
    </div>
  );
}

interface TutorialHomeViewProps {
  state: TutorialState;
  onReveal: (wordId: number) => void;
  onNext: () => void;
}

function TutorialHomeView({ state, onReveal, onNext }: TutorialHomeViewProps) {
  const revealedWordIds = new Set(state.revealedWordIds);
  const canContinue = revealedWordIds.size === tutorialWords.length;
  const remainingWordCount = tutorialWords.length - revealedWordIds.size;

  return (
    <div className="space-y-12 lg:space-y-8">
      <section
        aria-labelledby="tutorial-daily-words-title"
        className="rounded-3xl border border-violet-300 bg-white p-5 shadow-lg shadow-violet-100 ring-4 ring-violet-100 min-[400px]:p-6 lg:p-7"
      >
        <div className="mb-4 flex items-end justify-between gap-3">
          <div>
            <p className="text-xs font-bold text-violet-500">다미가 가리키는 곳</p>
            <h1
              id="tutorial-daily-words-title"
              className="mt-1 text-2xl font-extrabold text-violet-800"
            >
              오늘의 단어
            </h1>
          </div>
          <time dateTime="2026-08-07" className="text-xs text-stone-500">
            2026-08-07
          </time>
        </div>

        <p className="mb-6 text-sm leading-6 text-stone-600">
          카드를 하나씩 눌러 오늘의 이야기 재료를 확인해 보세요.
        </p>

        <TutorialCoachMark
          active={state.step === 1 && !canContinue}
          label="단어 카드를 눌러보세요"
          id="tutorial-coach-words"
        >
          <DailyWordList
            items={tutorialWords}
            revealedWordIds={revealedWordIds}
            onReveal={onReveal}
          />
        </TutorialCoachMark>

        <div className="mt-8">
          <EvaluationUsageSummary usage={evaluationUsage} />
        </div>

        <div
          className="mt-4 rounded-xl bg-stone-50 px-4 py-3 text-sm text-stone-600"
          aria-live="polite"
        >
          <strong className="text-violet-700">
            {revealedWordIds.size}/{tutorialWords.length}개
          </strong>
          의 단어를 확인했어요.
          {!canContinue && state.step === 1 && ` ${remainingWordCount}개를 더 열어보세요.`}
        </div>

        <TutorialCoachMark
          active={state.step === 1 && canContinue}
          label="다음 단계로 이동하세요"
          className="mt-4"
          id="tutorial-coach-next"
        >
          <Button size="lg" className="w-full" disabled={!canContinue} onClick={onNext}>
            오늘의 단어로 문장 만들기
          </Button>
        </TutorialCoachMark>
      </section>

      <section className="rounded-3xl border border-stone-200 bg-white p-5 shadow-sm lg:p-7">
        <h2 className="text-[15px] font-semibold text-stone-900">참여중인 원정</h2>
        <article className="mt-4 flex items-center gap-4 rounded-2xl bg-violet-50 p-4">
          <BossImage alt="새벽의 수호자" sizeClassName="size-20" />
          <div className="min-w-0 flex-1">
            <p className="text-xs font-semibold text-violet-600">Phase 1 진행 중</p>
            <h3 className="mt-1 font-extrabold text-stone-900">새벽의 수호자</h3>
            <div className="mt-3 h-2 overflow-hidden rounded-full bg-violet-100">
              <div className="h-full w-[38%] rounded-full bg-violet-500" />
            </div>
          </div>
          <span aria-hidden="true" className="text-stone-400">
            ›
          </span>
        </article>
      </section>
    </div>
  );
}

interface TutorialSentenceViewProps {
  state: TutorialState;
  evaluationGuideStep: EvaluationGuideStep;
  isSentenceInputFocused: boolean;
  onReveal: (wordId: number) => void;
  onSentenceChange: (sentence: string) => void;
  onSentenceFocusChange: (focused: boolean) => void;
  onFillExample: () => void;
  onEvaluate: () => void;
  onAdvanceEvaluationGuide: () => void;
  onNext: () => void;
}

function TutorialSentenceView({
  state,
  evaluationGuideStep,
  isSentenceInputFocused,
  onReveal,
  onSentenceChange,
  onSentenceFocusChange,
  onFillExample,
  onEvaluate,
  onAdvanceEvaluationGuide,
  onNext,
}: TutorialSentenceViewProps) {
  const matchedWordIds = useMemo(
    () => getMatchedDailyWordIds(state.sentence, tutorialWords),
    [state.sentence],
  );
  const revealedWordIds = new Set(state.revealedWordIds);
  const canEvaluate = state.sentence.trim().length > 0;
  const isEvaluationStep = state.step === 3;
  const showComposeGuide = !isEvaluationStep && (!canEvaluate || isSentenceInputFocused);
  const showEvaluateGuide = !isEvaluationStep && canEvaluate && !isSentenceInputFocused;

  return (
    <div className="mx-auto w-full max-w-4xl">
      <header>
        <h1 className="text-2xl font-extrabold text-violet-800">오늘의 문장 만들기</h1>
        <p className="mt-2 text-sm leading-6 text-stone-600">
          오늘의 단어를 사용해 최대 500자의 짧은 글을 작성해 보세요. AI 평가를 요청하면 문장을
          저장하고 함께 평가해요.
        </p>
      </header>

      <section className="mt-6 rounded-3xl border border-stone-200 bg-white p-5 shadow-sm min-[400px]:p-6 lg:p-7">
        <div className="flex items-center justify-between gap-3">
          <h2 className="font-extrabold text-stone-900">오늘의 단어팩</h2>
          <span className="text-xs font-semibold text-stone-500">
            활용한 오늘의 단어 {matchedWordIds.size}개
          </span>
        </div>
        <div className="mt-4">
          <DraftWordPack
            items={tutorialWords}
            matchedWordIds={matchedWordIds}
            revealedWordIds={revealedWordIds}
            onReveal={onReveal}
          />
        </div>

        <TutorialCoachMark
          active={showComposeGuide}
          label={TUTORIAL_COMPOSE_GUIDE_LABEL}
          showLabel={false}
          className="mt-7 -mx-2 p-2"
          id="tutorial-coach-compose"
        >
          <label htmlFor="tutorial-sentence" className="block text-sm font-bold text-stone-800">
            나의 문장
          </label>
          <textarea
            id="tutorial-sentence"
            value={state.sentence}
            readOnly={isEvaluationStep}
            maxLength={500}
            rows={6}
            placeholder="오늘의 단어를 활용해 문장을 작성해 보세요."
            onChange={(event) => onSentenceChange(event.target.value)}
            onFocus={() => onSentenceFocusChange(true)}
            onBlur={() => onSentenceFocusChange(false)}
            className="mt-2 w-full resize-none rounded-2xl border border-stone-300 bg-white p-4 leading-7 text-stone-900 outline-none transition focus:border-stone-300 focus:ring-0 read-only:bg-stone-50"
          />
          <div className="mt-2 flex items-center justify-between text-xs text-stone-500">
            <span>
              {state.sentence.trim().length === 0
                ? '열어본 단어를 활용해 문장을 만들어 보세요.'
                : matchedWordIds.size === 0
                  ? '오늘의 단어를 쓰면 점수가 더 올라가요.'
                  : '평가 가능한 문장이에요.'}
            </span>
            <span>{state.sentence.length} / 500자</span>
          </div>
          {!isEvaluationStep && (
            <div className="relative mt-12">
              <TutorialCoachMarkLabel
                active={showComposeGuide}
                label={TUTORIAL_COMPOSE_GUIDE_LABEL}
                labelAlign="center"
              />
              <Button variant="secondary" className="w-full" onClick={onFillExample}>
                예시 문장 채우기
              </Button>
            </div>
          )}
        </TutorialCoachMark>

        {!isEvaluationStep && (
          <TutorialCoachMark
            active={showEvaluateGuide}
            dimScreen={false}
            label="AI 평가를 요청하세요"
            className="mt-3"
            id="tutorial-coach-evaluate"
          >
            <Button size="lg" className="w-full" disabled={!canEvaluate} onClick={onEvaluate}>
              AI 평가 요청하기
            </Button>
          </TutorialCoachMark>
        )}
      </section>

      {isEvaluationStep && (
        <div id="tutorial-evaluation-result" className="mt-5 rounded-3xl ring-4 ring-violet-100">
          {state.evaluationStatus === 'loading' ? (
            <div
              role="status"
              className="flex min-h-44 flex-col items-center justify-center rounded-3xl border border-violet-200 bg-violet-50 text-center"
            >
              <Spinner className="size-8 text-violet-600" />
              <strong className="mt-4 text-stone-900">다미가 문장의 힘을 읽고 있어요</strong>
              <span className="mt-1 text-sm text-stone-500">
                표현과 분위기를 살펴보는 중이에요.
              </span>
            </div>
          ) : (
            <div>
              {evaluationGuideStep < 2 && (
                <button
                  type="button"
                  aria-label="화면을 눌러 다음 평가 안내 보기"
                  onClick={onAdvanceEvaluationGuide}
                  className="fixed inset-0 z-[47] cursor-pointer touch-manipulation bg-transparent focus:outline-none"
                >
                  <span className="sr-only">다음 평가 안내 보기</span>
                </button>
              )}
              <SentenceEvaluationResultCard>
                <TutorialCoachMark
                  active={evaluationGuideStep === 0}
                  label={evaluationGuideContent[0].label}
                  advanceHint
                  className="rounded-xl"
                  id={evaluationGuideContent[0].targetId}
                >
                  <SentenceEvaluationScoreSummary evaluation={tutorialEvaluation} />
                </TutorialCoachMark>
                <TutorialCoachMark
                  active={evaluationGuideStep === 1}
                  label={evaluationGuideContent[1].label}
                  advanceHint
                  className="rounded-xl"
                  id={evaluationGuideContent[1].targetId}
                >
                  <SentenceEvaluationDetails evaluation={tutorialEvaluation} />
                </TutorialCoachMark>
              </SentenceEvaluationResultCard>
              <TutorialCoachMark
                active={evaluationGuideStep === 2}
                label={evaluationGuideContent[2].label}
                className="mt-4"
                id={evaluationGuideContent[2].targetId}
              >
                <Button size="lg" className="w-full" onClick={onNext}>
                  이 문장으로 원정 참여하기
                </Button>
              </TutorialCoachMark>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

interface TutorialBossViewProps {
  state: TutorialState;
  onSelectSentence: (sentenceVersionId: number | null) => void;
  onAttack: () => void;
  onGuideInteract: () => void;
  onNext: () => void;
}

function TutorialLeaderboardPreview() {
  return (
    <section
      aria-labelledby="tutorial-leaderboard-title"
      className="rounded-2xl border border-violet-200 bg-white p-3 shadow-sm lg:p-5"
    >
      <div className="mb-2 flex items-center justify-between gap-3 border-b border-stone-100 pb-2 lg:mb-3 lg:pb-3">
        <h2
          id="tutorial-leaderboard-title"
          className="text-sm font-extrabold text-stone-800 lg:text-xl"
        >
          실시간 기여도
        </h2>
        <span
          aria-label="실시간 순위 갱신 중"
          className="flex items-center gap-1.5 text-[11px] font-bold text-emerald-600 lg:text-sm"
        >
          <span
            aria-hidden="true"
            className="size-2 animate-pulse rounded-full bg-emerald-500 lg:size-3"
          />
          LIVE
        </span>
      </div>

      <ol aria-label="실시간 기여도 미리보기" className="space-y-0.5 lg:space-y-1">
        {tutorialLeaderboard.map((entry, index) => (
          <li
            key={entry.rank}
            className={[
              'min-h-8 items-center gap-2 rounded-lg px-2 text-xs text-stone-700 lg:min-h-10 lg:gap-3 lg:px-3 lg:text-base',
              index >= 3 ? 'hidden lg:flex' : 'flex',
            ].join(' ')}
          >
            <span
              className={[
                'w-5 shrink-0 text-center font-extrabold lg:w-7',
                entry.rank <= 3 ? 'text-violet-700' : 'text-stone-400',
              ].join(' ')}
            >
              {entry.rank}
            </span>
            <span className="min-w-0 flex-1 truncate font-semibold">{entry.nickname}</span>
            <span className="shrink-0 font-bold">{entry.totalDamage}</span>
          </li>
        ))}
      </ol>

      <p className="mt-2 flex items-center justify-between rounded-lg bg-violet-100 px-2.5 py-2 text-xs font-extrabold text-violet-700 lg:px-3 lg:py-3 lg:text-base">
        <span>내 순위 {tutorialMyRank.rank}위</span>
        <span>{tutorialMyRank.totalDamage} 피해</span>
      </p>
    </section>
  );
}

function TutorialBossView({
  state,
  onSelectSentence,
  onAttack,
  onGuideInteract,
  onNext,
}: TutorialBossViewProps) {
  const [showClearResult, setShowClearResult] = useState(false);
  const [bossGuideStep, setBossGuideStep] = useState(0);
  const completedBookRef = useRef<HTMLDivElement>(null);
  const isSelected = state.selectedSentenceVersionId === tutorialEligibleSentence.sentenceVersionId;
  const isLastBossGuideStep = bossGuideStep === bossGuideContent.length - 1;
  const canAdvanceBossGuide = !isLastBossGuideStep && state.attackStatus === 'idle';
  const bossGuide = bossGuideContent[bossGuideStep];
  const currentHp =
    state.attackStatus === 'complete' ? tutorialBoss.hpAfterAttack : tutorialBoss.hpBeforeAttack;
  const hpPercent = (currentHp / tutorialBoss.maxHp) * 100;
  const showAttackDock = isSelected && state.attackStatus !== 'complete';

  useEffect(() => {
    if (state.attackStatus !== 'complete') return;

    const timeoutId = window.setTimeout(() => setShowClearResult(true), BOSS_DEFEAT_EFFECT_MS);
    return () => window.clearTimeout(timeoutId);
  }, [state.attackStatus]);

  useEffect(() => {
    if (!showClearResult) return;

    const timeoutId = window.setTimeout(() => {
      completedBookRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }, 100);
    return () => window.clearTimeout(timeoutId);
  }, [showClearResult]);

  useEffect(() => {
    if (state.attackStatus !== 'idle') return;

    const timeoutId = window.setTimeout(() => {
      const targetId =
        isSelected && isLastBossGuideStep ? 'tutorial-coach-submit' : bossGuide.targetId;
      const target = document.getElementById(targetId);
      if (typeof target?.scrollIntoView === 'function') {
        target.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    }, 80);

    return () => window.clearTimeout(timeoutId);
  }, [bossGuide.targetId, isLastBossGuideStep, isSelected, state.attackStatus]);

  return (
    <div className={showAttackDock ? 'pb-24 lg:pb-0' : ''}>
      {canAdvanceBossGuide && (
        <button
          type="button"
          aria-label="화면을 눌러 다음 보스 안내 보기"
          onClick={() => {
            onGuideInteract();
            setBossGuideStep((step) => Math.min(bossGuideContent.length - 1, step + 1));
          }}
          className="fixed inset-0 z-[47] cursor-pointer touch-manipulation bg-transparent focus:outline-none"
        >
          <span className="sr-only">화면 어디든 눌러 다음 설명으로 이동하세요</span>
        </button>
      )}

      <span className="inline-flex min-h-10 items-center gap-2 text-sm font-bold text-stone-700">
        <span aria-hidden="true">←</span> 이야기 원정
      </span>

      <div className="lg:grid lg:grid-cols-[minmax(0,1fr)_320px] lg:items-start lg:gap-4">
        <TutorialCoachMark
          active={bossGuideStep === 0 && state.attackStatus === 'idle'}
          label={bossGuideContent[0].label}
          advanceHint
          id="tutorial-boss-hero"
          className="mt-8 rounded-3xl lg:mt-10"
        >
          <section
            aria-label="보스 정보"
            className="rounded-3xl border border-violet-300 bg-white p-4 shadow-lg shadow-violet-100 ring-4 ring-violet-100 lg:flex lg:min-h-[304px] lg:items-center lg:gap-10 lg:p-8"
          >
            <div className="relative mt-2 flex justify-center lg:mt-0 lg:shrink-0">
              <span
                className={
                  state.attackStatus === 'complete' ? 'boss-defeat inline-flex' : 'inline-flex'
                }
              >
                <BossHitStage
                  hit={state.attackStatus === 'complete' ? tutorialAttackHit : null}
                  intensity={tutorialAttackIntensity}
                  charging={state.attackStatus === 'loading'}
                >
                  <BossImage
                    imageUrl={tutorialBossImageUrl}
                    alt={tutorialBoss.name}
                    sizeClassName="size-32 lg:size-64"
                  />
                </BossHitStage>
              </span>
              {state.attackStatus === 'complete' && !showClearResult && (
                <p
                  role="status"
                  aria-label="새벽의 수호자 처치 완료"
                  className="boss-clear-banner pointer-events-none absolute top-1/2 left-1/2 z-30 -translate-x-1/2 -translate-y-1/2 rounded-2xl bg-stone-950/85 px-5 py-3 text-center whitespace-nowrap text-white shadow-xl"
                >
                  <strong className="block text-xl font-black text-amber-300">원정 클리어!</strong>
                  <span className="mt-1 block text-xs text-white/80">마지막 일격에 성공했어요</span>
                </p>
              )}
            </div>
            <div className="mt-4 min-w-0 lg:mt-0 lg:flex-1">
              <h1 className="text-center text-2xl font-extrabold text-stone-900 lg:text-left lg:text-3xl">
                {tutorialBoss.name}
              </h1>
              <div className="mt-4 flex justify-between text-xs font-bold text-stone-600 lg:text-base">
                <span>Phase 1</span>
                <span>
                  {currentHp.toLocaleString()} / {tutorialBoss.maxHp.toLocaleString()}
                </span>
              </div>
              <div className="mt-2 h-4 overflow-hidden rounded-full bg-stone-100 lg:h-5">
                <div
                  className="h-full rounded-full bg-gradient-to-r from-rose-500 to-orange-400 transition-[width] duration-700"
                  style={{ width: `${hpPercent}%` }}
                />
              </div>
            </div>
          </section>
        </TutorialCoachMark>

        <TutorialCoachMark
          active={bossGuideStep === 1 && state.attackStatus === 'idle'}
          label={bossGuideContent[1].label}
          advanceHint
          id="tutorial-boss-leaderboard"
          className="mt-8 lg:mt-10"
        >
          <TutorialLeaderboardPreview />
        </TutorialCoachMark>
      </div>

      <div
        aria-label="원정 분위기와 약점"
        className="mt-8 flex flex-wrap items-center gap-2 text-xs lg:text-base"
      >
        <TutorialCoachMark
          active={bossGuideStep === 2 && state.attackStatus === 'idle'}
          label={bossGuideContent[2].label}
          advanceHint
          id="tutorial-boss-moods"
          className="flex flex-wrap items-center gap-2"
          labelAlign="start"
        >
          <dl className="rounded-full bg-white px-3 py-2 shadow-sm lg:px-4 lg:py-3">
            <dt className="inline text-stone-500">분위기 </dt>
            <dd className="inline font-extrabold text-violet-700">{tutorialBoss.theme}</dd>
          </dl>
          <dl className="rounded-full bg-white px-3 py-2 shadow-sm lg:px-4 lg:py-3">
            <dt className="inline text-stone-500">약점 </dt>
            <dd className="inline font-extrabold text-amber-600">{tutorialBoss.weakness}</dd>
          </dl>
        </TutorialCoachMark>
        <TutorialCoachMark
          active={bossGuideStep === 3 && state.attackStatus === 'idle'}
          label={bossGuideContent[3].label}
          advanceHint
          id="tutorial-boss-submission-limit"
          className="rounded-full"
        >
          <dl className="rounded-full bg-white px-3 py-2 shadow-sm lg:px-4 lg:py-3">
            <dt className="inline text-stone-500">남은 제출 </dt>
            <dd className="inline font-extrabold text-stone-900">2회</dd>
          </dl>
        </TutorialCoachMark>
      </div>

      {state.attackStatus !== 'complete' && (
        <section className="mt-4 rounded-3xl border border-stone-200 bg-white p-4 shadow-sm lg:p-8">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-lg font-extrabold text-stone-900 lg:text-2xl">제출 가능한 문장</h2>
            <span className="text-xs text-stone-500 lg:text-base">1개</span>
          </div>

          <dl aria-label="문장 제출 점수 기준" className="mt-3 grid grid-cols-2 gap-2 lg:text-base">
            <div className="flex items-center justify-between rounded-xl bg-stone-100 px-3 py-2 lg:px-4 lg:py-3">
              <dt className="text-xs font-semibold text-stone-600 lg:text-base">최소 점수</dt>
              <dd className="text-sm font-extrabold text-stone-900 lg:text-lg">
                {tutorialBoss.hardMinScore}
              </dd>
            </div>
            <div className="flex items-center justify-between rounded-xl bg-violet-50 px-3 py-2 lg:px-4 lg:py-3">
              <dt className="text-xs font-semibold text-violet-700 lg:text-base">권장 점수</dt>
              <dd className="text-sm font-extrabold text-violet-800 lg:text-lg">
                {tutorialBoss.recommendedScore}
              </dd>
            </div>
          </dl>

          <TutorialCoachMark
            active={!isSelected && isLastBossGuideStep && state.attackStatus === 'idle'}
            label="공격에 사용할 문장을 선택하세요"
            className="mt-3"
            id="tutorial-coach-select"
          >
            <div className="divide-y divide-stone-100">
              <EligibleSentenceCard
                sentence={tutorialEligibleSentence}
                isSelected={isSelected}
                onToggle={(sentenceVersionId) =>
                  onSelectSentence(isSelected ? null : sentenceVersionId)
                }
                extra={
                  <dl
                    aria-label="분위기와 약점 일치 보너스"
                    className="mt-2 flex flex-wrap gap-1.5 lg:text-sm"
                  >
                    <div className="inline-flex items-center gap-1.5 rounded-lg border border-violet-200 bg-violet-50 px-2 py-1 text-[11px] lg:px-3 lg:py-2 lg:text-sm">
                      <dt className="font-semibold text-violet-700">분위기 #평온</dt>
                      <dd className="font-extrabold text-violet-800">
                        +{tutorialSubmissionResult.damage.moodBonus.toFixed(1)}
                      </dd>
                    </div>
                    <div className="inline-flex items-center gap-1.5 rounded-lg border border-amber-200 bg-amber-50 px-2 py-1 text-[11px] lg:px-3 lg:py-2 lg:text-sm">
                      <dt className="font-semibold text-amber-700">약점 #신비</dt>
                      <dd className="font-extrabold text-amber-800">
                        +{tutorialSubmissionResult.damage.weaknessBonus.toFixed(1)}
                      </dd>
                    </div>
                  </dl>
                }
              />
            </div>
          </TutorialCoachMark>

          <TutorialCoachMark
            active={isSelected && state.attackStatus === 'idle'}
            label="문장을 제출해 공격하세요"
            className={
              showAttackDock
                ? '!fixed inset-x-3 bottom-[calc(env(safe-area-inset-bottom)+5.5rem)] z-40 mx-auto max-w-[454px] bg-white p-2 shadow-xl lg:!sticky lg:inset-x-auto lg:bottom-6 lg:mx-0 lg:max-w-none'
                : 'mt-4'
            }
            id="tutorial-coach-submit"
          >
            <Button
              size="lg"
              className="w-full"
              disabled={!isSelected}
              loading={state.attackStatus === 'loading'}
              onClick={onAttack}
            >
              문장 제출하기
            </Button>
          </TutorialCoachMark>
        </section>
      )}

      {showClearResult && (
        <section className="mt-8 motion-rise" aria-label="원정 클리어 결과">
          <BossClearResult
            bossName={tutorialBoss.name}
            clearedAt="2026-08-07T09:10:00+09:00"
            accumulatedDamage={tutorialBoss.maxHp}
            mySubmissionCount={tutorialClearSubmissions.length}
            mySubmissions={tutorialClearSubmissions}
            bookGeneration={
              <div id="tutorial-completed-book-action" ref={completedBookRef}>
                <TutorialCoachMark
                  active
                  label="완성된 공동 책으로 이동하세요"
                  id="tutorial-coach-book"
                >
                  <BookGenerationStatusContent
                    status={tutorialBookGenerationStatus}
                    onOpenBook={onNext}
                    openBookLabel="완성된 공동 책 보기"
                  />
                </TutorialCoachMark>
              </div>
            }
          />
        </section>
      )}

      {state.attackStatus !== 'complete' && (
        <details className="group mt-4 rounded-2xl border border-stone-200 bg-white px-4 py-3">
          <summary className="flex min-h-10 cursor-pointer list-none items-center justify-between gap-3 font-semibold text-stone-800">
            <span>원정 상세 정보</span>
            <span aria-hidden="true" className="text-stone-400 transition group-open:rotate-180">
              ↓
            </span>
          </summary>
          <p className="border-t border-stone-100 pt-4 text-sm leading-6 text-stone-600">
            {tutorialBoss.description}
          </p>
        </details>
      )}
    </div>
  );
}

function TutorialBookView({ onRead }: { onRead: () => void }) {
  const [liked, setLiked] = useState(false);
  const [bookmarked, setBookmarked] = useState(false);
  const contributors = tutorialBookContents
    .flatMap(({ contributor }) => (contributor ? [contributor] : []))
    .filter(
      (contributor, index, items) =>
        items.findIndex(({ userId }) => userId === contributor.userId) === index,
    );

  return (
    <article className="mx-auto max-w-5xl pb-6">
      <span className="inline-flex min-h-10 items-center gap-2 text-sm font-bold text-stone-700">
        <span aria-hidden="true">←</span> 공동 도서 정보
      </span>

      <header className="mt-5 text-center lg:mt-8">
        <div className="flex justify-center">
          <LibraryBookCover
            title="새벽의 수호자"
            imageUrl={tutorialBookCoverImageUrl}
            size="large"
          />
        </div>
        <div className="mt-5 flex flex-wrap justify-center gap-2">
          <span className="rounded-full bg-rose-50 px-3 py-1 text-xs font-bold text-rose-600">
            공동 책
          </span>
          <span className="rounded-full bg-emerald-50 px-3 py-1 text-xs font-bold text-emerald-700">
            최종 클리어
          </span>
        </div>
        <h1 className="mt-2 text-2xl font-extrabold text-stone-900">새벽의 수호자</h1>
      </header>

      <TutorialCoachMark
        active
        label="완성된 공동 소설집은 여기서 펼쳐요"
        className="mt-6"
        id="tutorial-coach-read"
      >
        <button
          type="button"
          onClick={onRead}
          className="flex min-h-14 w-full items-center justify-center rounded-2xl bg-violet-500 px-4 py-3 font-bold text-white shadow-lg shadow-violet-100 transition hover:bg-violet-600 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600"
        >
          <span aria-hidden="true">📖</span>
          <span className="ml-2">공동 소설집 전체 읽기</span>
        </button>
      </TutorialCoachMark>

      <div className="mt-5 flex flex-wrap items-center justify-center gap-2">
        <button
          type="button"
          aria-pressed={liked}
          aria-label={liked ? '좋아요 취소, 현재 13개' : '좋아요, 현재 12개'}
          onClick={() => setLiked((current) => !current)}
          className={[
            'inline-flex min-h-10 flex-1 items-center justify-center gap-1.5 rounded-xl px-2.5 text-sm font-semibold transition',
            liked
              ? 'bg-rose-50 text-rose-600'
              : 'text-stone-500 hover:bg-stone-50 hover:text-rose-500',
          ].join(' ')}
        >
          <span aria-hidden="true">{liked ? '♥' : '♡'}</span>
          <span>{liked ? 13 : 12}</span>
        </button>
        <button
          type="button"
          aria-pressed={bookmarked}
          aria-label={bookmarked ? '북마크 해제' : '북마크 추가'}
          onClick={() => setBookmarked((current) => !current)}
          className={[
            'inline-flex min-h-10 flex-1 items-center justify-center gap-1.5 rounded-xl px-2.5 text-sm font-semibold transition',
            bookmarked
              ? 'bg-violet-50 text-violet-700'
              : 'text-stone-500 hover:bg-stone-50 hover:text-violet-600',
          ].join(' ')}
        >
          <span aria-hidden="true">{bookmarked ? '★' : '☆'}</span>
          <span>{bookmarked ? '북마크됨' : '북마크'}</span>
        </button>
      </div>

      <dl className="mt-7 grid grid-cols-3 divide-x divide-stone-200 rounded-2xl border border-stone-200 bg-white px-2 py-4 text-center shadow-sm">
        <div className="px-1">
          <dt className="text-xs text-stone-500">참여 작가</dt>
          <dd className="mt-1 font-extrabold text-stone-900">{contributors.length}명</dd>
        </div>
        <div className="px-1">
          <dt className="text-xs text-stone-500">기여 문장</dt>
          <dd className="mt-1 font-extrabold text-stone-900">{tutorialBookContents.length}개</dd>
        </div>
        <div className="px-1">
          <dt className="text-xs text-stone-500">완성일</dt>
          <dd className="mt-1 font-extrabold text-violet-700">8월 7일</dd>
        </div>
      </dl>

      <div className="mt-6 grid gap-6 lg:grid-cols-[minmax(0,1.4fr)_minmax(280px,0.6fr)]">
        <section className="rounded-2xl border border-stone-200 bg-white p-5 lg:p-7">
          <h2 className="font-extrabold text-stone-900">작품 소개</h2>
          <p className="mt-3 text-sm leading-7 text-stone-600">
            고요한 새벽, 흩어진 별빛과 문장들이 모여 모두를 위한 하나의 길이 되는 이야기.
          </p>
        </section>
        <section className="rounded-2xl border border-stone-200 bg-white p-5 lg:p-7">
          <h2 className="font-extrabold text-stone-900">기여한 모험가 동료들</h2>
          <BookContributorList contributors={contributors} />
        </section>
      </div>

      <ButtonLink to="/" variant="ghost" size="lg" className="mt-3 w-full">
        튜토리얼 마치고 서비스 시작하기
      </ButtonLink>
    </article>
  );
}

function TutorialBookReaderView({
  onBack,
  onComplete,
  onReaderInteract,
}: {
  onBack: () => void;
  onComplete: () => void;
  onReaderInteract: () => void;
}) {
  const tutorialSources = tutorialBookContents
    .filter((content) => content.contributor !== null && content.contentType !== 'AI_GENERATED')
    .slice(0, 4);

  return (
    <BookReaderExperience
      title={tutorialBoss.name}
      body={tutorialBookBody}
      contents={tutorialBookContents}
      bodyPages={tutorialBookPages}
      sources={tutorialSources}
      onBack={onBack}
      turnPrompt="오른쪽 페이지를 눌러 다음 장으로 넘기기"
      completionLabel="튜토리얼 완료하기"
      onComplete={onComplete}
      onReaderInteract={onReaderInteract}
    />
  );
}

function TutorialCompletionView({
  onBack,
  onRestart,
}: {
  onBack: () => void;
  onRestart: () => void;
}) {
  return (
    <section aria-labelledby="tutorial-completion-title" className="mx-auto max-w-3xl py-4 lg:py-8">
      <div className="relative overflow-hidden rounded-[2rem] border border-violet-200 bg-gradient-to-br from-white via-violet-50 to-amber-50 px-5 py-7 text-center shadow-xl shadow-violet-100/80 min-[400px]:px-7 lg:px-12 lg:py-10">
        <div
          aria-hidden="true"
          className="absolute -top-20 -right-20 size-56 rounded-full bg-violet-200/40 blur-3xl"
        />
        <div
          aria-hidden="true"
          className="absolute -bottom-20 -left-16 size-48 rounded-full bg-amber-200/35 blur-3xl"
        />

        <div className="relative">
          <div className="mx-auto flex size-36 items-center justify-center overflow-hidden rounded-full border-4 border-white bg-violet-100 shadow-lg shadow-violet-200/60 min-[400px]:size-44">
            <img
              src="/tutorial-assets/dami-welcome.png"
              alt="튜토리얼 완료를 축하하는 다미"
              className="size-full object-cover object-left mix-blend-multiply"
            />
          </div>

          <span className="mt-5 inline-flex items-center gap-1.5 rounded-full bg-violet-600 px-3 py-1.5 text-xs font-black text-white shadow-sm">
            <span aria-hidden="true">✓</span>
            8/8 모든 단계 완료
          </span>
          <h1
            id="tutorial-completion-title"
            className="mt-3 text-2xl font-black tracking-tight text-stone-900 min-[400px]:text-3xl"
          >
            튜토리얼을 모두 마쳤어요!
          </h1>
          <p className="mx-auto mt-3 max-w-xl text-sm leading-6 text-stone-600 min-[400px]:text-base min-[400px]:leading-7">
            오늘의 단어가 한 문장이 되고, 동료들과 함께 한 권의 이야기로 완성되는 여정을 경험했어요.
            이제 실제 오늘의 단어로 첫 문장을 남겨보세요.
          </p>

          <div className="mx-auto mt-6 max-w-lg rounded-2xl bg-white/90 p-4 shadow-sm ring-1 ring-violet-100 min-[400px]:p-5">
            <p className="text-sm font-bold text-violet-800">
              로그인하면 작성한 문장과 원정 기록을 계속 이어갈 수 있어요.
            </p>
            <ButtonLink
              to="/login"
              state={{ from: '/sentences/new' }}
              size="lg"
              className="mt-4 w-full"
            >
              로그인하고 첫 문장 쓰기
              <span aria-hidden="true">→</span>
            </ButtonLink>
            <ButtonLink to="/" variant="ghost" className="mt-2 w-full">
              서비스 홈 둘러보기
            </ButtonLink>
          </div>

          <div className="mt-5 flex flex-wrap items-center justify-center gap-x-4 gap-y-1 text-xs font-semibold text-stone-500">
            <button type="button" onClick={onBack} className="min-h-9 px-2 hover:text-violet-700">
              책 다시 보기
            </button>
            <button
              type="button"
              onClick={onRestart}
              className="min-h-9 px-2 hover:text-violet-700"
            >
              튜토리얼 다시 시작
            </button>
          </div>
        </div>
      </div>
    </section>
  );
}

function getActiveNavigation(step: number) {
  if (step === TUTORIAL_COMPLETION_STEP) return '/';
  if (step <= 1) return '/';
  if (step <= 3) return '/sentences/new';
  if (step === 4) return '/boss';
  return '/library';
}

export function TutorialPage() {
  const [state, dispatch] = useReducer(tutorialReducer, initialTutorialState);
  const [evaluationGuideStep, setEvaluationGuideStep] = useState<EvaluationGuideStep>(0);
  const [isSentenceInputFocused, setSentenceInputFocused] = useState(false);
  const [bossGuideCompact, setBossGuideCompact] = useState(false);
  const [readerGuideCompact, setReaderGuideCompact] = useState(false);
  const [exitConfirmOpen, setExitConfirmOpen] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();
  const guide = state.step < TUTORIAL_COMPLETION_STEP ? guideContent[state.step] : null;
  const hasEvaluationReadySentence = state.sentence.trim().length > 0;
  const shouldGuideEvaluation = hasEvaluationReadySentence && !isSentenceInputFocused;
  const activeGuideTargetId =
    state.step === 1
      ? state.revealedWordIds.length === tutorialWords.length
        ? 'tutorial-coach-next'
        : 'tutorial-coach-words'
      : state.step === 2
        ? shouldGuideEvaluation
          ? 'tutorial-coach-evaluate'
          : 'tutorial-coach-compose'
        : state.step === 3 && state.evaluationStatus === 'complete'
          ? evaluationGuideContent[evaluationGuideStep].targetId
          : state.step === 4
            ? 'tutorial-boss-hero'
            : state.step === TUTORIAL_BOOK_STEP
              ? 'tutorial-coach-read'
              : undefined;

  useEffect(() => {
    markTutorialSeen();
  }, []);

  useEffect(() => {
    if (location.search) {
      void navigate(location.pathname, { replace: true });
    }
  }, [location.pathname, location.search, navigate]);

  useEffect(() => {
    document.documentElement.scrollTop = 0;
    document.body.scrollTop = 0;
  }, [state.step]);

  useEffect(() => {
    if (state.evaluationStatus !== 'loading') return;
    const timeoutId = window.setTimeout(
      () => dispatch({ type: 'COMPLETE_EVALUATION' }),
      EVALUATION_DELAY_MS,
    );
    return () => window.clearTimeout(timeoutId);
  }, [state.evaluationStatus]);

  useEffect(() => {
    if (state.attackStatus !== 'loading') return;
    const timeoutId = window.setTimeout(
      () => dispatch({ type: 'COMPLETE_ATTACK' }),
      ATTACK_DELAY_MS,
    );
    return () => window.clearTimeout(timeoutId);
  }, [state.attackStatus]);

  useEffect(() => {
    const targetId =
      state.step === 3 && state.evaluationStatus === 'loading'
        ? 'tutorial-evaluation-result'
        : state.step === 3 && state.evaluationStatus === 'complete'
          ? evaluationGuideContent[evaluationGuideStep].targetId
          : state.step === 4 && state.attackStatus === 'loading'
            ? 'tutorial-boss-hero'
            : null;
    if (!targetId) return;

    const timeoutId = window.setTimeout(() => {
      const target = document.getElementById(targetId);
      if (typeof target?.scrollIntoView === 'function') {
        target.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    }, 0);

    return () => window.clearTimeout(timeoutId);
  }, [evaluationGuideStep, state.attackStatus, state.evaluationStatus, state.step]);

  useEffect(() => {
    if (
      typeof window.matchMedia === 'function' &&
      !window.matchMedia('(max-width: 1023px)').matches
    ) {
      return;
    }

    const targetId =
      state.step === 1
        ? state.revealedWordIds.length === tutorialWords.length
          ? 'tutorial-coach-next'
          : 'tutorial-coach-words'
        : state.step === 2
          ? shouldGuideEvaluation
            ? 'tutorial-coach-evaluate'
            : 'tutorial-coach-compose'
          : state.step === TUTORIAL_BOOK_STEP
            ? 'tutorial-coach-read'
            : null;

    if (!targetId) return;

    const timeoutId = window.setTimeout(() => {
      const target = document.getElementById(targetId);
      if (typeof target?.scrollIntoView === 'function') {
        target.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    }, 80);

    return () => window.clearTimeout(timeoutId);
  }, [shouldGuideEvaluation, state.revealedWordIds.length, state.step]);

  const goToEvaluation = () => {
    setSentenceInputFocused(false);
    setEvaluationGuideStep(0);
    dispatch({ type: 'GO_TO_STEP', step: 3 });
    dispatch({ type: 'START_EVALUATION' });
  };

  const advanceEvaluationGuide = () => {
    setEvaluationGuideStep((step) => Math.min(2, step + 1) as EvaluationGuideStep);
  };

  const goBack = () => {
    setSentenceInputFocused(false);
    setEvaluationGuideStep(0);
    setBossGuideCompact(false);
    setReaderGuideCompact(false);
    dispatch({ type: 'BACK' });
  };

  const resetTutorial = () => {
    setSentenceInputFocused(false);
    setEvaluationGuideStep(0);
    setBossGuideCompact(false);
    setReaderGuideCompact(false);
    dispatch({ type: 'RESET' });
  };

  const exitTutorial = () => {
    setExitConfirmOpen(false);
    void navigate('/');
  };

  const guideAction = (() => {
    if (state.step === 0)
      return { label: '튜토리얼 시작', disabled: false, action: () => dispatch({ type: 'START' }) };
    return null;
  })();

  return (
    <ServiceShell
      sessionControl={<TutorialSessionControl />}
      activeNavigationTo={getActiveNavigation(state.step)}
      readerMode={state.step === TUTORIAL_READER_STEP}
      overlay={
        <>
          {guide && (
            <DamiGuide
              key={`${state.step}-${bossGuideCompact ? 'boss-compact' : 'boss-expanded'}-${readerGuideCompact ? 'reader-compact' : 'reader-expanded'}`}
              step={state.step}
              title={guide.title}
              description={guide.description}
              primaryLabel={guideAction?.label}
              primaryDisabled={guideAction?.disabled}
              onPrimary={guideAction?.action}
              onBack={state.step > 0 ? goBack : undefined}
              onReset={resetTutorial}
              onExit={() => setExitConfirmOpen(true)}
              compact={
                state.step === 3 || (state.step === 4 && bossGuideCompact) || readerGuideCompact
              }
              lifted={
                state.step === 4 &&
                state.selectedSentenceVersionId !== null &&
                state.attackStatus !== 'complete'
              }
              lowered={state.step === TUTORIAL_BOOK_STEP}
              backdrop={state.step === 0 || state.step === TUTORIAL_READER_STEP}
              targetId={activeGuideTargetId}
              placementOverride={state.step === TUTORIAL_BOOK_STEP ? 'bottom' : undefined}
            />
          )}
          <Modal
            open={exitConfirmOpen}
            title="튜토리얼을 나갈까요?"
            onClose={() => setExitConfirmOpen(false)}
            footer={
              <>
                <Button variant="secondary" onClick={() => setExitConfirmOpen(false)}>
                  계속하기
                </Button>
                <Button variant="danger" onClick={exitTutorial}>
                  나가기
                </Button>
              </>
            }
          >
            <p className="text-sm leading-6 text-stone-600">
              지금까지의 튜토리얼 진행 내용은 저장되지 않아요.
            </p>
          </Modal>
        </>
      }
    >
      <div
        data-testid="tutorial-stage"
        className={
          state.step === TUTORIAL_READER_STEP
            ? 'h-full min-h-0'
            : state.step === TUTORIAL_COMPLETION_STEP
              ? 'pb-10'
              : 'pb-24 lg:pb-10'
        }
      >
        {state.step <= 1 && (
          <TutorialHomeView
            state={state}
            onReveal={(wordId) => dispatch({ type: 'REVEAL_WORD', wordId })}
            onNext={() => dispatch({ type: 'GO_TO_STEP', step: 2 })}
          />
        )}
        {(state.step === 2 || state.step === 3) && (
          <TutorialSentenceView
            state={state}
            evaluationGuideStep={evaluationGuideStep}
            isSentenceInputFocused={isSentenceInputFocused}
            onReveal={(wordId) => dispatch({ type: 'REVEAL_WORD', wordId })}
            onSentenceChange={(sentence) => dispatch({ type: 'SET_SENTENCE', sentence })}
            onSentenceFocusChange={setSentenceInputFocused}
            onFillExample={() => {
              setSentenceInputFocused(false);
              dispatch({ type: 'FILL_EXAMPLE' });
            }}
            onEvaluate={goToEvaluation}
            onAdvanceEvaluationGuide={advanceEvaluationGuide}
            onNext={() => dispatch({ type: 'GO_TO_STEP', step: 4 })}
          />
        )}
        {state.step === 4 && (
          <TutorialBossView
            state={state}
            onSelectSentence={(sentenceVersionId) =>
              dispatch({ type: 'SELECT_SENTENCE', sentenceVersionId })
            }
            onAttack={() => dispatch({ type: 'START_ATTACK' })}
            onGuideInteract={() => setBossGuideCompact(true)}
            onNext={() => dispatch({ type: 'GO_TO_STEP', step: TUTORIAL_BOOK_STEP })}
          />
        )}
        {state.step === TUTORIAL_BOOK_STEP && (
          <TutorialBookView
            onRead={() => dispatch({ type: 'GO_TO_STEP', step: TUTORIAL_READER_STEP })}
          />
        )}
        {state.step === TUTORIAL_READER_STEP && (
          <TutorialBookReaderView
            onBack={goBack}
            onComplete={() => dispatch({ type: 'GO_TO_STEP', step: TUTORIAL_COMPLETION_STEP })}
            onReaderInteract={() => setReaderGuideCompact(true)}
          />
        )}
        {state.step === TUTORIAL_COMPLETION_STEP && (
          <TutorialCompletionView
            onBack={() => {
              setReaderGuideCompact(false);
              dispatch({ type: 'GO_TO_STEP', step: TUTORIAL_READER_STEP });
            }}
            onRestart={resetTutorial}
          />
        )}
      </div>
    </ServiceShell>
  );
}
