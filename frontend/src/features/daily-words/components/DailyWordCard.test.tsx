import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeAll, describe, expect, it } from 'vitest';

import { DailyWordCard } from './DailyWordCard';

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function showModal() {
    this.open = true;
  };
  HTMLDialogElement.prototype.close = function close() {
    this.open = false;
  };
});

const dailyWord = {
  position: 1,
  wordId: 101,
  word: '은하수',
  partOfSpeech: '명사',
  definition: '맑은 밤하늘에 강처럼 보이는 수많은 별의 무리',
  example: '여름밤 은하수가 우리 머리 위로 길게 흘렀다.',
};

const COVER_IMAGE_SELECTOR = 'img[src="/images/word-card-cover.png"]';
const FRAME_IMAGE_SELECTOR = 'img[src="/images/word-card-frame.png"]';

describe('DailyWordCard', () => {
  it('가림면에는 안내 글자 없이 카드 뒷면 이미지만 표시한다', () => {
    const { container } = render(<DailyWordCard dailyWord={dailyWord} />);

    expect(screen.getByRole('button', { name: '1번째 단어 카드 뒤집기' })).toHaveTextContent('');
    expect(container.querySelector(COVER_IMAGE_SELECTOR)).toBeInTheDocument();
    expect(container.querySelector(FRAME_IMAGE_SELECTOR)).toBeInTheDocument();
  });

  it('카드 이미지를 불러오지 못하면 이미지를 감추고 카드 기능은 유지한다', async () => {
    const user = userEvent.setup();
    const { container } = render(<DailyWordCard dailyWord={dailyWord} />);

    const coverImage = container.querySelector(COVER_IMAGE_SELECTOR);
    expect(coverImage).not.toBeNull();
    fireEvent.error(coverImage as HTMLImageElement);

    expect(container.querySelector(COVER_IMAGE_SELECTOR)).not.toBeInTheDocument();

    // 이미지가 없어도 개봉과 단어 표시는 그대로 동작한다.
    await user.click(screen.getByRole('button', { name: '1번째 단어 카드 뒤집기' }));
    expect(screen.getByText('은하수')).toBeInTheDocument();
  });

  it('카드를 뒤집으면 품사와 단어, 의미보기 버튼이 표시된다', async () => {
    const user = userEvent.setup();

    render(<DailyWordCard dailyWord={dailyWord} />);

    const hiddenCard = screen.getByRole('button', {
      name: '1번째 단어 카드 뒤집기',
    });

    expect(screen.queryByRole('button', { name: '은하수 의미보기' })).not.toBeInTheDocument();

    await user.click(hiddenCard);

    expect(screen.getByText('명사')).toBeInTheDocument();
    expect(screen.getByText('은하수')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '은하수 의미보기' })).toBeInTheDocument();

    // 포커스가 숨겨지는 앞면에 남지 않고 뒷면 의미보기 버튼으로 이동한다.
    expect(screen.getByRole('button', { name: '은하수 의미보기' })).toHaveFocus();

    // 공개된 카드의 뒤집기 버튼은 접근성 트리에서 제외되어 다시 눌 수 없다.
    expect(
      screen.queryByRole('button', { name: '1번째 단어 카드 뒤집기' }),
    ).not.toBeInTheDocument();
  });

  it('공개된 카드를 다시 누르면 앞면으로 돌아오고, 또 누르면 다시 공개된다', async () => {
    const user = userEvent.setup();

    render(<DailyWordCard dailyWord={dailyWord} />);

    await user.click(screen.getByRole('button', { name: '1번째 단어 카드 뒤집기' }));
    await user.click(screen.getByRole('button', { name: '은하수 카드 다시 뒤집기' }));

    // 앞면으로 돌아오면 뒷면 버튼들이 접근성 트리에서 빠지고 포커스는 앞면으로 이동한다.
    expect(screen.queryByRole('button', { name: '은하수 의미보기' })).not.toBeInTheDocument();
    const frontButton = screen.getByRole('button', { name: '1번째 단어 카드 뒤집기' });
    expect(frontButton).toHaveFocus();

    // 다시 뒤집으면 개봉 상태 그대로 단어가 보인다.
    await user.click(frontButton);
    expect(screen.getByRole('button', { name: '은하수 의미보기' })).toBeInTheDocument();
  });

  it('의미보기를 누르면 뜻과 예문 팝업이 열리고 닫기를 누르면 닫힌다', async () => {
    const user = userEvent.setup();

    render(<DailyWordCard dailyWord={dailyWord} />);

    await user.click(screen.getByRole('button', { name: '1번째 단어 카드 뒤집기' }));
    await user.click(screen.getByRole('button', { name: '은하수 의미보기' }));

    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAccessibleName('은하수');
    expect(dialog).toHaveTextContent(dailyWord.definition);
    expect(dialog).toHaveTextContent(dailyWord.example);

    await user.click(screen.getByRole('button', { name: '닫기' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
