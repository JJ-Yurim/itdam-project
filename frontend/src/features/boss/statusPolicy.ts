import type { BossRunStatus } from './types';

export interface BossRunStatusPolicy {
  /** 상태 뱃지에 표시할 이름 */
  label: string;
  /** 상태 뱃지 색상 클래스 */
  badgeClassName: string;
  /** 문장 제출(전투 진입) 가능 여부 */
  canSubmit: boolean;
  /** 제출이 차단된 상태에서 사용자에게 보여줄 안내 */
  blockedMessage: string | null;
  /**
   * 종료된 회차인지.
   * 예정·진행 중은 탭으로 이미 구분되므로 상태 뱃지를 숨기고,
   * 한 탭에 모인 종료 회차만 결과를 뱃지로 구분한다.
   */
  isEnded: boolean;
}

export const bossRunStatusPolicies: Record<BossRunStatus, BossRunStatusPolicy> = {
  SCHEDULED: {
    label: '예정',
    badgeClassName: 'bg-blue-100 text-blue-700',
    canSubmit: false,
    blockedMessage: '아직 시작 전인 원정입니다. 시작 후에 문장을 제출할 수 있어요.',
    isEnded: false,
  },
  ACTIVE: {
    label: '진행 중',
    badgeClassName: 'bg-red-100 text-red-700',
    canSubmit: true,
    blockedMessage: null,
    isEnded: false,
  },
  CLEARED: {
    label: '클리어',
    badgeClassName: 'bg-emerald-100 text-emerald-700',
    canSubmit: false,
    blockedMessage: '클리어된 원정에는 새 문장을 제출할 수 없어요.',
    isEnded: true,
  },
  ENDED_UNCLEARED: {
    label: '미클리어 종료',
    badgeClassName: 'bg-stone-200 text-stone-700',
    canSubmit: false,
    blockedMessage:
      '기간 안에 클리어하지 못하고 종료된 원정입니다. 이번 회차의 공동 책은 생성되지 않아요.',
    isEnded: true,
  },
  CLOSED: {
    label: '마감',
    badgeClassName: 'bg-stone-200 text-stone-500',
    canSubmit: false,
    blockedMessage: '종료되어 보관 중인 원정입니다.',
    isEnded: true,
  },
};

// 백엔드에 새 상태값이 추가돼도 화면이 TypeError로 깨지지 않도록 방어한다.
const unknownStatusPolicy: BossRunStatusPolicy = {
  label: '알 수 없음',
  badgeClassName: 'bg-stone-200 text-stone-500',
  canSubmit: false,
  blockedMessage: '지금은 문장을 제출할 수 없는 원정입니다.',
  isEnded: false,
};

export function getBossRunStatusPolicy(status: BossRunStatus): BossRunStatusPolicy {
  return bossRunStatusPolicies[status] ?? unknownStatusPolicy;
}
