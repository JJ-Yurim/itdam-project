import { describe, expect, it } from 'vitest';

import { getBossRun, getBossRuns, getEligibleSentences, submitBossSentence } from './api';

describe('submitBossSentence', () => {
  it('동의한 문장을 제출하고 데미지 결과를 받는다', async () => {
    const result = await submitBossSentence(7, {
      sentenceVersionId: 84,
      submissionConsentAccepted: true,
    });

    expect(result).toMatchObject({
      submissionId: 56,
      bossRunId: 7,
      bossPhaseId: 21,
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
    });
  });

  it('진행 중이 아닌 레이드에는 문장을 제출할 수 없다', async () => {
    await expect(
      submitBossSentence(8, {
        sentenceVersionId: 84,
        submissionConsentAccepted: true,
      }),
    ).rejects.toMatchObject({
      response: {
        status: 409,
        data: { code: 'CONFLICT' },
      },
    });
  });

  it('제출 가능한 목록에 없는 문장이면 404 오류를 전달한다', async () => {
    await expect(
      submitBossSentence(7, {
        sentenceVersionId: 999,
        submissionConsentAccepted: true,
      }),
    ).rejects.toMatchObject({
      response: {
        status: 404,
        data: { code: 'NOT_FOUND' },
      },
    });
  });
});

describe('getBossRuns', () => {
  it('ACTIVE 상태의 보스전 목록을 조회한다', async () => {
    const result = await getBossRuns({
      status: ['ACTIVE'],
      page: 0,
      size: 20,
    });

    expect(result.content).toHaveLength(1);
    expect(result.content[0]?.bossRunId).toBe(7);
    expect(result.content[0]?.status).toBe('ACTIVE');

    expect(result.page).toBe(0);
    expect(result.size).toBe(20);
    expect(result.totalElements).toBe(1);
    expect(result.totalPages).toBe(1);
    expect(result.hasNext).toBe(false);
  });
});

describe('getEligibleSentences', () => {
  it('ACTIVE 보스전에 제출 가능한 문장 목록을 조회한다', async () => {
    const result = await getEligibleSentences(7, {
      page: 0,
      size: 1,
    });

    expect(result.content).toHaveLength(1);

    expect(result.content[0]).toMatchObject({
      sentenceVersionId: 84,
      sentenceId: 42,
      versionNo: 2,
      content: '햇빛이 창가에 부드럽게 번졌다.',
      baseScore: 39,
      wordBonus: 0,
      gameBaseAttack: 39,
      sentenceEnergy: 55,
    });

    expect(result.page).toBe(0);
    expect(result.size).toBe(1);
    expect(result.totalElements).toBe(2);
    expect(result.totalPages).toBe(2);
    expect(result.hasNext).toBe(true);
  });

  it('ACTIVE 상태가 아닌 보스전이면 409 오류를 전달한다', async () => {
    await expect(
      getEligibleSentences(8, {
        page: 0,
        size: 20,
      }),
    ).rejects.toMatchObject({
      response: {
        status: 409,
        data: {
          code: 'CONFLICT',
          message: '진행 중인 보스전에서만 제출 가능한 문장을 조회할 수 있습니다.',
          fieldErrors: [],
        },
      },
    });
  });

  it('존재하지 않는 보스전이면 404 오류를 전달한다', async () => {
    await expect(
      getEligibleSentences(999, {
        page: 0,
        size: 20,
      }),
    ).rejects.toMatchObject({
      response: {
        status: 404,
        data: {
          code: 'NOT_FOUND',
          message: '보스전을 찾을 수 없습니다.',
          fieldErrors: [],
        },
      },
    });
  });
});

describe('getBossRun', () => {
  it('보스전 ID로 상세 정보를 조회한다', async () => {
    const result = await getBossRun(7);

    expect(result.bossRunId).toBe(7);
    expect(result.bossName).toBe('새벽의 수호자');
    expect(result.status).toBe('ACTIVE');

    expect(result.currentPhase.phaseNo).toBe(1);
    expect(result.currentPhase.maxHp).toBe(1000);
    expect(result.currentPhase.remainingHp).toBe(620);
    expect(result.currentPhase.progressPercent).toBe(38);

    expect(result.mySubmissionCount).toBe(1);
    expect(result.remainingSubmissionCount).toBe(2);
  });

  it('존재하지 않는 보스전이면 404 오류를 전달한다', async () => {
    await expect(getBossRun(999)).rejects.toMatchObject({
      response: {
        status: 404,
        data: {
          code: 'NOT_FOUND',
          message: '보스전을 찾을 수 없습니다.',
          fieldErrors: [],
        },
      },
    });
  });
});
