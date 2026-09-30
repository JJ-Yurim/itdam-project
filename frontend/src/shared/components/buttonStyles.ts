export type ButtonVariant = 'primary' | 'secondary' | 'success' | 'danger' | 'ghost';
export type ButtonSize = 'sm' | 'md' | 'lg';

const variantClasses: Record<ButtonVariant, string> = {
  primary: 'bg-brand text-white hover:bg-brand-strong',
  secondary: 'border border-stone-300 bg-white text-stone-700 hover:bg-stone-50',
  success: 'bg-emerald-600 text-white hover:bg-emerald-700',
  danger: 'bg-red-600 text-white hover:bg-red-700',
  ghost: 'text-brand-strong hover:bg-brand-soft',
};

const sizeClasses: Record<ButtonSize, string> = {
  sm: 'min-h-9 rounded-control px-3 text-sm',
  md: 'min-h-11 rounded-control px-4',
  lg: 'min-h-12 rounded-control-lg px-5 text-base',
};

export interface ButtonStyleOptions {
  variant?: ButtonVariant;
  size?: ButtonSize;
}

/**
 * 버튼과 "버튼처럼 보이는 링크"가 같은 스타일을 공유하기 위한 클래스 조합.
 * 화면에서 개별적으로 CTA 클래스를 복제하지 말고 이 함수를 쓴다.
 */
export function buttonClasses(
  { variant = 'primary', size = 'md' }: ButtonStyleOptions = {},
  className?: string,
) {
  return [
    'inline-flex items-center justify-center gap-2 font-semibold transition-colors',
    'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand',
    'active:translate-y-px motion-reduce:active:translate-y-0',
    'disabled:cursor-not-allowed disabled:opacity-50 aria-disabled:cursor-not-allowed aria-disabled:opacity-50',
    sizeClasses[size],
    variantClasses[variant],
    className,
  ]
    .filter(Boolean)
    .join(' ');
}
