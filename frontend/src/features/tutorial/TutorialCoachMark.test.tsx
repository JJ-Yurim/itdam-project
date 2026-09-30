import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { TutorialCoachMark, TutorialCoachMarkLabel } from './TutorialCoachMark';

describe('TutorialCoachMark', () => {
  afterEach(() => vi.restoreAllMocks());

  it('전체 화면 dim은 body에 고정하고 실제 대상은 그 위에 둔다', () => {
    render(
      <TutorialCoachMark active id="tutorial-target" label="단어 카드를 눌러보세요">
        <button type="button">단어 카드</button>
      </TutorialCoachMark>,
    );

    const target = document.getElementById('tutorial-target');
    const dim = screen.getByTestId('tutorial-screen-dim');
    const spotlight = screen.getByTestId('tutorial-spotlight');
    const label = screen.getByRole('status', {
      name: '다음 행동: 단어 카드를 눌러보세요',
    });

    expect(document.body).toContainElement(dim);
    expect(target).not.toContainElement(dim);
    expect(dim).toHaveClass('fixed', 'inset-0', 'z-[45]', 'pointer-events-none');
    expect(target).toHaveClass('z-[46]', 'bg-white');
    expect(target).toContainElement(spotlight);
    expect(spotlight).toHaveClass('absolute', 'pointer-events-none');
    expect(spotlight).not.toHaveClass('fixed');
    expect(target).toContainElement(label);
  });

  it('dim을 끄더라도 스포트라이트와 다음 행동 안내를 유지한다', () => {
    render(
      <TutorialCoachMark active dimScreen={false} label="AI 평가를 요청하세요">
        <button type="button">AI 평가 요청하기</button>
      </TutorialCoachMark>,
    );

    expect(screen.queryByTestId('tutorial-screen-dim')).not.toBeInTheDocument();
    expect(screen.getByTestId('tutorial-spotlight')).toBeInTheDocument();
    expect(
      screen.getByRole('status', { name: '다음 행동: AI 평가를 요청하세요' }),
    ).toBeInTheDocument();
  });

  it('비활성 상태에서는 스포트라이트를 만들지 않는다', () => {
    render(
      <TutorialCoachMark
        active={false}
        id="inactive-tutorial-target"
        label="단어 카드를 눌러보세요"
      >
        <button type="button">단어 카드</button>
      </TutorialCoachMark>,
    );

    expect(document.getElementById('inactive-tutorial-target')).not.toHaveClass('bg-white');
    expect(screen.queryByTestId('tutorial-spotlight')).not.toBeInTheDocument();
    expect(screen.queryByTestId('tutorial-screen-dim')).not.toBeInTheDocument();
  });

  it('내장 안내를 숨겨도 dim과 스포트라이트를 유지한다', () => {
    render(
      <TutorialCoachMark active showLabel={false} label="예시 문장을 눌러봐">
        <button type="button">예시 문장 채우기</button>
      </TutorialCoachMark>,
    );

    expect(screen.getByTestId('tutorial-screen-dim')).toBeInTheDocument();
    expect(screen.getByTestId('tutorial-spotlight')).toBeInTheDocument();
    expect(
      screen.queryByRole('status', { name: '다음 행동: 예시 문장을 눌러봐' }),
    ).not.toBeInTheDocument();
  });

  it('분리된 안내 배지를 행동 대상의 상대 위치 래퍼에 배치한다', () => {
    render(
      <div data-testid="action-anchor" className="relative">
        <TutorialCoachMarkLabel active label="예시 문장을 눌러봐" labelAlign="start" />
        <button type="button">예시 문장 채우기</button>
      </div>,
    );

    const anchor = screen.getByTestId('action-anchor');
    const label = screen.getByRole('status', { name: '다음 행동: 예시 문장을 눌러봐' });
    expect(anchor).toContainElement(label);
    expect(label).toHaveClass('absolute', 'bottom-full', 'pointer-events-none', 'left-0');
    expect(label).toHaveClass('lg:mb-3', 'lg:px-[18px]', 'lg:py-3', 'lg:text-lg');
    expect(label.querySelector('span.absolute')).toHaveClass('lg:size-[15px]');
  });

  it('스크롤할 때 viewport 좌표를 다시 측정하지 않는다', () => {
    const rectSpy = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect');

    render(
      <TutorialCoachMark active label="단어 카드를 눌러보세요">
        <button type="button">단어 카드</button>
      </TutorialCoachMark>,
    );
    fireEvent.scroll(window);

    expect(rectSpy).not.toHaveBeenCalled();
  });

  it('스포트라이트가 표시돼도 실제 대상 버튼을 누를 수 있다', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();

    render(
      <TutorialCoachMark active label="단어 카드를 눌러보세요">
        <button type="button" onClick={onClick}>
          단어 카드
        </button>
      </TutorialCoachMark>,
    );
    expect(screen.getByTestId('tutorial-spotlight')).toHaveClass('pointer-events-none');
    await user.click(screen.getByRole('button', { name: '단어 카드' }));

    expect(onClick).toHaveBeenCalledOnce();
  });
});
