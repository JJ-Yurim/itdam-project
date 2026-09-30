import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { FadeInImage } from './FadeInImage';

describe('FadeInImage', () => {
  it('로드가 끝나면 이미지를 드러낸다', () => {
    render(<FadeInImage src="/cover.png" alt="표지" />);

    const image = screen.getByAltText('표지');
    expect(image).toHaveClass('opacity-0');

    fireEvent.load(image);

    expect(image).toHaveClass('opacity-100');
  });

  it('로드에 실패해도 이미지를 드러내고 호출부 onError를 이어서 실행한다', () => {
    const onError = vi.fn();
    render(<FadeInImage src="/broken.png" alt="표지" onError={onError} />);

    const image = screen.getByAltText('표지');
    fireEvent.error(image);

    // 투명한 상태로 남으면 대체 텍스트도 보이지 않는 빈 칸이 된다.
    expect(image).toHaveClass('opacity-100');
    expect(onError).toHaveBeenCalledOnce();
  });

  it('src가 바뀌면 새 이미지 기준으로 다시 페이드한다', () => {
    const { rerender } = render(<FadeInImage src="/first.png" alt="표지" />);

    fireEvent.load(screen.getByAltText('표지'));
    expect(screen.getByAltText('표지')).toHaveClass('opacity-100');

    rerender(<FadeInImage src="/second.png" alt="표지" />);

    expect(screen.getByAltText('표지')).toHaveClass('opacity-0');
  });

  it('지연 로딩을 기본값으로 쓴다', () => {
    render(<FadeInImage src="/cover.png" alt="표지" />);

    expect(screen.getByAltText('표지')).toHaveAttribute('loading', 'lazy');
  });
});
