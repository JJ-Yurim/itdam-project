import { useRef } from 'react';
import type { ReactNode } from 'react';
import { useSearchParams } from 'react-router-dom';

export interface AdminTab {
  id: string;
  label: string;
}

interface AdminTabsProps {
  label: string;
  tabs: AdminTab[];
  renderPanel: (tabId: string) => ReactNode;
}

/**
 * 관리 탭. 선택 탭을 URL `tab` 파라미터에 두어 새로고침과 링크 공유가 같은 화면을 재현하게 한다.
 * 탭 전환은 히스토리에 쌓아 뒤로가기로 이전 탭에 돌아올 수 있게 한다.
 *
 * 탭을 옮길 때는 `tab`만 남기고 나머지 검색 파라미터를 버린다. 검색어·상태·페이지는 탭마다
 * 뜻이 다르므로(예: 보스 상태와 레이드 상태), 남겨 두면 다음 탭이 남의 조건을 자기 것으로 읽는다.
 */
export function AdminTabs({ label, tabs, renderPanel }: AdminTabsProps) {
  const [searchParams, setSearchParams] = useSearchParams();
  const tabRefs = useRef<(HTMLButtonElement | null)[]>([]);

  const requestedTab = searchParams.get('tab');
  const activeIndex = Math.max(
    tabs.findIndex((tab) => tab.id === requestedTab),
    0,
  );
  // 권한이나 기능 여부로 탭 목록을 계산하는 호출자가 생기면 빈 배열이 들어올 수 있다.
  const activeTab = tabs[activeIndex];

  const selectTab = (index: number) => {
    setSearchParams(new URLSearchParams({ tab: tabs[index].id }));
  };

  const moveFocus = (index: number) => {
    tabRefs.current[index]?.focus();
    selectTab(index);
  };

  const handleKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === 'ArrowRight') {
      event.preventDefault();
      moveFocus((activeIndex + 1) % tabs.length);
    }
    if (event.key === 'ArrowLeft') {
      event.preventDefault();
      moveFocus((activeIndex - 1 + tabs.length) % tabs.length);
    }
    if (event.key === 'Home') {
      event.preventDefault();
      moveFocus(0);
    }
    if (event.key === 'End') {
      event.preventDefault();
      moveFocus(tabs.length - 1);
    }
  };

  if (!activeTab) return null;

  return (
    <div>
      <div
        role="tablist"
        aria-label={label}
        className="flex flex-wrap gap-1 border-b border-stone-200"
        onKeyDown={handleKeyDown}
      >
        {tabs.map((tab, index) => {
          const isActive = index === activeIndex;
          return (
            <button
              key={tab.id}
              ref={(element) => {
                tabRefs.current[index] = element;
              }}
              type="button"
              role="tab"
              id={`admin-tab-${tab.id}`}
              aria-selected={isActive}
              aria-controls={`admin-tabpanel-${tab.id}`}
              tabIndex={isActive ? 0 : -1}
              onClick={() => selectTab(index)}
              className={[
                'min-h-11 rounded-t-lg px-4 py-2 text-sm font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-violet-600',
                isActive
                  ? 'border-b-2 border-violet-600 text-violet-800'
                  : 'text-stone-500 hover:bg-stone-50 hover:text-stone-800',
              ].join(' ')}
            >
              {tab.label}
            </button>
          );
        })}
      </div>

      <div
        role="tabpanel"
        id={`admin-tabpanel-${activeTab.id}`}
        aria-labelledby={`admin-tab-${activeTab.id}`}
        tabIndex={0}
        className="pt-5"
      >
        {renderPanel(activeTab.id)}
      </div>
    </div>
  );
}
