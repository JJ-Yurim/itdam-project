import type { ReactNode } from 'react';

/**
 * 상태·수치 뱃지의 톤 체계. 화면마다 임의 색을 고르지 말고 의미로 고른다.
 * - brand: 점수·참여 등 잇담의 주 지표
 * - attack: 게임 공격력 수치
 * - success: 완료·수록·실시간 등 긍정 상태
 * - warning: 지연·주의 상태
 * - danger: 오류·차단 상태
 * - like: 좋아요 계열
 * - neutral: 그 외 부가 정보
 */
export type BadgeTone = 'neutral' | 'brand' | 'attack' | 'success' | 'warning' | 'danger' | 'like';

export type BadgeSize = 'sm' | 'md';

const toneClasses: Record<BadgeTone, string> = {
  neutral: 'bg-stone-100 text-stone-700',
  brand: 'bg-brand-muted text-brand-deep',
  attack: 'bg-orange-100 text-orange-800',
  success: 'bg-emerald-100 text-emerald-700',
  warning: 'bg-amber-100 text-amber-700',
  danger: 'bg-red-50 text-red-700',
  like: 'bg-rose-100 text-rose-700',
};

// 클래스를 병합 없이 이어 붙이므로, 크기류 유틸리티는 호출부 className이 아니라
// 여기서 배타적으로 골라야 기본값과의 순서 경합이 생기지 않는다.
const sizeClasses: Record<BadgeSize, string> = {
  sm: 'px-2 py-0.5 text-[11px]',
  md: 'px-2.5 py-1 text-xs',
};

interface BadgeProps {
  tone?: BadgeTone;
  size?: BadgeSize;
  className?: string;
  children: ReactNode;
}

export function Badge({ tone = 'neutral', size = 'md', className, children }: BadgeProps) {
  return (
    <span
      className={[
        'inline-flex items-center gap-1 rounded-full font-semibold',
        sizeClasses[size],
        toneClasses[tone],
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {children}
    </span>
  );
}
