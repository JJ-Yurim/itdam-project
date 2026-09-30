import { useCallback, useState } from 'react';
import type { ComponentPropsWithoutRef, SyntheticEvent } from 'react';

/**
 * 로드가 끝나면 부드럽게 나타나는 이미지. 지연 로딩이 기본값이다.
 * 컨테이너가 비율을 고정하고 있어야 레이아웃 이동 없이 페이드만 일어난다.
 *
 * 로드 실패도 "표시 완료"로 취급한다. 그렇지 않으면 투명한 상태로 남아
 * 대체 텍스트나 깨진 이미지 표시조차 보이지 않는 빈 칸이 된다.
 */
export function FadeInImage({
  className,
  onLoad,
  onError,
  ...props
}: ComponentPropsWithoutRef<'img'>) {
  // 어떤 src를 다 보여줬는지로 판정한다. src가 바뀌면 자동으로 다시 페이드된다.
  const [settledSrc, setSettledSrc] = useState<string | undefined>(undefined);
  const isSettled = settledSrc === props.src;

  // 캐시된 이미지는 onLoad가 리스너 부착 전에 끝나 있을 수 있다.
  // 마운트 시점에 이미 완료된 이미지는 페이드 없이 바로 보여준다.
  const handleRef = useCallback(
    (element: HTMLImageElement | null) => {
      if (element?.complete) {
        setSettledSrc(props.src);
      }
    },
    [props.src],
  );

  const handleLoad = (event: SyntheticEvent<HTMLImageElement>) => {
    setSettledSrc(props.src);
    onLoad?.(event);
  };

  const handleError = (event: SyntheticEvent<HTMLImageElement>) => {
    setSettledSrc(props.src);
    onError?.(event);
  };

  return (
    <img
      loading="lazy"
      {...props}
      ref={handleRef}
      onLoad={handleLoad}
      onError={handleError}
      className={[
        'transition-opacity duration-200 motion-reduce:transition-none',
        isSettled ? 'opacity-100' : 'opacity-0',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    />
  );
}
