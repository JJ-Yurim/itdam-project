import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { BookGenerationStatusPanel } from './BookGenerationStatusPanel';

function renderBookGenerationStatusPanel(bossRunId: number) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <BookGenerationStatusPanel bossRunId={bossRunId} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('BookGenerationStatusPanel', () => {
  it('내부 작업 정보 대신 세계관에 맞는 책 생성 안내를 표시한다', async () => {
    renderBookGenerationStatusPanel(7);

    expect(
      await screen.findByText('이야기 마법사가 모두의 문장을 엮고 있어요'),
    ).toBeInTheDocument();
    expect(screen.getByText('이야기 엮는 중')).toBeInTheDocument();
    expect(screen.queryByText('AI 작업 상태')).not.toBeInTheDocument();
    expect(screen.queryByText('요청 시각')).not.toBeInTheDocument();
    expect(screen.queryByText('최근 갱신')).not.toBeInTheDocument();
    expect(screen.queryByText('책 ID')).not.toBeInTheDocument();
    expect(screen.queryByText('작업 ID')).not.toBeInTheDocument();
  });

  it('완료 상태이면 도서관 이동 링크를 표시한다', async () => {
    renderBookGenerationStatusPanel(8);

    expect(await screen.findByText('모두의 문장이 한 권의 책이 되었어요')).toBeInTheDocument();

    expect(
      screen.getByRole('link', {
        name: '도서관에서 보기',
      }),
    ).toHaveAttribute('href', '/library/books/19');
  });

  it('실패 상태이면 관리자 재처리 안내를 표시한다', async () => {
    renderBookGenerationStatusPanel(9);

    expect(await screen.findByText('이야기를 엮는 마법이 잠시 멈췄어요')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('관리자에게 알려 주세요');
  });

  it('조회 실패 시 오류와 재시도 버튼을 표시한다', async () => {
    renderBookGenerationStatusPanel(999);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '공동 책 생성 상태를 불러오지 못했어요',
    );

    expect(
      screen.getByRole('button', {
        name: '다시 시도',
      }),
    ).toBeInTheDocument();
  });
});
