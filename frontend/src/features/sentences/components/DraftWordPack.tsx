import { DailyWordCard } from '@/features/daily-words';
import type { DailyWord } from '@/features/daily-words';

interface DraftWordPackProps {
  items: DailyWord[];
  matchedWordIds: Set<number>;
  /** 개봉한 단어만 보여준다. 미개봉 단어는 가린 채 탭하면 개봉된다. */
  revealedWordIds: Set<number>;
  onReveal: (wordId: number) => void;
}

// 홈 단어팩과 같은 카드 디자인을 재사용하고, 문장에 사용된 단어만 배지로 표시한다.
export function DraftWordPack({
  items,
  matchedWordIds,
  revealedWordIds,
  onReveal,
}: DraftWordPackProps) {
  return (
    // 데스크톱 카드 크기는 DailyWordList(홈 단어팩)와 같은 상한을 공유한다. 한쪽만 바꾸면
    // 같은 카드가 화면마다 다른 크기로 보인다.
    <div
      aria-label="오늘의 단어팩"
      className="grid grid-cols-5 gap-1.5 sm:gap-2 lg:mx-auto lg:max-w-[700px] lg:gap-3"
    >
      {items.map((item) => {
        const isRevealed = revealedWordIds.has(item.wordId);
        const isMatched = matchedWordIds.has(item.wordId);

        return (
          <div
            key={item.wordId}
            role="group"
            // 미개봉 카드는 단어가 스크린리더로도 새어 나가지 않게 이름을 붙이지 않는다.
            aria-label={isRevealed ? `${item.word}, ${isMatched ? '사용됨' : '미사용'}` : undefined}
            className={[
              'relative rounded-xl',
              isMatched ? 'ring-2 ring-violet-500 ring-offset-1' : '',
            ].join(' ')}
          >
            <DailyWordCard
              dailyWord={item}
              revealed={isRevealed}
              onReveal={() => onReveal(item.wordId)}
            />

            {isMatched && (
              <span
                aria-hidden="true"
                className="absolute -right-1 -top-1 z-10 flex size-4 items-center justify-center rounded-full bg-violet-600 text-[9px] font-bold text-white"
              >
                ✓
              </span>
            )}
          </div>
        );
      })}
    </div>
  );
}
