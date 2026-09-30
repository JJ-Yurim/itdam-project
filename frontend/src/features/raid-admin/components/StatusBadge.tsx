export type StatusTone = 'violet' | 'emerald' | 'amber' | 'stone' | 'red';

// Tailwind는 클래스 이름을 정적으로 스캔하므로 톤별 클래스를 문자열 그대로 나열한다.
const toneClass: Record<StatusTone, string> = {
  violet: 'bg-violet-50 text-violet-700',
  emerald: 'bg-emerald-50 text-emerald-700',
  amber: 'bg-amber-50 text-amber-700',
  stone: 'bg-stone-100 text-stone-600',
  red: 'bg-red-50 text-red-700',
};

export function StatusBadge({ label, tone = 'stone' }: { label: string; tone?: StatusTone }) {
  return (
    <span
      className={`inline-flex shrink-0 items-center rounded-full px-2 py-1 text-xs font-semibold ${toneClass[tone]}`}
    >
      {label}
    </span>
  );
}
