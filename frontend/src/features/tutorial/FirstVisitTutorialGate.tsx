import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';

import { hasSeenTutorial } from './firstVisit';

interface FirstVisitTutorialGateProps {
  children: ReactNode;
}

/** 첫 서비스 방문만 튜토리얼로 보내고 이후에는 원래 요청한 화면을 그대로 연다. */
export function FirstVisitTutorialGate({ children }: FirstVisitTutorialGateProps) {
  if (!hasSeenTutorial()) {
    return <Navigate to="/tutorial" replace />;
  }

  return children;
}
