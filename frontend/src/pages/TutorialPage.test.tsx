import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { clearTutorialSeenForTest, hasSeenTutorial } from '@/features/tutorial/firstVisit';

import { TutorialPage } from './TutorialPage';

function renderTutorial(initialEntry = '/tutorial') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <TutorialPage />
    </MemoryRouter>,
  );
}

describe('TutorialPage', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    Reflect.deleteProperty(HTMLElement.prototype, 'scrollIntoView');
    clearTutorialSeenForTest();
  });

  it('튜토리얼 나가기를 취소하면 현재 화면에 머문다', async () => {
    const user = userEvent.setup();
    renderTutorial();

    await user.click(screen.getByRole('button', { name: '튜토리얼 나가기' }));

    const exitDialog = screen.getByRole('dialog', { name: '튜토리얼을 나갈까요?' });
    expect(exitDialog).toHaveTextContent('지금까지의 튜토리얼 진행 내용은 저장되지 않아요.');
    await user.click(within(exitDialog).getByRole('button', { name: '계속하기' }));

    expect(screen.queryByRole('dialog', { name: '튜토리얼을 나갈까요?' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '다미 안내 펼치기' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '오늘의 단어' })).toBeInTheDocument();
  });

  it('시작 안내는 전체 화면을 어둡게 하고 대상 안내로 넘어가면 스포트라이트만 남긴다', async () => {
    const user = userEvent.setup();
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({
      x: 24,
      y: 120,
      top: 120,
      right: 424,
      bottom: 240,
      left: 24,
      width: 400,
      height: 120,
      toJSON: () => ({}),
    } as DOMRect);
    renderTutorial();

    expect(screen.getByTestId('tutorial-guide-backdrop')).toBeInTheDocument();
    expect(screen.getByTestId('dami-speech-bubble')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '튜토리얼 시작' }));

    expect(screen.queryByTestId('tutorial-guide-backdrop')).not.toBeInTheDocument();
    expect(screen.getByTestId('tutorial-spotlight')).toBeInTheDocument();
  });

  it('다섯 단어를 모두 열 때까지 카드 영역을 강조한 뒤 다음 버튼으로 이동한다', async () => {
    const user = userEvent.setup();
    renderTutorial();

    await user.click(screen.getByRole('button', { name: '튜토리얼 시작' }));
    for (const index of [1, 2, 3, 4]) {
      await user.click(screen.getByRole('button', { name: `${index}번째 단어 카드 뒤집기` }));
    }

    expect(document.getElementById('tutorial-coach-words')).toHaveClass('tutorial-coachmark');
    expect(screen.getByRole('button', { name: '오늘의 단어로 문장 만들기' })).toBeDisabled();
    expect(screen.getByText('4/5개').parentElement).toHaveTextContent('1개를 더 열어보세요.');

    await user.click(screen.getByRole('button', { name: '5번째 단어 카드 뒤집기' }));

    expect(document.getElementById('tutorial-coach-words')).not.toHaveClass('tutorial-coachmark');
    expect(document.getElementById('tutorial-coach-next')).toHaveClass('tutorial-coachmark');
    expect(screen.getByRole('button', { name: '오늘의 단어로 문장 만들기' })).toBeEnabled();
  });

  it('문장 입력 영역 전체를 강조하고 오늘의 단어가 없어도 평가를 허용한다', async () => {
    const user = userEvent.setup();
    renderTutorial();

    await user.click(screen.getByRole('button', { name: '튜토리얼 시작' }));
    for (const index of [1, 2, 3, 4, 5]) {
      await user.click(screen.getByRole('button', { name: `${index}번째 단어 카드 뒤집기` }));
    }
    await user.click(screen.getByRole('button', { name: '오늘의 단어로 문장 만들기' }));

    const composeTarget = document.getElementById('tutorial-coach-compose');
    const sentenceInput = screen.getByLabelText('나의 문장');
    const composeGuide = screen.getByRole('status', {
      name: '다음 행동: 이번에는 내가 도와줄게! 예시 문장을 눌러봐',
    });
    const exampleButton = screen.getByRole('button', { name: '예시 문장 채우기' });
    const actionAnchor = exampleButton.parentElement;
    expect(composeTarget).toHaveClass('tutorial-coachmark');
    expect(actionAnchor).not.toBe(composeTarget);
    expect(actionAnchor).toHaveClass('relative', 'mt-12');
    expect(actionAnchor).toContainElement(composeGuide);
    expect(composeGuide).toHaveClass('left-1/2', '-translate-x-1/2');
    expect(composeGuide).not.toHaveClass('left-0');
    expect(composeTarget).toContainElement(sentenceInput);
    expect(sentenceInput).toHaveClass('focus:border-stone-300', 'focus:ring-0');
    expect(sentenceInput).not.toHaveClass('focus:border-violet-500', 'focus:ring-4');
    expect(composeTarget).toHaveClass('-mx-2', 'p-2');
    expect(composeTarget).toContainElement(exampleButton);
    expect(screen.getByText('활용한 오늘의 단어 0개')).toBeInTheDocument();

    await user.type(sentenceInput, '오');

    expect(screen.getByRole('button', { name: '예시 문장 채우기' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'AI 평가 요청하기' })).toBeEnabled();
    expect(document.getElementById('tutorial-coach-compose')).toHaveClass('tutorial-coachmark');
    expect(document.getElementById('tutorial-coach-evaluate')).not.toHaveClass(
      'tutorial-coachmark',
    );
    expect(
      screen.getByRole('status', {
        name: '다음 행동: 이번에는 내가 도와줄게! 예시 문장을 눌러봐',
      }),
    ).toBeInTheDocument();

    await user.type(sentenceInput, '늘은 조용히 걸었다.');
    await user.tab();

    expect(document.getElementById('tutorial-coach-evaluate')).toHaveClass('tutorial-coachmark');
    expect(screen.getByRole('button', { name: '예시 문장 채우기' })).toBeInTheDocument();
    expect(screen.getByText('오늘의 단어를 쓰면 점수가 더 올라가요.')).toBeInTheDocument();
  });

  it('문장 단계 안내를 대상 반대편에 두고 바깥 조작 뒤 현재 단계에서만 최소화한다', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('innerHeight', 844);
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (
      this: HTMLElement,
    ) {
      const top = this.id === 'tutorial-coach-compose' ? 620 : 120;
      return {
        x: 24,
        y: top,
        top,
        right: 366,
        bottom: top + 80,
        left: 24,
        width: 342,
        height: 80,
        toJSON: () => ({}),
      } as DOMRect;
    });
    renderTutorial();

    await user.click(screen.getByRole('button', { name: '튜토리얼 시작' }));
    await user.click(screen.getByRole('button', { name: '1번째 단어 카드 뒤집기' }));
    expect(screen.getByRole('button', { name: '다미 안내 펼치기' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '2번째 단어 카드 뒤집기' }));
    await user.click(screen.getByRole('button', { name: '3번째 단어 카드 뒤집기' }));
    await user.click(screen.getByRole('button', { name: '4번째 단어 카드 뒤집기' }));
    await user.click(screen.getByRole('button', { name: '5번째 단어 카드 뒤집기' }));
    await user.click(screen.getByRole('button', { name: '오늘의 단어로 문장 만들기' }));

    expect(screen.getByTestId('dami-speech-bubble')).toBeInTheDocument();
    expect(screen.getByLabelText('다미의 튜토리얼 안내')).toHaveClass('tutorial-dami-guide--top');

    await user.click(screen.getByRole('button', { name: '예시 문장 채우기' }));

    expect(screen.getByLabelText('나의 문장')).toHaveValue('고요한 골목에 은하수와 윤슬이 번졌다.');
    expect(document.getElementById('tutorial-coach-evaluate')).toHaveClass('tutorial-coachmark');
    expect(screen.getByRole('button', { name: '다미 안내 펼치기' })).toBeInTheDocument();
  });

  it('로그인이나 API 요청 없이 핵심 서비스 흐름을 끝까지 체험한다', async () => {
    const user = userEvent.setup();
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const scrolledTargets: HTMLElement[] = [];
    Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
      configurable: true,
      value: vi.fn(function scrollIntoView(this: HTMLElement) {
        scrolledTargets.push(this);
      }),
    });

    expect(hasSeenTutorial()).toBe(false);
    renderTutorial('/tutorial?present=1');
    expect(hasSeenTutorial()).toBe(true);

    expect(screen.queryByText('발표 모드')).not.toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: '주요 메뉴' })).toBeInTheDocument();
    expect(screen.getByTestId('tutorial-stage')).not.toHaveClass('lg:pr-[370px]', 'pb-72');
    expect(screen.getByLabelText('다미의 튜토리얼 안내')).toHaveClass(
      'tutorial-dami-guide',
      'tutorial-dami-guide--bottom',
      'lg:w-[740px]',
    );
    expect(screen.getByLabelText('다미의 튜토리얼 안내')).not.toHaveClass(
      'tutorial-dami-guide--intro',
    );
    expect(
      screen.getByText(
        '지금 보이는 실제 서비스 화면을 함께 둘러볼게. 모든 내용은 튜토리얼용이라 마음껏 눌러봐도 괜찮아!',
      ),
    ).not.toHaveClass('line-clamp-3');
    await user.click(screen.getByRole('button', { name: '튜토리얼 시작' }));
    expect(
      screen.getByRole('status', { name: '다음 행동: 단어 카드를 눌러보세요' }),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '1번째 단어 카드 뒤집기' }));
    await user.click(screen.getByRole('button', { name: '2번째 단어 카드 뒤집기' }));
    await user.click(screen.getByRole('button', { name: '3번째 단어 카드 뒤집기' }));
    await user.click(screen.getByRole('button', { name: '4번째 단어 카드 뒤집기' }));
    await user.click(screen.getByRole('button', { name: '5번째 단어 카드 뒤집기' }));
    expect(
      screen.getByRole('status', { name: '다음 행동: 다음 단계로 이동하세요' }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '오늘의 단어로 문장 만들기' }));

    expect(
      screen.getByRole('status', {
        name: '다음 행동: 이번에는 내가 도와줄게! 예시 문장을 눌러봐',
      }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '예시 문장 채우기' }));
    expect(screen.getByRole('button', { name: '예시 문장 채우기' })).toBeInTheDocument();
    expect(screen.getByLabelText('나의 문장')).toHaveValue('고요한 골목에 은하수와 윤슬이 번졌다.');
    expect(
      screen.getByRole('status', { name: '다음 행동: AI 평가를 요청하세요' }),
    ).toBeInTheDocument();
    expect(document.getElementById('tutorial-coach-evaluate')).toHaveClass('tutorial-coachmark');
    expect(screen.queryByTestId('tutorial-screen-dim')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'AI 평가 요청하기' })).toBeEnabled();
    await user.click(screen.getByRole('button', { name: 'AI 평가 요청하기' }));
    expect(screen.queryByRole('button', { name: '예시 문장 채우기' })).not.toBeInTheDocument();

    expect(await screen.findByText('83.35', {}, { timeout: 1_500 })).toBeInTheDocument();
    expect(
      screen.getByRole('status', {
        name: '안내: 기본 점수와 보너스, 문장 에너지를 확인해보세요. 화면을 눌러 계속하세요',
      }),
    ).toBeInTheDocument();
    expect(screen.queryByTestId('dami-speech-bubble')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '다미 안내 펼치기' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '화면을 눌러 다음 평가 안내 보기' }));

    expect(
      screen.getByRole('status', {
        name: '안내: 세부 점수와 감지된 분위기를 확인해보세요. 화면을 눌러 계속하세요',
      }),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '다미 안내 펼치기' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '화면을 눌러 다음 평가 안내 보기' }));

    expect(
      screen.getByRole('status', { name: '다음 행동: 평가된 문장으로 원정에 참여하세요' }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: '화면을 눌러 다음 평가 안내 보기' }),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '이 문장으로 원정 참여하기' }));

    expect(screen.getByLabelText('다미의 튜토리얼 안내')).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: '평가된 문장으로 원정에 참여해봐!' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '다미 안내 펼치기' })).not.toBeInTheDocument();

    await waitFor(() =>
      expect(scrolledTargets.some((target) => target.id === 'tutorial-boss-hero')).toBe(true),
    );
    expect(scrolledTargets.some((target) => target.id === 'tutorial-coach-select')).toBe(false);
    expect(document.getElementById('tutorial-boss-hero')).toHaveClass('tutorial-coachmark');
    expect(screen.getByRole('img', { name: '새벽의 수호자' })).toHaveAttribute(
      'src',
      '/tutorial-assets/tutorial-boss-mountain-phase-1.png',
    );
    expect(screen.getByRole('img', { name: '새벽의 수호자' })).toHaveClass('lg:size-64');
    expect(screen.getByRole('heading', { name: '새벽의 수호자', level: 1 })).toHaveClass(
      'lg:text-3xl',
    );
    expect(
      screen.getByRole('status', {
        name: '안내: 보스의 남은 체력을 확인해보세요. 화면을 눌러 계속하세요',
      }),
    ).toBeInTheDocument();

    const bossTouchLayer = screen.getByRole('button', {
      name: '화면을 눌러 다음 보스 안내 보기',
    });
    expect(bossTouchLayer).toHaveClass('z-[47]');
    expect(screen.getByLabelText('다미의 튜토리얼 안내')).toHaveClass('z-50');

    const advanceBossGuide = () => user.click(bossTouchLayer);

    await advanceBossGuide();
    expect(screen.getByRole('button', { name: '다미 안내 펼치기' })).toBeInTheDocument();
    expect(
      screen.queryByRole('heading', { name: '평가된 문장으로 원정에 참여해봐!' }),
    ).not.toBeInTheDocument();
    await waitFor(() =>
      expect(scrolledTargets.some((target) => target.id === 'tutorial-boss-leaderboard')).toBe(
        true,
      ),
    );
    expect(document.getElementById('tutorial-boss-leaderboard')).toHaveClass('tutorial-coachmark');

    await advanceBossGuide();
    await waitFor(() =>
      expect(scrolledTargets.some((target) => target.id === 'tutorial-boss-moods')).toBe(true),
    );
    expect(document.getElementById('tutorial-boss-moods')).toHaveClass('tutorial-coachmark');

    await advanceBossGuide();
    await waitFor(() =>
      expect(scrolledTargets.some((target) => target.id === 'tutorial-boss-submission-limit')).toBe(
        true,
      ),
    );
    expect(document.getElementById('tutorial-boss-submission-limit')).toHaveClass(
      'tutorial-coachmark',
    );

    await advanceBossGuide();
    await waitFor(() =>
      expect(scrolledTargets.some((target) => target.id === 'tutorial-coach-select')).toBe(true),
    );
    expect(
      screen.getByRole('status', { name: '다음 행동: 공격에 사용할 문장을 선택하세요' }),
    ).toBeInTheDocument();
    expect(document.getElementById('tutorial-coach-select')).toHaveClass('tutorial-coachmark');

    const leaderboard = screen.getByRole('list', { name: '실시간 기여도 미리보기' });
    expect(document.getElementById('tutorial-boss-hero')?.parentElement).toHaveClass(
      'lg:grid-cols-[minmax(0,1fr)_320px]',
    );
    expect(leaderboard).toHaveClass('lg:space-y-1');
    expect(screen.getByRole('heading', { name: '실시간 기여도' })).toHaveClass('lg:text-xl');
    expect(screen.getByLabelText('실시간 순위 갱신 중')).toHaveClass('lg:text-sm');
    expect(leaderboard.querySelector('li')).toHaveClass('lg:min-h-10', 'lg:text-base');
    expect(screen.getByText('내 순위 6위')).toBeInTheDocument();
    expect(screen.getByText('내 순위 6위').closest('p')).toHaveClass(
      'lg:px-3',
      'lg:py-3',
      'lg:text-base',
    );
    expect(screen.getByRole('heading', { name: '제출 가능한 문장' })).toHaveClass('lg:text-2xl');
    const bossMoods = screen.getByLabelText('원정 분위기와 약점');
    expect(bossMoods).toHaveClass('lg:text-base');
    expect(within(bossMoods).getByText('분위기')).toBeInTheDocument();
    expect(within(bossMoods).getByText('평온')).toBeInTheDocument();
    expect(within(bossMoods).getByText('약점')).toBeInTheDocument();
    expect(within(bossMoods).getByText('신비')).toBeInTheDocument();
    expect(within(bossMoods).queryByText(/보너스|가중치|계산식/)).not.toBeInTheDocument();

    const scoreGuide = screen.getByLabelText('문장 제출 점수 기준');
    expect(scoreGuide).toHaveClass('lg:text-base');
    expect(within(scoreGuide).getByText('최소 점수')).toBeInTheDocument();
    expect(within(scoreGuide).getByText('35')).toBeInTheDocument();
    expect(within(scoreGuide).getByText('권장 점수')).toBeInTheDocument();
    expect(within(scoreGuide).getByText('50')).toBeInTheDocument();
    expect(screen.queryByText(/버전 \d+/)).not.toBeInTheDocument();
    expect(screen.queryByText('선택하기')).not.toBeInTheDocument();

    const matchedBonuses = screen.getByLabelText('분위기와 약점 일치 보너스');
    expect(matchedBonuses).toHaveClass('lg:text-sm');
    expect(within(matchedBonuses).getByText('분위기 #평온')).toBeInTheDocument();
    expect(within(matchedBonuses).getByText('+8.2')).toBeInTheDocument();
    expect(within(matchedBonuses).getByText('약점 #신비')).toBeInTheDocument();
    expect(within(matchedBonuses).getByText('+12.8')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /고요한 골목에 은하수와 윤슬이 번졌다/ }));
    await waitFor(() =>
      expect(scrolledTargets.some((target) => target.id === 'tutorial-coach-submit')).toBe(true),
    );
    await user.click(screen.getByRole('button', { name: '문장 제출하기' }));

    expect(
      await screen.findByRole('status', { name: '137 데미지' }, { timeout: 1_500 }),
    ).toBeInTheDocument();
    expect(screen.getByText('강한 공격!')).toBeInTheDocument();
    expect(screen.getByRole('status', { name: '새벽의 수호자 처치 완료' })).toBeInTheDocument();

    expect(
      await screen.findByRole('heading', { name: '집필 완료' }, { timeout: 3_000 }),
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(scrolledTargets.some((target) => target.id === 'tutorial-completed-book-action')).toBe(
        true,
      ),
    );
    await user.click(screen.getByRole('button', { name: '완성된 공동 책 보기' }));

    expect(screen.getByRole('heading', { name: '새벽의 수호자' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: '새벽의 수호자 책 표지' })).toHaveAttribute(
      'src',
      '/tutorial-assets/tutorial-book-cover-mountain.png',
    );
    expect(screen.getByLabelText('다미의 튜토리얼 안내')).toHaveClass(
      'tutorial-dami-guide--bottom',
      'tutorial-dami-guide--lowered',
    );
    const readBookButton = screen.getByRole('button', { name: '공동 소설집 전체 읽기' });
    const contributorList = screen.getByRole('list', { name: '기여 작가 전체 목록' });
    expect(
      readBookButton.compareDocumentPosition(contributorList) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    expect(within(contributorList).getAllByRole('listitem')).toHaveLength(5);
    expect(within(contributorList).getByText('문장수집가')).toBeInTheDocument();
    await user.click(readBookButton);
    expect(
      screen.getByRole('heading', { name: '이제 완성된 이야기를 직접 펼쳐봐!' }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        '보라색과 주황색으로 강조된 문장은 회원이 만든 문장이야. 문장을 눌러 작성자와 보스전 기여 정보를 확인하고, 오른쪽 페이지를 눌러 이야기를 넘겨봐.',
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '다미 안내 펼치기' })).not.toBeInTheDocument();
    expect(screen.getByRole('region', { name: '책 읽기 영역' })).toBeInTheDocument();
    expect(screen.getByTestId('tutorial-guide-backdrop')).toBeInTheDocument();
    const accessibleBook = screen.getByRole('region', { name: '공동 소설 본문' });
    expect(accessibleBook).toHaveTextContent('세 번째 장 · 우리가 만든 아침');
    ['나', '새벽작가', '달빛서기', '별빛여우'].forEach((contributor) =>
      expect(within(accessibleBook).getByText(contributor)).toBeInTheDocument(),
    );
    expect(screen.getByRole('button', { name: '공동 도서 정보로 돌아가기' })).toBeInTheDocument();

    await user.click(screen.getByRole('region', { name: '책 읽기 영역' }));
    expect(screen.queryByTestId('tutorial-guide-backdrop')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '다미 안내 펼치기' })).toBeInTheDocument();
    expect(
      screen.queryByRole('heading', { name: '이제 완성된 이야기를 직접 펼쳐봐!' }),
    ).not.toBeInTheDocument();

    const clientWidthSpy = vi
      .spyOn(HTMLElement.prototype, 'clientWidth', 'get')
      .mockImplementation(function (this: HTMLElement) {
        return this.classList.contains('book-flip-stage') ? 1_000 : 0;
      });
    const clientHeightSpy = vi
      .spyOn(HTMLElement.prototype, 'clientHeight', 'get')
      .mockImplementation(function (this: HTMLElement) {
        return this.classList.contains('book-flip-stage') ? 600 : 0;
      });
    const scrollWidthSpy = vi
      .spyOn(HTMLElement.prototype, 'scrollWidth', 'get')
      .mockImplementation(function (this: HTMLElement) {
        return this.classList.contains('book-flip-flow') ? 600 : 0;
      });
    window.dispatchEvent(new Event('resize'));

    const engineHost = screen.getByTestId('book-flip-engine');
    await waitFor(() =>
      expect(engineHost.querySelector('[data-book-content-id="101"]')).toBeInTheDocument(),
    );

    const originalContribution = engineHost.querySelector<HTMLElement>(
      '[data-book-content-id="101"]',
    )!;
    const editedContribution = engineHost.querySelector<HTMLElement>(
      '[data-book-content-id="102"]',
    )!;

    expect(originalContribution).toHaveClass('book-flip-user-sentence');
    expect(originalContribution).not.toHaveClass('book-flip-user-sentence--edited');
    expect(editedContribution).toHaveClass(
      'book-flip-user-sentence',
      'book-flip-user-sentence--edited',
    );
    expect(engineHost.querySelector('[data-book-content-id="106"]')).not.toBeInTheDocument();

    fireEvent.pointerDown(originalContribution, {
      clientX: 300,
      clientY: 300,
      pointerId: 1,
      pointerType: 'touch',
      isPrimary: true,
    });
    fireEvent.pointerUp(originalContribution, {
      clientX: 300,
      clientY: 300,
      pointerId: 1,
      pointerType: 'touch',
      isPrimary: true,
    });

    let contributionDialog = screen.getByRole('dialog', { name: '문장 정보' });
    expect(within(contributionDialog).getByText('사용자 원문')).toBeInTheDocument();
    expect(within(contributionDialog).getByText('나')).toBeInTheDocument();
    expect(within(contributionDialog).getByText('137 피해')).toBeInTheDocument();
    expect(within(contributionDialog).getByText('평온 82%')).toBeInTheDocument();
    expect(within(contributionDialog).getByText('신비 64%')).toBeInTheDocument();
    expect(within(contributionDialog).getByText('#은하수')).toBeInTheDocument();
    expect(within(contributionDialog).getByText('#윤슬')).toBeInTheDocument();
    expect(within(contributionDialog).queryByText('제출한 원문')).not.toBeInTheDocument();
    await user.click(within(contributionDialog).getByRole('button', { name: '닫기' }));

    const contributionShortcuts = screen.getByRole('region', {
      name: '사용자 기여 문장 정보',
    });
    expect(within(contributionShortcuts).getAllByRole('button')).toHaveLength(5);
    await user.click(
      within(contributionShortcuts).getByRole('button', {
        name: /새벽작가의 다듬어진 사용자 문장/,
      }),
    );

    contributionDialog = screen.getByRole('dialog', { name: '문장 정보' });
    expect(within(contributionDialog).getByText('다듬어진 사용자 문장')).toBeInTheDocument();
    expect(within(contributionDialog).getByText('새벽작가')).toBeInTheDocument();
    expect(within(contributionDialog).getByText('제출한 원문')).toBeInTheDocument();
    expect(contributionDialog).toHaveTextContent('수호자는 골목 끝에서 흩어진 빛을 모았다.');
    expect(contributionDialog).toHaveTextContent(
      '이야기 흐름에 맞게 AI가 최소한으로 다듬었습니다.',
    );
    expect(within(contributionDialog).getByText('126 피해')).toBeInTheDocument();
    expect(within(contributionDialog).getByText('평온 78%')).toBeInTheDocument();
    await user.click(within(contributionDialog).getByRole('button', { name: '닫기' }));

    expect(
      await screen.findByRole(
        'button',
        { name: '오른쪽 페이지를 눌러 다음 장으로 넘기기' },
        { timeout: 1_500 },
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('1–2 / 4');
    await waitFor(() => {
      const sheets = engineHost.querySelectorAll('.book-flip-sheet');
      expect(sheets).toHaveLength(4);
      expect(sheets.item(3)).not.toHaveClass('book-flip-sheet-placeholder');
      expect(engineHost.querySelector('.book-flip-sources-continuation')).not.toBeInTheDocument();
    });
    const readingProgress = screen.getByRole('slider', { name: '읽기 진행률' });
    fireEvent.change(readingProgress, {
      target: { value: readingProgress.getAttribute('max') },
    });
    const completeButton = await screen.findByRole(
      'button',
      { name: '튜토리얼 완료하기' },
      { timeout: 1_500 },
    );
    expect(completeButton.parentElement).toHaveClass('book-flip-footer');
    await user.click(completeButton);
    clientWidthSpy.mockRestore();
    clientHeightSpy.mockRestore();
    scrollWidthSpy.mockRestore();
    expect(screen.getByRole('heading', { name: '튜토리얼을 모두 마쳤어요!' })).toBeInTheDocument();
    expect(screen.getByText('8/8 모든 단계 완료')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /로그인하고 첫 문장 쓰기/ })).toHaveAttribute(
      'href',
      '/login',
    );
    expect(screen.getByRole('link', { name: '서비스 홈 둘러보기' })).toHaveAttribute('href', '/');
    ['오늘의 단어 확인', '나만의 문장 작성', '이야기 원정 참여', '공동 책 감상'].forEach(
      (highlight) => expect(screen.queryByText(highlight)).not.toBeInTheDocument(),
    );
    expect(screen.getByRole('link', { name: '홈' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: '도서관' })).not.toHaveAttribute('aria-current');
    expect(screen.getByTestId('tutorial-stage')).not.toHaveClass('lg:pr-[370px]');

    await waitFor(() => expect(fetchSpy).not.toHaveBeenCalled());
  }, 15_000);

  it('이전 안내를 누르면 실제 홈 화면 위의 시작 안내로 돌아간다', async () => {
    const user = userEvent.setup();
    renderTutorial();

    await user.click(screen.getByRole('button', { name: '튜토리얼 시작' }));
    await user.click(screen.getByRole('button', { name: '1번째 단어 카드 뒤집기' }));
    await user.click(screen.getByRole('button', { name: '다미 안내 펼치기' }));
    await user.click(screen.getByRole('button', { name: '이전' }));

    expect(screen.getByRole('button', { name: '튜토리얼 시작' })).toBeInTheDocument();
    expect(screen.getByText('안녕, 나는 이야기 마법사 다미야!')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '오늘의 단어' })).toBeInTheDocument();
  });
});
