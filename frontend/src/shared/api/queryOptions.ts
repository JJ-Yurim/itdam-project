// 비동기로 확정되는 상태를 다루므로 재진입 시 항상 서버 상태를 다시 확인한다.
export const ALWAYS_REFRESH_ON_MOUNT = { staleTime: 0 } as const;
