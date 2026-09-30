import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import type { SentenceEvaluationResponse } from '../types';

import {
  SentenceEvaluationDetails,
  SentenceEvaluationResult,
  SentenceEvaluationResultCard,
  SentenceEvaluationScoreSummary,
} from './SentenceEvaluationResult';

const evaluation: SentenceEvaluationResponse = {
  sentenceVersionId: 84,
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
  overallFeedback: '이미지가 선명해요.',
  moods: [{ code: 'CALM', strength: 0.82, rank: 1 }],
  usedDailyWords: [{ dailyWordSetItemId: 15, wordId: 1, word: '은하수' }],
  modelName: 'test',
  promptVersion: 'v1',
  rubricVersion: 'v1',
  evaluatedAt: '2026-07-30T02:00:00Z',
};

describe('SentenceEvaluationResult', () => {
  it('평가 중에는 움직이는 진행 상태와 분석 항목을 표시한다', () => {
    render(
      <SentenceEvaluationResult
        evaluation={{ ...evaluation, evaluationStatus: 'EVALUATING', scores: null }}
      />,
    );

    expect(screen.getByRole('heading', { name: 'AI가 문장의 힘을 읽고 있어요' })).toBeVisible();
    expect(screen.getByRole('progressbar', { name: 'AI 문장 평가 진행 중' })).toHaveAttribute(
      'aria-valuetext',
      '문장을 분석하고 있어요',
    );
    expect(screen.getByText('문장 흐름 분석 중')).toBeInTheDocument();
    expect(screen.getByText('표현력 분석 중')).toBeInTheDocument();
    expect(screen.getByText('분위기 분석 중')).toBeInTheDocument();
  });

  it('점수, 분위기, 사용 단어와 피드백을 표시한다', () => {
    render(<SentenceEvaluationResult evaluation={evaluation} />);

    expect(screen.getByText('83.35')).toBeInTheDocument();
    expect(screen.getByText('문장 에너지')).toBeInTheDocument();
    expect(screen.queryByText('기본 공격력')).not.toBeInTheDocument();
    expect(screen.getByText('평온 82%')).toBeInTheDocument();
    expect(screen.getByText(/은하수/)).toBeInTheDocument();
    expect(screen.getByText('이미지가 선명해요.')).toBeInTheDocument();
  });

  it('핵심 점수와 세부 결과를 독립된 의미 영역으로 조합한다', () => {
    render(
      <SentenceEvaluationResultCard>
        <SentenceEvaluationScoreSummary evaluation={evaluation} />
        <SentenceEvaluationDetails evaluation={evaluation} />
      </SentenceEvaluationResultCard>,
    );

    const summary = screen.getByLabelText('핵심 평가 점수');
    expect(within(summary).getByText('기본 점수')).toBeInTheDocument();
    expect(within(summary).getByText('문장 에너지')).toBeInTheDocument();

    const details = screen.getByLabelText('세부 평가 결과');
    expect(within(details).getByText('완결성')).toBeInTheDocument();
    expect(within(details).getByText('평온 82%')).toBeInTheDocument();
    expect(within(details).getByText('이미지가 선명해요.')).toBeInTheDocument();
  });

  it('안전성 거부 사유를 표시한다', () => {
    render(
      <SentenceEvaluationResult
        evaluation={{
          ...evaluation,
          evaluationStatus: 'REJECTED',
          safety: { status: 'REJECTED', reason: '유해 표현이 감지되었습니다.' },
          scores: null,
        }}
      />,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('유해 표현이 감지되었습니다.');
  });
});
