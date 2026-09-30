const TUTORIAL_SEEN_STORAGE_KEY = 'itdam:tutorial-seen:v1';

function getStorage() {
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

export function hasSeenTutorial() {
  try {
    const storage = getStorage();
    if (!storage) return true;

    return storage.getItem(TUTORIAL_SEEN_STORAGE_KEY) === 'true';
  } catch {
    // 저장할 수 없는 환경에서 매번 강제 이동되는 루프를 피한다.
    return true;
  }
}

export function markTutorialSeen() {
  try {
    getStorage()?.setItem(TUTORIAL_SEEN_STORAGE_KEY, 'true');
  } catch {
    // 저장소가 막힌 환경에서도 튜토리얼 자체는 정상적으로 진행한다.
  }
}

export function clearTutorialSeenForTest() {
  try {
    getStorage()?.removeItem(TUTORIAL_SEEN_STORAGE_KEY);
  } catch {
    // 테스트 정리용이며 저장소를 쓸 수 없다면 할 일이 없다.
  }
}
