import { describe, expect, it } from 'vitest';

import { getLibraryBook, getLibraryBooks } from './api';

describe('getLibraryBooks', () => {
  it('완성된 공동 책 목록을 페이지 단위로 조회한다', async () => {
    const result = await getLibraryBooks({
      page: 0,
      size: 2,
    });

    expect(result.content).toHaveLength(2);
    expect(result.content[0]?.bookId).toBe(18);
    expect(result.content[0]?.bossName).toBe('안개의 수호자');
    expect(result.content[0]?.contentCount).toBe(8);

    expect(result.page).toBe(0);
    expect(result.size).toBe(2);
    expect(result.totalElements).toBe(3);
    expect(result.totalPages).toBe(2);
    expect(result.hasNext).toBe(true);
  });
});

describe('getLibraryBook', () => {
  it('공동 책 ID로 상세 정보와 수록 본문을 조회한다', async () => {
    const result = await getLibraryBook(18);

    expect(result.bookId).toBe(18);
    expect(result.bossName).toBe('안개의 수호자');
    expect(result.contents).toHaveLength(3);
    expect(result.contents[0]).toMatchObject({
      contentId: 101,
      sequenceNo: 1,
      contentType: 'USER_ORIGINAL',
      contributor: {
        userId: 1,
        nickname: '문장여행자',
      },
    });
  });

  it('존재하지 않는 공동 책이면 404 오류를 전달한다', async () => {
    await expect(getLibraryBook(999)).rejects.toMatchObject({
      response: {
        status: 404,
        data: {
          code: 'NOT_FOUND',
          message: '공동 책을 찾을 수 없습니다.',
          fieldErrors: [],
        },
      },
    });
  });
});
