import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

describe('테스트 환경', () => {
  it('React 컴포넌트를 DOM에 렌더링한다', () => {
    render(<button>확인</button>);

    expect(screen.getByRole('button', { name: '확인' })).toBeInTheDocument();
  });
});
