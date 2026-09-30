import { describe, expect, it } from 'vitest';

import {
  tutorialBookBody,
  tutorialBookCoverImageUrl,
  tutorialBookContents,
  tutorialBookPages,
  tutorialBoss,
  tutorialBossImageUrl,
  tutorialEvaluation,
  tutorialSubmissionResult,
} from './data';

const MAX_TUTORIAL_BOOK_PAGE_CHARACTERS = 300;
const MIN_TUTORIAL_BOOK_BODY_CHARACTERS = 180;
const MAX_TUTORIAL_BOOK_BODY_CHARACTERS = 220;

describe('튜토리얼 공격 데이터', () => {
  it('튜토리얼 전용 보스와 책 표지 이미지 경로를 제공한다', () => {
    expect(tutorialBossImageUrl).toBe('/tutorial-assets/tutorial-boss-mountain-phase-1.png');
    expect(tutorialBookCoverImageUrl).toBe(
      '/tutorial-assets/tutorial-book-cover-mountain.png',
    );
  });

  it('화면에 표시하는 구성 요소와 배율이 최종 피해량과 일치한다', () => {
    const damage = tutorialSubmissionResult.damage;
    const componentTotal =
      damage.aiBaseScore + damage.wordBonus + damage.moodBonus + damage.weaknessBonus;

    expect(Math.round(componentTotal * damage.phaseModifier)).toBe(damage.finalDamage);
    expect(damage.moodBonus).toBeCloseTo(
      tutorialEvaluation.moods[0].strength * tutorialBoss.themeWeight * 10,
    );
    expect(damage.weaknessBonus).toBeCloseTo(
      tutorialEvaluation.moods[1].strength * tutorialBoss.weaknessWeight * 20,
    );
    expect(tutorialBoss.hpBeforeAttack - tutorialBoss.hpAfterAttack).toBe(damage.finalDamage);
    expect(tutorialBoss.hpAfterAttack).toBe(0);
    expect(tutorialSubmissionResult.bossRunStatus).toBe('CLEARED');
  });

  it('실제 평가 정책과 같은 가중치와 단어 보너스로 기본 공격력을 계산한다', () => {
    const scores = tutorialEvaluation.scores;
    expect(scores).toBeDefined();
    if (!scores) return;

    const weightedBaseScore =
      scores.completeness * 0.25 +
      scores.originality * 0.25 +
      scores.expression * 0.2 +
      scores.imagery * 0.15 +
      scores.emotionalImpact * 0.15;
    const wordBonus = tutorialEvaluation.wordBonus ?? 0;

    expect(scores.baseScore).toBeCloseTo(weightedBaseScore, 2);
    expect(wordBonus).toBe(5);
    expect(tutorialEvaluation.gameBaseAttack).toBeCloseTo(scores.baseScore + wordBonus, 2);
  });

  it('전체 읽기 화면에 세 장의 충분한 분량을 제공한다', () => {
    expect(tutorialBookPages).toHaveLength(3);
    expect(tutorialBookBody).toContain('첫 번째 장 · 별빛을 모으는 밤');
    expect(tutorialBookBody).toContain('세 번째 장 · 우리가 만든 아침');
    tutorialBookPages.forEach((page) => {
      const [title, ...bodyParagraphs] = page.split(/\n\s*\n/);
      expect(title).toMatch(/장 ·/);
      expect(bodyParagraphs).toHaveLength(2);
      expect(bodyParagraphs.join('').length).toBeGreaterThanOrEqual(
        MIN_TUTORIAL_BOOK_BODY_CHARACTERS,
      );
      expect(bodyParagraphs.join('').length).toBeLessThanOrEqual(MAX_TUTORIAL_BOOK_BODY_CHARACTERS);
      expect(page.length).toBeLessThanOrEqual(MAX_TUTORIAL_BOOK_PAGE_CHARACTERS);
    });
  });

  it('기여 문장을 실제 책 본문에서 한 번씩 순서대로 찾을 수 있다', () => {
    const contentOffsets = tutorialBookContents.map(({ content }) =>
      tutorialBookBody.indexOf(content),
    );

    expect(contentOffsets.every((offset) => offset >= 0)).toBe(true);
    expect(contentOffsets).toEqual([...contentOffsets].sort((left, right) => left - right));
    tutorialBookContents.forEach(({ content }) => {
      expect(tutorialBookBody.split(content)).toHaveLength(2);
    });
    expect(tutorialBookContents.map(({ contentType }) => contentType)).toEqual([
      'USER_ORIGINAL',
      'USER_EDITED',
      'USER_EDITED',
      'USER_EDITED',
      'USER_EDITED',
      'AI_GENERATED',
    ]);
  });

  it('회원 기여 문장에 실제 문장 정보 모달에 필요한 메타데이터를 제공한다', () => {
    tutorialBookContents
      .filter(({ contentType }) => contentType !== 'AI_GENERATED')
      .forEach((content) => {
        expect(content.contributor).not.toBeNull();
        expect(content.contribution).not.toBeNull();
        expect(content.contribution?.finalDamage).toBeGreaterThan(0);
        expect(content.contribution?.moods.length).toBeGreaterThan(0);

        if (content.contentType === 'USER_EDITED') {
          expect(content.contribution?.originalContent).toBeTruthy();
        } else {
          expect(content.contribution?.originalContent).toBeNull();
        }
      });
  });
});
