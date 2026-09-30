import { useState } from 'react';

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import type { EligibleSentenceResponse } from '../types';

import { EligibleSentenceList } from './EligibleSentenceList';

const sentences: EligibleSentenceResponse[] = [
  {
    sentenceVersionId: 84,
    sentenceId: 42,
    versionNo: 2,
    content: '햇빛이 창가에 부드럽게 번졌다.',
    baseScore: 39,
    wordBonus: 0,
    gameBaseAttack: 39,
    sentenceEnergy: 57,
  },
  {
    sentenceVersionId: 91,
    sentenceId: 47,
    versionNo: 1,
    content: '고요한 바람이 숲의 이야기를 전해 주었다.',
    baseScore: 42,
    wordBonus: 5,
    gameBaseAttack: 47,
    sentenceEnergy: 69,
  },
];

function TestHarness() {
  const [selectedSentenceVersionId, setSelectedSentenceVersionId] = useState<number | null>(null);

  return (
    <EligibleSentenceList
      items={sentences}
      selectedSentenceVersionId={selectedSentenceVersionId}
      onSelectionChange={setSelectedSentenceVersionId}
    />
  );
}

describe('EligibleSentenceList', () => {
  it('한 번에 문장 하나만 선택한다', async () => {
    const user = userEvent.setup();

    render(<TestHarness />);

    const firstCard = screen.getByRole('button', {
      name: /햇빛이 창가에 부드럽게 번졌다/,
    });

    const secondCard = screen.getByRole('button', {
      name: /고요한 바람이 숲의 이야기를 전해 주었다/,
    });

    expect(firstCard).toHaveAttribute('aria-pressed', 'false');
    expect(secondCard).toHaveAttribute('aria-pressed', 'false');

    await user.click(firstCard);

    expect(firstCard).toHaveAttribute('aria-pressed', 'true');
    expect(secondCard).toHaveAttribute('aria-pressed', 'false');

    await user.click(secondCard);

    expect(firstCard).toHaveAttribute('aria-pressed', 'false');
    expect(secondCard).toHaveAttribute('aria-pressed', 'true');
  });

  it('선택된 문장을 다시 누르면 선택을 해제한다', async () => {
    const user = userEvent.setup();

    render(<TestHarness />);

    const firstCard = screen.getByRole('button', {
      name: /햇빛이 창가에 부드럽게 번졌다/,
    });

    await user.click(firstCard);

    expect(firstCard).toHaveAttribute('aria-pressed', 'true');

    await user.click(firstCard);

    expect(firstCard).toHaveAttribute('aria-pressed', 'false');
  });

  it('최소 점수 미달 문장을 붉게 표시하고 선택을 막는다', async () => {
    const user = userEvent.setup();
    render(
      <EligibleSentenceList
        items={sentences}
        selectedSentenceVersionId={null}
        onSelectionChange={() => undefined}
        minimumScore={40}
      />,
    );

    const unavailableCard = screen.getByRole('button', {
      name: /최소 점수 미달 · 제출 불가.*햇빛이 창가에 부드럽게 번졌다/,
    });
    const availableCard = screen.getByRole('button', {
      name: /고요한 바람이 숲의 이야기를 전해 주었다/,
    });

    expect(unavailableCard).toBeDisabled();
    expect(unavailableCard).toHaveClass('bg-red-50', 'ring-red-200');
    expect(availableCard).toBeEnabled();

    await user.click(unavailableCard);
    expect(unavailableCard).toHaveAttribute('aria-pressed', 'false');
  });
});
