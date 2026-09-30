import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { BookContributorList } from './BookContributorList';

const contributors = Array.from({ length: 8 }, (_, index) => ({
  userId: index + 1,
  nickname: `모험가${index + 1}`,
}));

describe('BookContributorList', () => {
  it('기여자가 많으면 여섯 명씩 나누고 모든 기여자를 페이지로 확인한다', async () => {
    const user = userEvent.setup();
    render(<BookContributorList contributors={contributors} />);

    const list = screen.getByRole('list', { name: '기여 작가 전체 목록' });
    expect(within(list).getAllByRole('listitem')).toHaveLength(6);
    expect(screen.getByRole('status')).toHaveTextContent('전체 8명 중 1–6명');
    expect(screen.getByText('1 / 2')).toBeInTheDocument();
    expect(screen.queryByText('모험가7')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '다음' }));

    expect(within(list).getAllByRole('listitem')).toHaveLength(2);
    expect(screen.getByRole('status')).toHaveTextContent('전체 8명 중 7–8명');
    expect(screen.getByText('모험가7')).toBeInTheDocument();
    expect(screen.getByText('모험가8')).toBeInTheDocument();
    expect(screen.getByText('2 / 2')).toBeInTheDocument();
  });

  it('한 페이지 이내면 페이지 이동 UI를 표시하지 않는다', () => {
    render(<BookContributorList contributors={contributors.slice(0, 5)} />);

    expect(screen.getByRole('list', { name: '기여 작가 전체 목록' })).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: '기여 작가 페이지 이동' })).not.toBeInTheDocument();
  });
});
