import type { DailyWord } from '../types';

import { DailyWordCard } from './DailyWordCard';

interface DailyWordListProps {
  items: DailyWord[];
  /** 개봉 상태를 밖에서 관리할 때 전달한다. 없으면 카드 내부 상태로 동작한다. */
  revealedWordIds?: Set<number>;
  onReveal?: (wordId: number) => void;
}

export function DailyWordList({ items, revealedWordIds, onReveal }: DailyWordListProps) {
  return (
    <div
      aria-label="오늘의 단어 카드"
      // 카드는 폭에 비례해 커지므로 페이지 컨테이너가 넓을수록 커진다. 홈·개인 문장 생성 화면이
      // 저마다 다른 폭을 쓰므로 카드팩이 직접 상한을 갖게 해 어느 화면에서나 같은 크기로 보이게 한다.
      // 폭을 바꿀 때는 DraftWordPack도 같이 맞춰야 한다.
      className="grid grid-cols-5 gap-1 min-[360px]:gap-1.5 min-[400px]:gap-2 lg:mx-auto lg:max-w-[700px] lg:gap-3"
    >
      {items.map((dailyWord) => (
        <DailyWordCard
          key={dailyWord.wordId}
          dailyWord={dailyWord}
          revealed={revealedWordIds?.has(dailyWord.wordId)}
          onReveal={onReveal ? () => onReveal(dailyWord.wordId) : undefined}
        />
      ))}
    </div>
  );
}
