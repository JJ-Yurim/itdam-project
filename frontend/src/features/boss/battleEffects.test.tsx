import { act, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  getBossAttackIntensity,
  useBossHpLoss,
  useBossPhaseAdvance,
  useLatestBossHit,
} from './battleEffects';
import type { BossRunHit } from './useBossRunStream';
import {
  BossHitStage,
  BossHpLossTrail,
  BossPhaseClearBanner,
} from './components/BossBattleEffects';

afterEach(() => {
  vi.useRealTimers();
});

describe('공격 강도 판정', () => {
  it('최소 기준 이상 권장 기준 미만은 일반 공격이다', () => {
    expect(getBossAttackIntensity(35, 35, 50)).toBe('normal');
    expect(getBossAttackIntensity(49, 35, 50)).toBe('normal');
  });

  it('권장 기준 이상은 강한 공격이다', () => {
    expect(getBossAttackIntensity(50, 35, 50)).toBe('strong');
    expect(getBossAttackIntensity(84, 35, 50)).toBe('strong');
  });

  it('기준값이 올바르지 않으면 과한 연출 대신 일반 공격을 사용한다', () => {
    expect(getBossAttackIntensity(80, Number.NaN, 50)).toBe('normal');
  });
});

/** HP 비율을 prop으로 받아 훅 결과를 그대로 노출하는 검사용 화면. */
function HpLossProbe({ percent }: { percent: number | null }) {
  const loss = useBossHpLoss(percent);

  return (
    <div className="relative">
      <span data-testid="loss">{loss === null ? 'none' : `${loss.from}→${loss.to}`}</span>
      <BossHpLossTrail loss={loss} />
    </div>
  );
}

function PhaseProbe({ phaseNo }: { phaseNo: number | undefined }) {
  const advance = useBossPhaseAdvance(phaseNo);

  return <BossPhaseClearBanner advance={advance} />;
}

describe('보스 HP 잔상', () => {
  it('HP가 줄면 잃은 구간을 잔상으로 남긴다', () => {
    const { rerender } = render(<HpLossProbe percent={100} />);

    // 첫 렌더는 비교 대상이 없어 잔상을 만들지 않는다.
    expect(screen.getByTestId('loss')).toHaveTextContent('none');

    rerender(<HpLossProbe percent={62} />);

    expect(screen.getByTestId('loss')).toHaveTextContent('100→62');
  });

  it('HP가 늘거나 그대로면 잔상을 만들지 않는다', () => {
    const { rerender } = render(<HpLossProbe percent={40} />);

    // 페이즈가 넘어가 HP가 다시 채워지는 경우다.
    rerender(<HpLossProbe percent={100} />);

    expect(screen.getByTestId('loss')).toHaveTextContent('none');
  });

  it('현재 페이즈가 없어지면(null) 유령 손실을 만들지 않는다', () => {
    const { rerender } = render(<HpLossProbe percent={45} />);

    // 전투를 보던 중 회차가 종료돼 currentPhase가 사라진 상황이다.
    // 0%로 간주하면 "45% → 0%" 잔상이 바 전체에 남는다.
    rerender(<HpLossProbe percent={null} />);

    expect(screen.getByTestId('loss')).toHaveTextContent('none');
  });
});

describe('페이즈 돌파 배너', () => {
  it('페이즈 번호가 올라가면 돌파와 다음 페이즈를 알린다', () => {
    const { rerender } = render(<PhaseProbe phaseNo={1} />);

    expect(screen.queryByRole('status')).not.toBeInTheDocument();

    rerender(<PhaseProbe phaseNo={2} />);

    const banner = screen.getByRole('status');
    expect(banner).toHaveTextContent('Phase 1 돌파!');
    expect(banner).toHaveTextContent('Phase 2가 시작됐어요');
  });

  it('배너는 잠시 뒤 스스로 사라진다', () => {
    vi.useFakeTimers();
    const { rerender } = render(<PhaseProbe phaseNo={1} />);
    rerender(<PhaseProbe phaseNo={2} />);

    expect(screen.getByRole('status')).toBeInTheDocument();

    act(() => vi.advanceTimersByTime(2_500));

    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });
});

/** 두 출처의 타격을 합친 결과를 노출하는 검사용 화면. */
function LatestHitProbe({
  streamHit,
  ownHit,
}: {
  streamHit: BossRunHit | null;
  ownHit: BossRunHit | null;
}) {
  const hit = useLatestBossHit(streamHit, ownHit);

  return <span data-testid="hit">{hit === null ? 'none' : `${hit.damage}#${hit.key}`}</span>;
}

describe('타격 출처 합치기', () => {
  it('새로 도착한 타격만 재생 대상으로 삼는다', () => {
    const { rerender } = render(<LatestHitProbe streamHit={null} ownHit={null} />);
    expect(screen.getByTestId('hit')).toHaveTextContent('none');

    rerender(<LatestHitProbe streamHit={{ damage: 110, baseScore: 49, key: 1 }} ownHit={null} />);
    expect(screen.getByTestId('hit')).toHaveTextContent('110#1');

    rerender(<LatestHitProbe streamHit={{ damage: 90, baseScore: 50, key: 2 }} ownHit={null} />);
    // 출처와 무관한 단조 증가 key로 다시 매긴다.
    expect(screen.getByTestId('hit')).toHaveTextContent('90#2');
  });

  it('실시간이 끊겨 출처가 바뀌어도 이미 재생한 타격을 다시 재생하지 않는다', () => {
    const streamHit = { damage: 110, baseScore: 84, key: 42 };
    const ownHit = { damage: 57, baseScore: 49, key: 1 };
    const { rerender } = render(<LatestHitProbe streamHit={streamHit} ownHit={null} />);

    expect(screen.getByTestId('hit')).toHaveTextContent('110#1');

    // 강등된 직후에는 내 타격이 아직 없다. 지나간 SSE 타격이 되살아나선 안 된다.
    rerender(<LatestHitProbe streamHit={streamHit} ownHit={null} />);
    expect(screen.getByTestId('hit')).toHaveTextContent('110#1');

    // 강등 상태에서 내가 제출하면 그 타격만 새로 재생된다.
    rerender(<LatestHitProbe streamHit={streamHit} ownHit={ownHit} />);
    expect(screen.getByTestId('hit')).toHaveTextContent('57#2');

    // 실시간이 복구돼도 이미 본 SSE 타격은 다시 재생하지 않는다.
    rerender(<LatestHitProbe streamHit={streamHit} ownHit={null} />);
    expect(screen.getByTestId('hit')).toHaveTextContent('57#2');
  });
});

describe('타격 연출', () => {
  it('타격이 도착하면 보스 위에 데미지 수치를 띄운다', () => {
    render(
      <BossHitStage hit={{ damage: 110, baseScore: 84, key: 1 }}>
        <img alt="보스" src="/boss.png" />
      </BossHitStage>,
    );

    expect(screen.getByLabelText('110 데미지')).toHaveTextContent('-110');
  });

  it('타격이 없으면 연출을 그리지 않는다', () => {
    render(
      <BossHitStage hit={null}>
        <img alt="보스" src="/boss.png" />
      </BossHitStage>,
    );

    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('제출 중에는 공격 마법 충전 상태를 알린다', () => {
    render(
      <BossHitStage hit={null} charging>
        <img alt="보스" src="/boss.png" />
      </BossHitStage>,
    );

    expect(screen.getByRole('status', { name: '공격 마법 충전 중' })).toBeInTheDocument();
  });

  it('강한 공격은 금빛 투사체와 강화된 데미지 연출을 사용한다', () => {
    const { container } = render(
      <BossHitStage hit={{ damage: 57, baseScore: 84, key: 2 }} intensity="strong">
        <img alt="보스" src="/boss.png" />
      </BossHitStage>,
    );

    expect(container.querySelector('[data-attack-intensity="strong"]')).toBeInTheDocument();
    expect(container.querySelector('.boss-hit-stage')).toHaveStyle(
      '--boss-projectile-travel: 300ms',
    );
    expect(container.querySelector('.boss-projectile-strong')).toBeInTheDocument();
    expect(screen.getByText('강한 공격!')).toBeInTheDocument();
  });

  it('같은 타격의 강도만 바뀌면 반동을 다시 재생하지 않는다', () => {
    const originalAnimate = HTMLElement.prototype.animate;
    const cancel = vi.fn();
    const animate = vi.fn(() => ({ cancel }) as unknown as Animation);
    Object.defineProperty(HTMLElement.prototype, 'animate', {
      configurable: true,
      value: animate,
    });

    try {
      const { rerender } = render(
        <BossHitStage hit={{ damage: 49, baseScore: 49, key: 3 }} intensity="normal">
          <img alt="보스" src="/boss.png" />
        </BossHitStage>,
      );

      rerender(
        <BossHitStage hit={{ damage: 50, baseScore: 50, key: 3 }} intensity="strong">
          <img alt="보스" src="/boss.png" />
        </BossHitStage>,
      );
      expect(animate).toHaveBeenCalledTimes(1);

      rerender(
        <BossHitStage hit={{ damage: 50, baseScore: 50, key: 4 }} intensity="strong">
          <img alt="보스" src="/boss.png" />
        </BossHitStage>,
      );
      expect(animate).toHaveBeenCalledTimes(2);
    } finally {
      if (originalAnimate) {
        Object.defineProperty(HTMLElement.prototype, 'animate', {
          configurable: true,
          value: originalAnimate,
        });
      } else {
        Reflect.deleteProperty(HTMLElement.prototype, 'animate');
      }
    }
  });

  it('데미지 수치는 잠시 뒤 사라지고 보스 이미지는 남는다', () => {
    vi.useFakeTimers();
    render(
      <BossHitStage hit={{ damage: 64, baseScore: 64, key: 7 }}>
        <img alt="보스" src="/boss.png" />
      </BossHitStage>,
    );

    act(() => vi.advanceTimersByTime(1_500));

    expect(screen.queryByLabelText('64 데미지')).not.toBeInTheDocument();
    expect(screen.getByAltText('보스')).toBeInTheDocument();
  });
});
