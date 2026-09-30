import type { DailyWord } from '@/features/daily-words';
import type {
  BossSubmissionResponse,
  BossSubmissionSummary,
  EligibleSentenceResponse,
} from '@/features/boss';
import type { BookContentResponse } from '@/features/library';
import type { SentenceEvaluationResponse } from '@/features/sentences';

export const tutorialBossImageUrl = '/tutorial-assets/tutorial-boss-mountain-phase-1.png';
export const tutorialBookCoverImageUrl = '/tutorial-assets/tutorial-book-cover-mountain.png';

export const tutorialWords: DailyWord[] = [
  {
    position: 1,
    wordId: 101,
    word: '은하수',
    partOfSpeech: '명사',
    definition: '맑은 밤하늘에 강처럼 보이는 수많은 별의 무리',
    example: '여름밤 은하수가 우리 머리 위로 길게 흘렀다.',
  },
  {
    position: 2,
    wordId: 102,
    word: '산들바람',
    partOfSpeech: '명사',
    definition: '시원하고 가볍게 부는 바람',
    example: '산들바람이 열린 창문으로 조용히 들어왔다.',
  },
  {
    position: 3,
    wordId: 103,
    word: '윤슬',
    partOfSpeech: '명사',
    definition: '햇빛이나 달빛에 비치어 반짝이는 잔물결',
    example: '호수 위 윤슬이 보석처럼 반짝였다.',
  },
  {
    position: 4,
    wordId: 104,
    word: '포근하다',
    partOfSpeech: '형용사',
    definition: '감싸 주는 것처럼 따뜻하고 편안하다',
    example: '햇살이 비치는 방 안은 포근했다.',
  },
  {
    position: 5,
    wordId: 105,
    word: '마중',
    partOfSpeech: '명사',
    definition: '오는 사람을 나가서 맞이하는 일',
    example: '강아지가 꼬리를 흔들며 나를 마중 나왔다.',
  },
];

export const tutorialExampleSentence = '고요한 골목에 은하수와 윤슬이 번졌다.';

export const tutorialEvaluation: SentenceEvaluationResponse = {
  sentenceVersionId: 8400,
  evaluationStatus: 'EVALUATED',
  safety: { status: 'PASSED', reason: null },
  scores: {
    completeness: 82,
    originality: 76,
    expression: 88,
    imagery: 91,
    emotionalImpact: 84,
    baseScore: 83.35,
  },
  wordBonus: 5,
  gameBaseAttack: 88.35,
  overallFeedback: '이미지가 선명하고 문장의 흐름이 자연스러워요.',
  moods: [
    { code: 'CALM', strength: 0.82, rank: 1 },
    { code: 'MYSTERY', strength: 0.64, rank: 2 },
  ],
  usedDailyWords: [
    { dailyWordSetItemId: 15, wordId: 101, word: '은하수' },
    { dailyWordSetItemId: 16, wordId: 103, word: '윤슬' },
  ],
  modelName: 'tutorial-model',
  promptVersion: 'tutorial-v1',
  rubricVersion: 'v1',
  evaluatedAt: '2026-08-07T09:00:00+09:00',
};

export const tutorialBoss = {
  name: '새벽의 수호자',
  description: '새벽이 끝나기 전에 모두의 문장으로 마음을 밝혀 주세요.',
  maxHp: 1_000,
  hpBeforeAttack: 137,
  hpAfterAttack: 0,
  damage: 137,
  hardMinScore: 35,
  recommendedScore: 50,
  theme: '평온',
  themeWeight: 1,
  weakness: '신비',
  weaknessWeight: 1,
} as const;

export const tutorialEligibleSentence: EligibleSentenceResponse = {
  sentenceVersionId: 8400,
  sentenceId: 4200,
  versionNo: 1,
  content: tutorialExampleSentence,
  baseScore: 83.35,
  wordBonus: 5,
  gameBaseAttack: 88.35,
  sentenceEnergy: tutorialBoss.damage,
};

export const tutorialSubmissionResult: BossSubmissionResponse = {
  submissionId: 5600,
  bossRunId: 700,
  bossPhaseId: 2100,
  serviceDate: '2026-08-07',
  damage: {
    aiBaseScore: 83.35,
    wordBonus: 5,
    moodBonus: 8.2,
    weaknessBonus: 12.8,
    phaseModifier: 1.25,
    finalDamage: tutorialBoss.damage,
  },
  phase: {
    phaseNo: 1,
    remainingHp: tutorialBoss.hpAfterAttack,
    status: 'CLEARED',
  },
  bossRunStatus: 'CLEARED',
};

export const tutorialClearSubmissions: BossSubmissionSummary[] = [
  {
    submissionId: tutorialSubmissionResult.submissionId,
    bossRunId: tutorialSubmissionResult.bossRunId,
    bossName: tutorialBoss.name,
    bossPhaseId: tutorialSubmissionResult.bossPhaseId,
    sentenceVersionId: tutorialEligibleSentence.sentenceVersionId,
    content: tutorialExampleSentence,
    finalDamage: tutorialBoss.damage,
    submittedAt: '2026-08-07T09:10:00+09:00',
  },
];

export const tutorialBookContents: BookContentResponse[] = [
  {
    contentId: 101,
    sequenceNo: 1,
    contentType: 'USER_ORIGINAL',
    content: tutorialExampleSentence,
    contributor: { userId: 1, nickname: '나' },
    sourceSubmissionId: 5600,
    contribution: {
      originalContent: null,
      finalDamage: tutorialBoss.damage,
      moods: tutorialEvaluation.moods,
      usedDailyWords: tutorialEvaluation.usedDailyWords.map((word) => word.word),
    },
  },
  {
    contentId: 102,
    sequenceNo: 2,
    contentType: 'USER_EDITED',
    content: '수호자는 누구의 이야기도 길을 잃지 않도록 골목 끝에서 흩어진 빛을 하나씩 모았다.',
    contributor: { userId: 2, nickname: '새벽작가' },
    sourceSubmissionId: 5601,
    contribution: {
      originalContent: '수호자는 골목 끝에서 흩어진 빛을 모았다.',
      finalDamage: 126,
      moods: [{ code: 'CALM', strength: 0.78, rank: 1 }],
      usedDailyWords: [],
    },
  },
  {
    contentId: 103,
    sequenceNo: 3,
    contentType: 'USER_EDITED',
    content: '산들바람이 문 사이로 스며들어 흩어진 문장을 한 줄로 이었다.',
    contributor: { userId: 3, nickname: '달빛서기' },
    sourceSubmissionId: 5602,
    contribution: {
      originalContent: '산들바람이 흩어진 문장을 한 줄로 이었다.',
      finalDamage: 118,
      moods: [
        { code: 'CALM', strength: 0.74, rank: 1 },
        { code: 'HOPE', strength: 0.58, rank: 2 },
      ],
      usedDailyWords: ['산들바람'],
    },
  },
  {
    contentId: 104,
    sequenceNo: 4,
    contentType: 'USER_EDITED',
    content: '별빛여우가 가장 작은 빛도 놓치지 않고 품에 안자 그림자는 한 걸음씩 물러났다.',
    contributor: { userId: 4, nickname: '별빛여우' },
    sourceSubmissionId: 5603,
    contribution: {
      originalContent: '별빛여우가 가장 작은 빛을 품에 안았다.',
      finalDamage: 132,
      moods: [{ code: 'HOPE', strength: 0.84, rank: 1 }],
      usedDailyWords: [],
    },
  },
  {
    contentId: 105,
    sequenceNo: 5,
    contentType: 'USER_EDITED',
    content:
      '함께 읽는 목소리가 커질수록 골목의 창문이 차례로 열리고 문 너머에서 아침빛이 길게 스며들었다.',
    contributor: { userId: 5, nickname: '문장수집가' },
    sourceSubmissionId: 5604,
    contribution: {
      originalContent: '함께 읽는 목소리에 골목의 창문이 열렸다.',
      finalDamage: 129,
      moods: [
        { code: 'HOPE', strength: 0.8, rank: 1 },
        { code: 'CALM', strength: 0.6, rank: 2 },
      ],
      usedDailyWords: [],
    },
  },
  {
    contentId: 106,
    sequenceNo: 6,
    contentType: 'AI_GENERATED',
    content:
      '사람들은 다음 새벽에도 이곳에 모여 자신의 하루를 한 줄씩 보태기로 약속했고, 빈 책의 마지막 여백은 다음 목소리를 기다렸다.',
    contributor: null,
    sourceSubmissionId: null,
    contribution: null,
  },
];

/** 튜토리얼에서 책 넘김을 짧게 체험할 수 있도록 압축한 세 장 분량의 정적 본문. */
export const tutorialBookPages = [
  `첫 번째 장 · 별빛을 모으는 밤

고요한 골목에 은하수와 윤슬이 번졌다. 못다 한 말들이 작은 불빛으로 떠올랐다. 수호자는 누구의 이야기도 길을 잃지 않도록 골목 끝에서 흩어진 빛을 하나씩 모았다.

새벽작가는 오래된 창가에 불빛을 내려놓고, 달빛서기는 희미한 목소리를 노트에 옮겼다. 서로 다른 기억이 별자리처럼 이어지자 닫혀 있던 문 위에 따뜻한 문장이 천천히 나타났다.`,
  `두 번째 장 · 문장이 연 문

산들바람이 문 사이로 스며들어 흩어진 문장을 한 줄로 이었다. 이야기는 오래 잠긴 문을 여는 열쇠가 되었고, 사람들은 각자 품고 있던 단어를 조심스럽게 그 문장 끝에 보탰다.

별빛여우가 가장 작은 빛도 놓치지 않고 품에 안자 그림자는 한 걸음씩 물러났다. 함께 읽는 목소리가 커질수록 골목의 창문이 차례로 열리고 문 너머에서 아침빛이 길게 스며들었다.`,
  `세 번째 장 · 우리가 만든 아침

마지막 문장이 소리 내어 읽히자 거대한 그림자는 부드러운 별가루가 되어 밤하늘로 흩어졌다. 수호자는 모아 둔 빛을 길 위에 뿌렸고, 사람들은 서로의 얼굴을 처음처럼 또렷하게 바라보았다.

문 너머로 흘러든 아침은 골목마다 새로운 이야기를 깨웠다. 사람들은 다음 새벽에도 이곳에 모여 자신의 하루를 한 줄씩 보태기로 약속했고, 빈 책의 마지막 여백은 다음 목소리를 기다렸다.`,
] as const;

export const tutorialBookBody = tutorialBookPages.join('\n\n');
