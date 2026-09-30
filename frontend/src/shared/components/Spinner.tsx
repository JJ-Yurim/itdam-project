import type { ReactNode } from 'react';

/**
 * 진행 중임을 알리는 회전 링. 의미 전달은 감싸는 쪽의 role="status" 텍스트가 담당하므로
 * 스피너 자체는 장식으로 취급한다. 모션 최소화 설정에서는 회전 없이 링만 보여준다.
 */
export function Spinner({ className }: { className?: string }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      fill="none"
      className={['animate-spin motion-reduce:animate-none', className ?? 'size-5'].join(' ')}
    >
      <circle cx="12" cy="12" r="10" stroke="currentColor" strokeOpacity="0.25" strokeWidth="4" />
      <path
        d="M22 12a10 10 0 0 0-10-10"
        stroke="currentColor"
        strokeWidth="4"
        strokeLinecap="round"
      />
    </svg>
  );
}

/**
 * 실제 콘텐츠 골격을 보여주는 로딩 섹션. 중앙 스피너 대신 이걸 쓰면
 * 로딩→콘텐츠 전환에서 레이아웃이 점프하지 않는다.
 * label은 스크린리더 안내이자 테스트가 조회하는 문구다(화면에는 숨김).
 */
export function SkeletonSection({ label, children }: { label: string; children: ReactNode }) {
  return (
    <section role="status" aria-live="polite">
      <span className="sr-only">{label}</span>
      <div aria-hidden="true">{children}</div>
    </section>
  );
}

/**
 * 로딩 중 자리를 잡아 두는 스켈레톤 블록. 크기는 호출부가 className으로 정한다.
 * 스크린리더에는 감싸는 쪽의 로딩 안내가 전달되므로 여기서는 숨긴다.
 */
export function Skeleton({ className }: { className?: string }) {
  return (
    <div
      aria-hidden="true"
      className={[
        'animate-pulse rounded-control bg-stone-200/80 motion-reduce:animate-none',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    />
  );
}
