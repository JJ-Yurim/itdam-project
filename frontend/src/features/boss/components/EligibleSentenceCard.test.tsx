import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import type { EligibleSentenceResponse } from '../types';

import { EligibleSentenceCard } from './EligibleSentenceCard';

const sentence: EligibleSentenceResponse = {
  sentenceVersionId: 84,
  sentenceId: 42,
  versionNo: 2,
  content: '햇빛이 창가에 부드럽게 번졌다.',
  baseScore: 39,
  wordBonus: 0,
  gameBaseAttack: 39,
  sentenceEnergy: 57,
};

describe('EligibleSentenceCard', () => {
  it('버전과 선택 문구 없이 문장 및 점수 정보를 간결하게 표시한다', () => {
    render(
      <EligibleSentenceCard sentence={sentence} isSelected={false} onToggle={() => undefined} />,
    );

    expect(screen.getByRole('button')).toHaveClass('lg:px-5', 'lg:py-4');
    expect(screen.getByText(sentence.content)).toHaveClass('lg:text-lg', 'lg:leading-8');
    expect(screen.queryByText('버전 2')).not.toBeInTheDocument();
    expect(screen.queryByText('선택하기')).not.toBeInTheDocument();

    expect(screen.getByText('기본 점수')).toHaveClass('lg:text-sm');
    expect(screen.getByText('39')).toHaveClass('lg:text-base');

    expect(screen.getByText('단어 보너스')).toBeInTheDocument();
    expect(screen.getByText('+0.0')).toBeInTheDocument();

    expect(screen.getByText('문장 에너지')).toHaveClass('lg:text-sm');
    expect(screen.getByText('57')).toBeInTheDocument();
    expect(screen.queryByText('기본 공격력')).not.toBeInTheDocument();
  });

  it('카드를 누르면 문장 버전 ID를 전달한다', async () => {
    const user = userEvent.setup();
    const onToggle = vi.fn();

    render(<EligibleSentenceCard sentence={sentence} isSelected={false} onToggle={onToggle} />);

    await user.click(
      screen.getByRole('button', {
        name: /햇빛이 창가에 부드럽게 번졌다/,
      }),
    );

    expect(onToggle).toHaveBeenCalledOnce();
    expect(onToggle).toHaveBeenCalledWith(84);
  });

  it('선택된 카드의 상태를 접근성 속성과 시각 스타일로 표시한다', () => {
    render(<EligibleSentenceCard sentence={sentence} isSelected onToggle={() => undefined} />);

    const selectedCard = screen.getByRole('button', {
      name: /햇빛이 창가에 부드럽게 번졌다/,
    });

    expect(selectedCard).toHaveAttribute('aria-pressed', 'true');
    expect(selectedCard).toHaveClass('bg-violet-50', 'ring-2');
    expect(screen.queryByText('선택됨')).not.toBeInTheDocument();
  });

  it('단어 보너스와 정수 문장 에너지를 표시한다', () => {
    render(
      <EligibleSentenceCard
        sentence={{ ...sentence, wordBonus: 5, gameBaseAttack: 44, sentenceEnergy: 71 }}
        isSelected={false}
        onToggle={() => undefined}
      />,
    );

    expect(screen.getByText('+5.0')).toBeInTheDocument();
    expect(screen.getByText('71')).toBeInTheDocument();
  });
});
