import { useState } from 'react';

interface ProfileAvatarProps {
  nickname: string;
  /** 크기와 이모지 크기를 함께 지정한다. 예: "h-9 w-9 text-base" */
  className?: string;
}

// 프로필 이미지는 아직 서버 계약이 없어 모든 사용자가 같은 기본 아바타를 쓴다.
// public/images/avatar-default.png 파일을 추가하면 그대로 표시된다.
export function ProfileAvatar({ nickname, className = 'h-9 w-9 text-base' }: ProfileAvatarProps) {
  const [hasError, setHasError] = useState(false);

  if (hasError) {
    return (
      <span
        aria-hidden="true"
        className={[
          'flex shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-violet-200 to-violet-400',
          className,
        ].join(' ')}
      >
        🧙
      </span>
    );
  }

  return (
    <img
      src="/images/avatar-default.png"
      alt={`${nickname} 프로필 이미지`}
      className={['shrink-0 rounded-full object-cover', className].join(' ')}
      onError={() => setHasError(true)}
    />
  );
}
