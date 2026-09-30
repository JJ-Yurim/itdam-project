import '@testing-library/jest-dom/vitest';
import { cleanup, configure } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';

import { resetAuthMockState } from '@/mocks/handlers/auth.handlers';
import { resetBossMockState } from '@/mocks/handlers/boss.handlers';
import { resetFeedMockState } from '@/mocks/handlers/feed.handlers';
import { resetLibraryMockState } from '@/mocks/handlers/library.handlers';
import { resetRaidAdminMockState } from '@/mocks/handlers/raidAdmin.handlers';
import { server } from '@/mocks/server';

// findBy*·waitFor의 기본 대기 시간은 1초다. CI가 로컬보다 느릴 수 있어 여유를 둔다.
// 요소가 아예 나타나지 않는 진짜 실패는 여전히 실패한다.
configure({ asyncUtilTimeout: 3_000 });

beforeAll(() => {
  server.listen({ onUnhandledRequest: 'error' });

  // jsdom은 dialog의 모달 API를 구현하지 않으므로 모든 테스트에서 공용 스텁을 쓴다.
  HTMLDialogElement.prototype.showModal = function showModal() {
    this.open = true;
  };
  HTMLDialogElement.prototype.close = function close() {
    this.open = false;
  };
});

afterEach(() => {
  cleanup();
  server.resetHandlers();
  resetAuthMockState();
  resetBossMockState();
  resetFeedMockState();
  resetLibraryMockState();
  resetRaidAdminMockState();
  localStorage.clear();
});

afterAll(() => {
  server.close();
});
