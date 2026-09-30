import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ComponentProps } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { DamiGuide } from './DamiGuide';

function renderGuide(overrides: Partial<ComponentProps<typeof DamiGuide>> = {}) {
  return render(
    <DamiGuide
      step={0}
      title="안녕, 나는 다미야!"
      description="실제 서비스 화면을 함께 둘러볼게."
      primaryLabel="튜토리얼 시작"
      onPrimary={vi.fn()}
      onReset={vi.fn()}
      onExit={vi.fn()}
      backdrop
      {...overrides}
    />,
  );
}

function appendTarget(id: string, top: number) {
  const target = document.createElement('div');
  target.id = id;
  target.dataset.guideTestTarget = '';
  target.getBoundingClientRect = vi.fn(
    () =>
      ({
        x: 24,
        y: top,
        top,
        right: 366,
        bottom: top + 80,
        left: 24,
        width: 342,
        height: 80,
        toJSON: () => ({}),
      }) as DOMRect,
  );
  document.body.append(target);
  return target;
}

describe('DamiGuide', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    document.querySelectorAll('[data-guide-test-target]').forEach((target) => target.remove());
  });

  it('다미 캐릭터와 왼쪽 말풍선, 전체 배경 오버레이를 표시한다', () => {
    renderGuide();

    expect(screen.getByTestId('dami-character')).toHaveAttribute(
      'src',
      '/tutorial-assets/dami-speaking-pointing-left.png',
    );
    const speechBubble = screen.getByTestId('dami-speech-bubble');
    expect(speechBubble).toContainElement(
      screen.getByRole('heading', { name: '안녕, 나는 다미야!' }),
    );
    expect(screen.getByTestId('tutorial-guide-backdrop')).toHaveClass('pointer-events-none');
    const guide = screen.getByLabelText('다미의 튜토리얼 안내');
    expect(guide).toHaveClass(
      'tutorial-dami-guide',
      'tutorial-dami-guide--bottom',
      'lg:h-[360px]',
      'lg:w-[740px]',
    );
    expect(guide).not.toHaveClass('tutorial-dami-guide--intro');
    expect(speechBubble).toHaveClass('lg:max-w-[520px]', 'lg:p-6');
    expect(screen.getByTestId('dami-character-frame')).toHaveClass(
      'h-32',
      'w-28',
      '-right-1',
      'lg:h-[336px]',
      'lg:w-72',
    );
    expect(screen.getByText('다미의 안내 · 1/8')).toHaveClass('lg:text-base');
    expect(screen.getByRole('heading', { name: '안녕, 나는 다미야!' })).toHaveClass(
      'lg:text-3xl',
    );
    expect(screen.getByText('실제 서비스 화면을 함께 둘러볼게.')).toHaveClass(
      'lg:text-lg',
      'lg:leading-8',
    );
    const primaryButton = screen.getByRole('button', { name: '튜토리얼 시작' });
    expect(primaryButton).toHaveClass(
      'w-full',
      'lg:min-h-12',
      'lg:text-base',
    );
    expect(primaryButton).not.toHaveClass('sm:w-auto');
  });

  it('안내를 접으면 말풍선과 전체 배경을 제거하고 원형 다미 버튼만 남긴다', async () => {
    const user = userEvent.setup();
    renderGuide();
    expect(screen.getByTestId('tutorial-guide-backdrop')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '다미 안내 접기' }));

    expect(screen.queryByTestId('dami-speech-bubble')).not.toBeInTheDocument();
    expect(screen.queryByTestId('tutorial-guide-backdrop')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '다미 안내 펼치기' })).toBeInTheDocument();
    expect(screen.getByTestId('dami-character')).toHaveAttribute(
      'src',
      '/tutorial-assets/dami-minimized-bust.png',
    );
    expect(screen.getByTestId('dami-character')).toHaveClass(
      'scale-[0.95]',
      'object-center',
      'translate-y-1',
    );
  });

  it('대상이 있는 단계는 말풍선을 표시하되 전체 배경 레이어를 만들지 않는다', () => {
    renderGuide({ step: 1, backdrop: false });

    expect(screen.getByTestId('dami-speech-bubble')).toBeInTheDocument();
    expect(screen.queryByTestId('tutorial-guide-backdrop')).not.toBeInTheDocument();
    expect(screen.getByLabelText('다미의 튜토리얼 안내')).not.toHaveClass(
      'tutorial-dami-guide--intro',
    );
  });

  it('공격 바가 있으면 펼친 다미 안내 전체를 위로 올린다', () => {
    renderGuide({ lifted: true });

    expect(screen.getByLabelText('다미의 튜토리얼 안내')).toHaveClass(
      'tutorial-dami-guide--lifted',
    );
  });

  it('대상이 화면 아래쪽이면 안내를 상단에 배치한다', () => {
    vi.stubGlobal('innerHeight', 844);
    const target = appendTarget('tutorial-coach-example', 620);

    renderGuide({ step: 2, backdrop: false, targetId: target.id });

    expect(screen.getByLabelText('다미의 튜토리얼 안내')).toHaveClass('tutorial-dami-guide--top');
  });

  it('대상이 화면 위쪽이면 안내를 하단에 배치한다', () => {
    vi.stubGlobal('innerHeight', 844);
    const target = appendTarget('tutorial-coach-words', 120);

    renderGuide({ step: 1, backdrop: false, targetId: target.id });

    expect(screen.getByLabelText('다미의 튜토리얼 안내')).toHaveClass(
      'tutorial-dami-guide--bottom',
    );
  });

  it('강제 배치가 대상 위치 기반 자동 배치보다 우선한다', () => {
    vi.stubGlobal('innerHeight', 844);
    const target = appendTarget('tutorial-coach-read', 620);

    renderGuide({
      step: 5,
      backdrop: false,
      targetId: target.id,
      placementOverride: 'bottom',
    });

    const guide = screen.getByLabelText('다미의 튜토리얼 안내');
    expect(guide).toHaveClass('tutorial-dami-guide--bottom');
    expect(guide).not.toHaveClass('tutorial-dami-guide--top');
  });

  it('책 상세 단계에서는 데스크톱 안내를 표지 아래로 더 내린다', () => {
    renderGuide({ step: 5, backdrop: false, placementOverride: 'bottom', lowered: true });

    expect(screen.getByLabelText('다미의 튜토리얼 안내')).toHaveClass(
      'tutorial-dami-guide--bottom',
      'tutorial-dami-guide--lowered',
    );
  });

  it('안내 바깥 조작은 실행하면서 현재 단계 안내를 최소화한다', async () => {
    const user = userEvent.setup();
    const onOutsideClick = vi.fn();

    render(
      <>
        <DamiGuide
          step={2}
          title="이제 너만의 문장을 만들어봐!"
          description="오늘의 단어를 문장에 담아봐."
          onReset={vi.fn()}
          onExit={vi.fn()}
        />
        <button type="button" onClick={onOutsideClick}>
          예시 문장 채우기
        </button>
      </>,
    );

    await user.click(screen.getByRole('button', { name: '예시 문장 채우기' }));

    expect(onOutsideClick).toHaveBeenCalledOnce();
    expect(screen.queryByTestId('dami-speech-bubble')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '다미 안내 펼치기' })).toBeInTheDocument();
  });

  it('안내 내부 조작은 자동으로 최소화하지 않는다', async () => {
    const user = userEvent.setup();
    const onExit = vi.fn();
    renderGuide({ onExit });

    await user.click(screen.getByRole('button', { name: '튜토리얼 나가기' }));

    expect(onExit).toHaveBeenCalledOnce();
    expect(screen.getByTestId('dami-speech-bubble')).toBeInTheDocument();
  });

  it('바깥 조작으로 접힌 안내는 다음 단계에서 다시 펼쳐진다', async () => {
    const user = userEvent.setup();
    const { rerender } = render(
      <DamiGuide
        step={1}
        title="단어를 확인해봐!"
        description="카드를 눌러봐."
        onReset={vi.fn()}
        onExit={vi.fn()}
      />,
    );

    await user.click(document.body);
    expect(screen.queryByTestId('dami-speech-bubble')).not.toBeInTheDocument();

    rerender(
      <DamiGuide
        step={2}
        title="문장을 만들어봐!"
        description="단어를 문장에 담아봐."
        onReset={vi.fn()}
        onExit={vi.fn()}
      />,
    );

    expect(screen.getByTestId('dami-speech-bubble')).toBeInTheDocument();
  });
});
