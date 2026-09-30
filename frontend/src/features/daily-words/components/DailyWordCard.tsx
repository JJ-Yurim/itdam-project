import { useEffect, useRef, useState } from 'react';

import { Button, Modal } from '@/shared/components';

import type { DailyWord } from '../types';

interface DailyWordCardProps {
  dailyWord: DailyWord;
  /** 개봉 상태를 밖에서 관리할 때 전달한다. 없으면 카드 내부 상태로 동작한다. */
  revealed?: boolean;
  onReveal?: () => void;
}

// 카드 두 면의 디자인 에셋. 다섯 장이 같은 이미지를 공유한다.
const COVER_IMAGE_SRC = '/images/word-card-cover.png';
const FRAME_IMAGE_SRC = '/images/word-card-frame.png';

/**
 * 카드 면 배경 이미지.
 * 파일이 없거나 로드에 실패하면 아무것도 그리지 않아 아래의 배경색이 그대로 보인다.
 */
function CardFaceImage({ src }: { src: string }) {
  const [hasFailed, setHasFailed] = useState(false);

  if (hasFailed) {
    return null;
  }

  return (
    <img
      src={src}
      alt=""
      aria-hidden="true"
      className="pointer-events-none absolute inset-0 size-full rounded-xl object-cover"
      onError={() => setHasFailed(true)}
    />
  );
}

export function DailyWordCard({ dailyWord, revealed, onReveal }: DailyWordCardProps) {
  const [internalRevealed, setInternalRevealed] = useState(false);
  const isRevealed = revealed ?? internalRevealed;
  // 개봉한 카드를 다시 앞면으로 뒤집어 둘 수 있다. 개봉 상태(저장·집계)는 유지되고
  // 카드 안에서만 보이는 면을 토글한다.
  const [isFlippedBack, setIsFlippedBack] = useState(false);
  const isShowingWord = isRevealed && !isFlippedBack;
  const [isMeaningOpen, setIsMeaningOpen] = useState(false);
  const meaningButtonRef = useRef<HTMLButtonElement>(null);
  const frontButtonRef = useRef<HTMLButtonElement>(null);
  const shouldFocusMeaningRef = useRef(false);
  const shouldFocusFrontRef = useRef(false);

  const handleReveal = () => {
    // 포커스 이동은 카드 버튼으로 직접 개봉한 경우에만 한다.
    // 타이핑 중 자동 개봉(controlled reveal)이 입력 포커스를 뺏으면 안 된다.
    shouldFocusMeaningRef.current = true;
    setIsFlippedBack(false);

    if (revealed === undefined) {
      setInternalRevealed(true);
    }
    onReveal?.();
  };

  const handleFlipBack = () => {
    shouldFocusFrontRef.current = true;
    setIsFlippedBack(true);
  };

  // 방금 눌린 면의 버튼은 접근성 트리에서 빠지므로, 포커스가 숨겨지는
  // 요소에 남지 않도록 렌더 후 반대 면의 버튼으로 옮긴다.
  useEffect(() => {
    if (isShowingWord && shouldFocusMeaningRef.current) {
      shouldFocusMeaningRef.current = false;
      meaningButtonRef.current?.focus();
    }

    if (!isShowingWord && shouldFocusFrontRef.current) {
      shouldFocusFrontRef.current = false;
      frontButtonRef.current?.focus();
    }
  }, [isShowingWord]);

  return (
    /* 카드 슬롯 비율은 두 디자인 에셋의 비율(약 0.69)에 맞춰 여백 없이 꽉 차게 한다. */
    <div className="min-w-0 aspect-[69/100] w-full [perspective:1000px]">
      <span
        className={[
          'relative block size-full [transform-style:preserve-3d] transition-transform duration-500 motion-reduce:transition-none',
          isShowingWord ? '[transform:rotateY(180deg)]' : '',
        ].join(' ')}
      >
        {/* 가림면: 카드 뒷면 이미지만 보여주는 뒤집기 버튼.
            카드를 여는 안내는 목록 위 설명 문구가 담당하므로 면 안에는 글자를 두지 않는다.
            뒷면이 보이는 동안은 상호작용과 접근성 트리에서 제외한다. */}
        <button
          ref={frontButtonRef}
          type="button"
          aria-label={`${dailyWord.position}번째 단어 카드 뒤집기`}
          aria-hidden={isShowingWord}
          tabIndex={isShowingWord ? -1 : 0}
          onClick={handleReveal}
          className={[
            'absolute inset-0 overflow-hidden rounded-xl bg-gradient-to-br from-violet-400 to-violet-600 shadow-sm [backface-visibility:hidden] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600',
            isShowingWord ? 'pointer-events-none' : '',
          ].join(' ')}
        >
          <CardFaceImage src={COVER_IMAGE_SRC} />
        </button>

        {/* 공개면: 프레임 이미지 위에 품사 · 단어 · 의미보기를 얹는다.
            의미보기 밖을 누르면 다시 가림면으로 뒤집힌다. */}
        <span
          aria-hidden={!isShowingWord}
          className={[
            'absolute inset-0 flex min-w-0 [transform:rotateY(180deg)] flex-col overflow-hidden rounded-xl bg-[#fbf8f3] text-center shadow-sm [backface-visibility:hidden]',
            isShowingWord ? '' : 'pointer-events-none',
          ].join(' ')}
        >
          <CardFaceImage src={FRAME_IMAGE_SRC} />

          <button
            type="button"
            aria-label={`${dailyWord.word} 카드 다시 뒤집기`}
            tabIndex={isShowingWord ? 0 : -1}
            onClick={handleFlipBack}
            className="absolute inset-0 rounded-xl focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600"
          />

          {/* 테두리 장식을 덮지 않도록 프레임 안쪽 여백만큼 띄운다.
              래퍼는 클릭을 통과시키고 의미보기 버튼만 다시 받는다. */}
          <span className="pointer-events-none relative flex min-w-0 flex-1 flex-col items-center justify-between p-[12%]">
            <span className="max-w-full truncate text-[clamp(8px,2.1vw,10px)] font-semibold text-violet-700">
              {dailyWord.partOfSpeech}
            </span>

            <span className="max-w-full overflow-hidden text-ellipsis whitespace-nowrap text-[clamp(10px,3.1vw,15px)] font-extrabold text-stone-900">
              {dailyWord.word}
            </span>

            <button
              ref={meaningButtonRef}
              type="button"
              aria-label={`${dailyWord.word} 의미보기`}
              tabIndex={isShowingWord ? 0 : -1}
              onClick={() => setIsMeaningOpen(true)}
              className="pointer-events-auto relative z-10 flex min-h-6 w-full shrink-0 items-center justify-center py-0.5 leading-tight text-violet-700 transition hover:scale-110 focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-violet-600 motion-reduce:hover:scale-100"
            >
              {/* 이모지는 OS마다 모양이 달라 카드 아트와 어긋난다. 선화 SVG로 통일한다. */}
              <svg
                aria-hidden="true"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2.2"
                strokeLinecap="round"
                className="size-[clamp(12px,3vw,16px)]"
              >
                <circle cx="11" cy="11" r="6.5" />
                <path d="m16 16 4.5 4.5" />
              </svg>
            </button>
          </span>
        </span>
      </span>

      {isMeaningOpen && (
        <Modal
          open
          title={dailyWord.word}
          onClose={() => setIsMeaningOpen(false)}
          footer={
            <Button variant="secondary" onClick={() => setIsMeaningOpen(false)}>
              닫기
            </Button>
          }
        >
          <p className="text-sm font-semibold text-violet-700">{dailyWord.partOfSpeech}</p>

          <p className="mt-2 text-base leading-7 text-stone-800">{dailyWord.definition}</p>

          {dailyWord.example && (
            <p className="mt-4 rounded-xl bg-stone-50 p-4 text-sm leading-6 text-stone-600">
              예문 · {dailyWord.example}
            </p>
          )}
        </Modal>
      )}
    </div>
  );
}
