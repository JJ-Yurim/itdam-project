import { FadeInImage } from '@/shared/components';

interface LibraryBookCoverProps {
  title: string;
  imageUrl?: string | null;
  size?: 'small' | 'large';
  variant?: 'default' | 'grid';
}

const coverPresets = [
  {
    icon: '별',
    className: 'from-indigo-950 via-violet-800 to-amber-300',
  },
  {
    icon: '열쇠',
    className: 'from-emerald-950 via-teal-700 to-lime-300',
  },
  {
    icon: '달',
    className: 'from-slate-950 via-blue-800 to-cyan-300',
  },
  {
    icon: '탑',
    className: 'from-stone-950 via-rose-800 to-orange-300',
  },
];

function getCoverPreset(title: string) {
  const charCodeSum = [...title].reduce((sum, char) => sum + char.charCodeAt(0), 0);

  return coverPresets[charCodeSum % coverPresets.length] ?? coverPresets[0];
}

export function LibraryBookCover({
  title,
  imageUrl,
  size = 'small',
  variant = 'default',
}: LibraryBookCoverProps) {
  const coverPreset = getCoverPreset(title);
  const isLarge = size === 'large';

  if (imageUrl) {
    return (
      <div
        className={[
          'relative shrink-0 overflow-hidden rounded-xl bg-stone-100 shadow-sm',
          variant === 'grid'
            ? 'aspect-[3/4] w-full'
            : isLarge
              ? 'aspect-[3/4] w-36'
              : 'aspect-[3/4] w-20',
        ].join(' ')}
      >
        <FadeInImage src={imageUrl} alt={`${title} 책 표지`} className="h-full w-full object-cover" />
      </div>
    );
  }

  return (
    <div
      aria-hidden="true"
      className={[
        'relative flex shrink-0 flex-col justify-end overflow-hidden rounded-xl bg-gradient-to-br p-3 text-white shadow-sm',
        coverPreset.className,
        variant === 'grid'
          ? 'aspect-[3/4] w-full'
          : isLarge
            ? 'aspect-[3/4] w-36'
            : 'aspect-[3/4] w-20',
      ].join(' ')}
    >
      <div className="absolute inset-x-3 top-3 h-px bg-white/35" />
      <div className="absolute inset-x-3 bottom-3 h-px bg-white/30" />

      <strong
        className={[
          'absolute inset-0 flex items-center justify-center font-extrabold',
          isLarge ? 'text-3xl' : 'text-xl',
        ].join(' ')}
      >
        {coverPreset.icon}
      </strong>

      <span className={['font-extrabold', isLarge ? 'text-lg' : 'text-xs'].join(' ')}>
        잇담 도서관
      </span>
    </div>
  );
}
