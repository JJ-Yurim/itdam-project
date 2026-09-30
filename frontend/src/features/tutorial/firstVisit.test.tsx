import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';

import { FirstVisitTutorialGate } from './FirstVisitTutorialGate';
import { clearTutorialSeenForTest, hasSeenTutorial, markTutorialSeen } from './firstVisit';

function CurrentPath() {
  return <p>{useLocation().pathname}</p>;
}

function renderGate() {
  render(
    <MemoryRouter initialEntries={['/library']}>
      <Routes>
        <Route
          path="/library"
          element={
            <FirstVisitTutorialGate>
              <CurrentPath />
            </FirstVisitTutorialGate>
          }
        />
        <Route path="/tutorial" element={<CurrentPath />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('최초 방문 튜토리얼', () => {
  afterEach(() => {
    clearTutorialSeenForTest();
  });

  it('방문 기록이 없으면 튜토리얼로 자동 이동한다', () => {
    renderGate();

    expect(screen.getByText('/tutorial')).toBeInTheDocument();
  });

  it('튜토리얼을 본 사용자는 요청한 서비스 화면을 연다', () => {
    markTutorialSeen();
    expect(hasSeenTutorial()).toBe(true);

    renderGate();

    expect(screen.getByText('/library')).toBeInTheDocument();
  });
});
