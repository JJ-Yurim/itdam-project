import { FadeInImage } from '@/shared/components';

interface BossImageProps {
  imageUrl?: string | null;
  alt: string;
  /** 원형 이미지 크기 클래스 (기본 size-20) */
  sizeClassName?: string;
}

/** 보스 원형 이미지. 이미지가 없는 회차는 그라데이션 플레이스홀더를 보여준다. */
export function BossImage({ imageUrl, alt, sizeClassName = 'size-20' }: BossImageProps) {
  if (!imageUrl) {
    return (
      <div
        aria-hidden="true"
        className={`${sizeClassName} rounded-full bg-gradient-to-br from-violet-300 to-blue-300 shadow-inner ring-4 ring-violet-100`}
      />
    );
  }

  return (
    <FadeInImage
      src={imageUrl}
      alt={alt}
      className={`${sizeClassName} rounded-full object-cover ring-4 ring-violet-100`}
    />
  );
}
