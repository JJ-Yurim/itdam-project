import { HttpResponse, http } from 'msw';

import type {
  BossRunResponse,
  BossRunSummaryResponse,
  BossSubmissionRequest,
  BossSubmissionResponse,
  EligibleSentenceResponse,
} from '@/features/boss';

const activeBossRun: BossRunResponse = {
  bossRunId: 7,
  bossId: 3,
  bossName: '새벽의 수호자',
  bossDescription: '새벽이 끝나기 전에 마음을 모으세요.',
  status: 'ACTIVE',
  startedAt: '2026-07-31T00:00:00Z',
  endsAt: '2026-08-01T00:00:00Z',
  clearedAt: null,
  accumulatedDamage: 380,
  currentPhase: {
    phaseId: 21,
    phaseNo: 1,
    maxHp: 1000,
    remainingHp: 620,
    progressPercent: 38,
    hardMinScore: 20,
    recommendedScore: 35,
    damageMultiplier: 1.25,
    imageUrl: '/images/boss-dawn-guardian.webp',
    status: 'ACTIVE',
  },
  moods: [
    { code: 'CALM', displayName: '평온', role: 'THEME', weight: 1 },
    { code: 'JOY', displayName: '기쁨', role: 'WEAKNESS', weight: 1.2 },
  ],
  mySubmissionCount: 1,
  remainingSubmissionCount: 2,
  mySubmissions: [
    {
      submissionId: 55,
      bossRunId: 7,
      bossName: '새벽의 수호자',
      bossPhaseId: 21,
      sentenceVersionId: 84,
      content: '달빛이 소담하게 내리는 골목에서 은하수 한 조각을 발견했다.',
      finalDamage: 57,
      submittedAt: '2026-07-31T01:30:00Z',
    },
  ],
};

const scheduledBossRun: BossRunResponse = {
  bossRunId: 8,
  bossId: 4,
  bossName: '기억의 파수꾼',
  bossDescription: '다가오는 전투를 준비하세요.',
  status: 'SCHEDULED',
  startedAt: '2026-08-02T00:00:00Z',
  endsAt: '2026-08-03T00:00:00Z',
  clearedAt: null,
  accumulatedDamage: 0,
  currentPhase: {
    phaseId: 24,
    phaseNo: 1,
    maxHp: 1200,
    remainingHp: 1200,
    progressPercent: 0,
    hardMinScore: 25,
    recommendedScore: 40,
    damageMultiplier: 1,
    imageUrl: '/images/boss-memory-keeper.webp',
    status: 'WAITING',
  },
  moods: [{ code: 'MYSTERY', displayName: '신비', role: 'THEME', weight: 1 }],
  mySubmissionCount: 0,
  remainingSubmissionCount: 3,
  mySubmissions: [],
};

const bossRuns: BossRunResponse[] = [activeBossRun, scheduledBossRun];
const eligibleSentences: EligibleSentenceResponse[] = [
  {
    sentenceVersionId: 84,
    sentenceId: 42,
    versionNo: 2,
    content: '햇빛이 창가에 부드럽게 번졌다.',
    baseScore: 39,
    wordBonus: 0,
    gameBaseAttack: 39,
    sentenceEnergy: 55,
  },
  {
    sentenceVersionId: 91,
    sentenceId: 47,
    versionNo: 1,
    content: '고요한 바람이 숲의 이야기를 전해 주었다.',
    baseScore: 42,
    wordBonus: 0,
    gameBaseAttack: 42,
    sentenceEnergy: 42,
  },
];

const submittedSentenceVersionIds = new Set<number>();

/**
 * 목록 계약에는 보스 설명과 제출 이력이 없다. 목이 상세와 같은 모양을 내려주면 화면이
 * 실제로는 없는 필드를 읽고도 테스트가 통과해 버린다.
 */
function toSummary(bossRun: BossRunResponse): BossRunSummaryResponse {
  const { bossDescription, mySubmissions, ...summary } = bossRun;
  void bossDescription;
  void mySubmissions;

  return summary;
}

export function resetBossMockState() {
  submittedSentenceVersionIds.clear();
}

const myBossRuns = [
  {
    bossRunId: 7,
    bossId: 3,
    bossName: '새벽의 수호자',
    status: 'CLEARED',
    currentPhaseNo: 3,
    startedAt: '2026-07-31T00:00:00Z',
    endsAt: '2026-08-01T00:00:00Z',
    clearedAt: '2026-07-31T12:00:00Z',
    lastSubmittedAt: '2026-07-31T01:30:00Z',
    bookId: 18,
    bookStatus: 'COMPLETED',
  },
  {
    bossRunId: 5,
    bossId: 2,
    bossName: '잿빛 문지기',
    status: 'ENDED_UNCLEARED',
    currentPhaseNo: 2,
    startedAt: '2026-07-27T00:00:00Z',
    endsAt: '2026-07-28T00:00:00Z',
    clearedAt: null,
    lastSubmittedAt: '2026-07-27T20:10:00Z',
    bookId: null,
    bookStatus: null,
  },
  // 홈의 참여중인 레이드(status=ACTIVE) 개발용. 배열 끝에 둬 기존 순서 기반 테스트에 영향을 주지 않는다.
  {
    bossRunId: 11,
    bossId: 4,
    bossName: '심연의 기록자',
    status: 'ACTIVE',
    currentPhaseNo: 1,
    startedAt: '2026-08-03T00:00:00Z',
    endsAt: '2026-12-31T00:00:00Z',
    clearedAt: null,
    lastSubmittedAt: '2026-08-03T09:00:00Z',
    bookId: null,
    bookStatus: null,
  },
];

export const bossHandlers = [
  http.get('*/api/v1/me/boss-runs', ({ request }) => {
    const url = new URL(request.url);
    const status = url.searchParams.get('status');
    const page = Number(url.searchParams.get('page') ?? 0);
    const size = Number(url.searchParams.get('size') ?? 20);
    const filtered = status ? myBossRuns.filter((run) => run.status === status) : myBossRuns;
    const start = page * size;
    const content = filtered.slice(start, start + size);

    return HttpResponse.json({
      content,
      page,
      size,
      totalElements: filtered.length,
      totalPages: Math.ceil(filtered.length / size),
      hasNext: start + size < filtered.length,
    });
  }),
  http.post('*/api/v1/boss-runs/:bossRunId/submissions', async ({ params, request }) => {
    const bossRunId = Number(params.bossRunId);
    const bossRun = bossRuns.find((item) => item.bossRunId === bossRunId);
    const body = (await request.json()) as BossSubmissionRequest;

    if (!bossRun || bossRun.status !== 'ACTIVE') {
      return HttpResponse.json(
        {
          occurredAt: new Date().toISOString(),
          code: bossRun ? 'CONFLICT' : 'NOT_FOUND',
          message: bossRun
            ? '진행 중인 보스전에만 문장을 제출할 수 있습니다.'
            : '보스전을 찾을 수 없습니다.',
          fieldErrors: [],
        },
        { status: bossRun ? 409 : 404 },
      );
    }

    if (!body.submissionConsentAccepted) {
      return HttpResponse.json(
        {
          occurredAt: new Date().toISOString(),
          code: 'INVALID_REQUEST',
          message: '공동 책 이용동의가 필요합니다.',
          fieldErrors: [],
        },
        { status: 400 },
      );
    }

    const isEligibleSentence = eligibleSentences.some(
      (sentence) => sentence.sentenceVersionId === body.sentenceVersionId,
    );

    if (!isEligibleSentence || submittedSentenceVersionIds.has(body.sentenceVersionId)) {
      return HttpResponse.json(
        {
          occurredAt: new Date().toISOString(),
          code: 'NOT_FOUND',
          message: '제출 가능한 문장을 찾을 수 없습니다.',
          fieldErrors: [],
        },
        { status: 404 },
      );
    }

    submittedSentenceVersionIds.add(body.sentenceVersionId);

    const response: BossSubmissionResponse = {
      submissionId: 56,
      bossRunId,
      bossPhaseId: 21,
      serviceDate: '2026-08-04',
      damage: {
        aiBaseScore: 39,
        wordBonus: 0,
        moodBonus: 2,
        weaknessBonus: 3,
        phaseModifier: 1.25,
        finalDamage: 55,
      },
      phase: {
        phaseNo: 1,
        remainingHp: 565,
        status: 'ACTIVE',
      },
      bossRunStatus: 'ACTIVE',
    };

    return HttpResponse.json(response, { status: 201 });
  }),

  http.get('*/api/v1/boss-runs', ({ request }) => {
    const url = new URL(request.url);
    // status는 반복 파라미터다. "종료" 탭 하나가 세 상태에 걸쳐 있어 여러 번 실려 온다.
    const statuses = url.searchParams.getAll('status');
    const page = Number(url.searchParams.get('page') ?? '0');
    const size = Number(url.searchParams.get('size') ?? '20');

    const filteredBossRuns = statuses.length
      ? bossRuns.filter((bossRun) => statuses.includes(bossRun.status))
      : bossRuns;

    const startIndex = page * size;
    const endIndex = startIndex + size;
    const content = filteredBossRuns.slice(startIndex, endIndex).map(toSummary);
    const totalElements = filteredBossRuns.length;
    const totalPages = Math.ceil(totalElements / size);

    return HttpResponse.json({
      content,
      page,
      size,
      totalElements,
      totalPages,
      hasNext: endIndex < totalElements,
    });
  }),

  http.get('*/api/v1/boss-runs/:bossRunId/eligible-sentences', ({ params, request }) => {
    const bossRunId = Number(params.bossRunId);
    const bossRun = bossRuns.find((item) => item.bossRunId === bossRunId);

    if (!bossRun) {
      return HttpResponse.json(
        {
          occurredAt: new Date().toISOString(),
          code: 'NOT_FOUND',
          message: '보스전을 찾을 수 없습니다.',
          fieldErrors: [],
        },
        {
          status: 404,
        },
      );
    }

    if (bossRun.status !== 'ACTIVE') {
      return HttpResponse.json(
        {
          occurredAt: new Date().toISOString(),
          code: 'CONFLICT',
          message: '진행 중인 보스전에서만 제출 가능한 문장을 조회할 수 있습니다.',
          fieldErrors: [],
        },
        {
          status: 409,
        },
      );
    }

    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') ?? '0');
    const size = Number(url.searchParams.get('size') ?? '20');

    const startIndex = page * size;
    const endIndex = startIndex + size;
    const availableSentences = eligibleSentences
      .filter((sentence) => !submittedSentenceVersionIds.has(sentence.sentenceVersionId))
      .sort((left, right) => right.sentenceEnergy - left.sentenceEnergy);
    const content = availableSentences.slice(startIndex, endIndex);
    const totalElements = availableSentences.length;
    const totalPages = Math.ceil(totalElements / size);

    return HttpResponse.json({
      content,
      page,
      size,
      totalElements,
      totalPages,
      hasNext: endIndex < totalElements,
    });
  }),

  http.get('*/api/v1/boss-runs/:bossRunId/leaderboard', ({ params }) =>
    HttpResponse.json({
      bossRunId: Number(params.bossRunId),
      entries: [
        { rank: 1, userId: 11, nickname: '문장수집가', totalDamage: 380 },
        { rank: 2, userId: 12, nickname: '달빛서기', totalDamage: 240 },
      ],
      me: { rank: 5, totalDamage: 57 },
    }),
  ),

  http.get('*/api/v1/boss-runs/:bossRunId', ({ params }) => {
    const bossRunId = Number(params.bossRunId);
    const bossRun = bossRuns.find((item) => item.bossRunId === bossRunId);

    if (!bossRun) {
      return HttpResponse.json(
        {
          occurredAt: new Date().toISOString(),
          code: 'NOT_FOUND',
          message: '보스전을 찾을 수 없습니다.',
          fieldErrors: [],
        },
        { status: 404 },
      );
    }

    return HttpResponse.json(bossRun);
  }),
];
