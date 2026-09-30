import { describe, expect, it } from 'vitest';

import { formatWeekLabel } from './weekLabel';

describe('formatWeekLabel', () => {
  it('달의 첫 7일 구간에서 시작하면 첫째 주로 표시한다', () => {
    expect(formatWeekLabel('2026-08-03')).toBe('8월 첫째 주');
  });

  it('월 첫날이 주 시작일이어도 타임존과 무관하게 그 달 첫째 주로 표시한다', () => {
    expect(formatWeekLabel('2026-08-01')).toBe('8월 첫째 주');
  });

  it('8일에서 14일 사이에 시작하면 둘째 주로 표시한다', () => {
    expect(formatWeekLabel('2026-08-10')).toBe('8월 둘째 주');
  });

  it('달의 마지막 날짜에 시작하면 다섯째 주로 표시한다', () => {
    expect(formatWeekLabel('2026-08-31')).toBe('8월 다섯째 주');
  });

  it('파싱할 수 없는 값이면 이번 주로 대체한다', () => {
    expect(formatWeekLabel('invalid')).toBe('이번 주');
  });
});
