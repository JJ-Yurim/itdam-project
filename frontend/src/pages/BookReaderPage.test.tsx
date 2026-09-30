import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createEvent, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse, http } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { server } from '@/mocks/server';

import { BookReaderPage } from './BookReaderPage';

const pageFlipSpies = vi.hoisted(() => ({
  create: vi.fn(),
  destroy: vi.fn(),
  flip: vi.fn(),
  flipNext: vi.fn(),
  flipPrev: vi.fn(),
  emitChangeState: vi.fn<(state: string) => void>(),
  startUserTouch: vi.fn(),
  turnToPage: vi.fn(),
  userMove: vi.fn(),
  userStop: vi.fn(),
}));

vi.mock('page-flip', () => ({
  PageFlip: class {
    private readonly handlers = new Map<string, Array<(event: { data: unknown }) => void>>();
    private readonly root: HTMLElement;
    private currentPage = 0;
    private pageCount = 1;
    private readonly landscape: boolean;

    constructor(
      root: HTMLElement,
      settings: { width: number; startPage?: number; useMouseEvents?: boolean },
    ) {
      this.root = root;
      this.currentPage = settings.startPage ?? 0;
      this.landscape = Number.parseFloat(root.style.width) > settings.width;
      pageFlipSpies.create(settings);
      pageFlipSpies.emitChangeState.mockImplementation((state) => this.emit('changeState', state));
    }

    on(eventName: string, callback: (event: { data: unknown }) => void) {
      this.handlers.set(eventName, [...(this.handlers.get(eventName) ?? []), callback]);
      return this;
    }

    off(eventName: string) {
      this.handlers.delete(eventName);
    }

    loadFromHTML(elements: HTMLElement[]) {
      this.pageCount = elements.length;
    }

    getCurrentPageIndex() {
      return this.landscape ? Math.floor(this.currentPage / 2) * 2 : this.currentPage;
    }

    private emit(eventName: string, data: unknown) {
      this.handlers.get(eventName)?.forEach((handler) => handler({ data }));
    }

    flip(page: number) {
      pageFlipSpies.flip(page);
      this.currentPage = page;
      this.emit('flip', page);
    }

    flipNext() {
      pageFlipSpies.flipNext();
      this.currentPage = Math.min(this.pageCount - 1, this.currentPage + (this.landscape ? 2 : 1));
      this.emit('flip', this.currentPage);
    }

    flipPrev() {
      pageFlipSpies.flipPrev();
      this.currentPage = Math.max(0, this.currentPage - (this.landscape ? 2 : 1));
      this.emit('flip', this.currentPage);
    }

    startUserTouch(point: { x: number; y: number }) {
      pageFlipSpies.startUserTouch(point);
    }

    userMove(point: { x: number; y: number }, isTouch: boolean) {
      pageFlipSpies.userMove(point, isTouch);
    }

    userStop(point: { x: number; y: number }) {
      pageFlipSpies.userStop(point);
    }

    turnToPage(page: number) {
      pageFlipSpies.turnToPage(page);
      this.currentPage = this.landscape ? Math.floor(page / 2) * 2 : page;
      this.emit('flip', this.currentPage);
    }

    destroy() {
      pageFlipSpies.destroy();
      this.root.remove();
    }
  },
}));

let stageWidth = 1000;
let stageHeight = 600;
let flowScrollWidth = 2800;

function renderBookReaderPage(initialEntry = '/library/books/18/read') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route path="/library/books/:bookId/read" element={<BookReaderPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('BookReaderPage', () => {
  beforeEach(() => {
    stageWidth = 1000;
    stageHeight = 600;
    flowScrollWidth = 2800;
    vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockImplementation(function (
      this: HTMLElement,
    ) {
      return this.classList.contains('book-flip-stage') ? stageWidth : 0;
    });
    vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockImplementation(function (
      this: HTMLElement,
    ) {
      return this.classList.contains('book-flip-stage') ? stageHeight : 0;
    });
    vi.spyOn(HTMLElement.prototype, 'scrollWidth', 'get').mockImplementation(function (
      this: HTMLElement,
    ) {
      return this.classList.contains('book-flip-flow') ? flowScrollWidth : 0;
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    Object.values(pageFlipSpies).forEach((spy) => spy.mockClear());
  });

  it('본문 마지막에 사용자 원문과 작성자 출처를 표시한다', async () => {
    renderBookReaderPage();

    const reader = await screen.findByRole('region', { name: '공동 소설 본문' });
    expect(within(reader).getByRole('heading', { name: '수록 문장 출처' })).toBeInTheDocument();
    expect(within(reader).getByText('문장여행자')).toBeVisible();
    expect(screen.queryByText('AI')).not.toBeInTheDocument();
  });

  it('본문의 사용자 기여 문장을 구분하고 누르면 작성자 정보를 표시한다', async () => {
    renderBookReaderPage();

    const host = await screen.findByTestId('book-flip-engine');
    await waitFor(() =>
      expect(host.querySelector('[data-book-content-id="101"]')).toBeInTheDocument(),
    );

    const original = host.querySelector<HTMLElement>('[data-book-content-id="101"]')!;
    const edited = host.querySelector<HTMLElement>('[data-book-content-id="102"]')!;

    expect(original).toHaveClass('book-flip-user-sentence');
    expect(original).not.toHaveClass('book-flip-user-sentence--edited');
    expect(edited).toHaveClass('book-flip-user-sentence--edited');
    expect(host.querySelector('[data-book-content-id="103"]')).not.toBeInTheDocument();

    fireEvent.pointerDown(original, {
      clientX: 300,
      clientY: 300,
      pointerId: 1,
      pointerType: 'touch',
      isPrimary: true,
    });
    fireEvent.pointerUp(original, {
      clientX: 300,
      clientY: 300,
      pointerId: 1,
      pointerType: 'touch',
      isPrimary: true,
    });

    let dialog = screen.getByRole('dialog', { name: '문장 정보' });
    expect(within(dialog).getByText('사용자 원문')).toBeInTheDocument();
    expect(within(dialog).getByText('문장여행자')).toBeInTheDocument();
    expect(within(dialog).queryByText('수록 순서')).not.toBeInTheDocument();
    expect(within(dialog).getByText('121 피해')).toBeInTheDocument();
    expect(pageFlipSpies.flip).not.toHaveBeenCalled();

    fireEvent.click(within(dialog).getByRole('button', { name: '닫기' }));
    fireEvent.pointerDown(edited, {
      clientX: 300,
      clientY: 300,
      pointerId: 2,
      pointerType: 'touch',
      isPrimary: true,
    });
    fireEvent.pointerUp(edited, {
      clientX: 300,
      clientY: 300,
      pointerId: 2,
      pointerType: 'touch',
      isPrimary: true,
    });

    dialog = screen.getByRole('dialog', { name: '문장 정보' });
    expect(within(dialog).getByText('다듬어진 사용자 문장')).toBeInTheDocument();
    expect(within(dialog).getByText('새벽작가')).toBeInTheDocument();
    expect(within(dialog).getByText('제출한 원문')).toBeInTheDocument();
    expect(dialog).toHaveTextContent('수호자는 흩어진 문장들을 주워 오래된 문 앞에 두었다.');
    expect(within(dialog).getByText('137 피해')).toBeInTheDocument();
    expect(within(dialog).getByText('평온 82%')).toBeInTheDocument();
    expect(within(dialog).getByText('신비 64%')).toBeInTheDocument();
    expect(within(dialog).getByText('#수호자')).toBeInTheDocument();
    expect(within(dialog).getByText('#새벽')).toBeInTheDocument();
  });

  it('강조 문장에서 시작한 가로 드래그도 페이지 넘김 동작으로 전달한다', async () => {
    stageWidth = 600;
    renderBookReaderPage();

    const host = await screen.findByTestId('book-flip-engine');
    const stage = host.parentElement!;
    await waitFor(() =>
      expect(host.querySelector('[data-book-content-id="101"]')).toBeInTheDocument(),
    );
    Object.defineProperty(host.firstElementChild, 'getBoundingClientRect', {
      configurable: true,
      value: () => ({ left: 84, top: 0, width: 432, height: 600, right: 516, bottom: 600 }),
    });

    const contribution = host.querySelector<HTMLElement>('[data-book-content-id="101"]')!;
    fireEvent.pointerDown(contribution, {
      clientX: 500,
      clientY: 300,
      pointerId: 1,
      pointerType: 'touch',
      isPrimary: true,
    });
    fireEvent.pointerMove(stage, {
      clientX: 450,
      clientY: 302,
      pointerId: 1,
      pointerType: 'touch',
      isPrimary: true,
    });
    fireEvent.pointerUp(stage, {
      clientX: 350,
      clientY: 304,
      pointerId: 1,
      pointerType: 'touch',
      isPrimary: true,
    });

    expect(pageFlipSpies.startUserTouch).toHaveBeenCalledTimes(1);
    expect(pageFlipSpies.userMove).toHaveBeenCalledTimes(2);
    expect(pageFlipSpies.userStop).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('dialog', { name: '문장 정보' })).not.toBeInTheDocument();
  });

  it('강조 문장을 드래그했다가 시작점으로 돌아와도 문장 정보는 열지 않는다', async () => {
    stageWidth = 600;
    renderBookReaderPage();

    const host = await screen.findByTestId('book-flip-engine');
    const stage = host.parentElement!;
    await waitFor(() =>
      expect(host.querySelector('[data-book-content-id="101"]')).toBeInTheDocument(),
    );
    Object.defineProperty(host.firstElementChild, 'getBoundingClientRect', {
      configurable: true,
      value: () => ({ left: 84, top: 0, width: 432, height: 600, right: 516, bottom: 600 }),
    });

    const contribution = host.querySelector<HTMLElement>('[data-book-content-id="101"]')!;
    fireEvent.pointerDown(contribution, {
      clientX: 500,
      clientY: 300,
      pointerId: 1,
      pointerType: 'touch',
      isPrimary: true,
    });
    fireEvent.pointerMove(stage, {
      clientX: 450,
      clientY: 302,
      pointerId: 1,
      pointerType: 'touch',
      isPrimary: true,
    });
    fireEvent.pointerMove(stage, {
      clientX: 500,
      clientY: 300,
      pointerId: 1,
      pointerType: 'touch',
      isPrimary: true,
    });
    fireEvent.pointerUp(stage, {
      clientX: 500,
      clientY: 300,
      pointerId: 1,
      pointerType: 'touch',
      isPrimary: true,
    });

    expect(pageFlipSpies.startUserTouch).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('dialog', { name: '문장 정보' })).not.toBeInTheDocument();
  });

  it('모션 감소 설정에서도 드래그 후 시작점으로 돌아오면 문장 정보를 열지 않는다', async () => {
    vi.stubGlobal(
      'matchMedia',
      vi.fn().mockReturnValue({
        matches: true,
        media: '(prefers-reduced-motion: reduce)',
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      }),
    );
    stageWidth = 600;
    renderBookReaderPage();

    const host = await screen.findByTestId('book-flip-engine');
    const stage = host.parentElement!;
    await waitFor(() =>
      expect(host.querySelector('[data-book-content-id="101"]')).toBeInTheDocument(),
    );
    const contribution = host.querySelector<HTMLElement>('[data-book-content-id="101"]')!;

    fireEvent.pointerDown(contribution, {
      clientX: 500,
      clientY: 300,
      pointerId: 1,
      pointerType: 'touch',
      isPrimary: true,
    });
    fireEvent.pointerMove(stage, {
      clientX: 450,
      clientY: 302,
      pointerId: 1,
      pointerType: 'touch',
      isPrimary: true,
    });
    fireEvent.pointerMove(stage, {
      clientX: 500,
      clientY: 300,
      pointerId: 1,
      pointerType: 'touch',
      isPrimary: true,
    });
    fireEvent.pointerUp(stage, {
      clientX: 500,
      clientY: 300,
      pointerId: 1,
      pointerType: 'touch',
      isPrimary: true,
    });

    expect(screen.queryByRole('dialog', { name: '문장 정보' })).not.toBeInTheDocument();
  });

  it('키보드로 사용자 기여 문장 정보를 열 수 있다', async () => {
    const user = userEvent.setup();
    renderBookReaderPage();

    const contributionButton = await screen.findByRole('button', {
      name: /문장여행자.*사용자 원문/,
    });
    contributionButton.focus();
    await user.keyboard('{Enter}');

    const dialog = screen.getByRole('dialog', { name: '문장 정보' });
    expect(within(dialog).getByText('문장여행자')).toBeInTheDocument();
    expect(within(dialog).getByText('121 피해')).toBeInTheDocument();
  });

  it('작은 화면에서도 많은 기여 문장 단축 목록을 스크롤할 수 있다', async () => {
    stageHeight = 320;
    const contributions = Array.from({ length: 60 }, (_, index) => ({
      contentId: 1_000 + index,
      sequenceNo: index + 1,
      contentType: 'USER_ORIGINAL' as const,
      content: `사용자 기여 문장 ${index + 1}.`,
      contributor: { userId: 2_000 + index, nickname: `작가 ${index + 1}` },
      sourceSubmissionId: 3_000 + index,
      contribution: null,
    }));
    server.use(
      http.get('*/api/v1/library/books/:bookId', () =>
        HttpResponse.json({
          bookId: 18,
          bossRunId: 7,
          bossName: '안개의 수호자',
          coverImageUrl: null,
          completedAt: '2026-07-27T10:05:00Z',
          body: contributions.map((content) => content.content).join('\n\n'),
          likeCount: 0,
          liked: false,
          bookmarked: false,
          contents: contributions,
        }),
      ),
    );
    renderBookReaderPage();

    const shortcuts = await screen.findByRole('region', { name: '사용자 기여 문장 정보' });
    const shortcutList = shortcuts.querySelector<HTMLElement>(
      '.book-flip-contribution-shortcut-list',
    )!;
    const buttons = within(shortcuts).getAllByRole('button');
    const lastButton = buttons.at(-1)!;
    const scrollIntoView = vi.fn();
    Object.defineProperty(lastButton, 'scrollIntoView', {
      configurable: true,
      value: scrollIntoView,
    });

    expect(buttons).toHaveLength(60);
    expect(shortcuts).toHaveStyle({ display: 'flex', overflow: 'hidden' });
    expect(Number.parseFloat(getComputedStyle(shortcuts).maxHeight)).toBeLessThanOrEqual(
      stageHeight - 100,
    );
    expect(shortcutList).toHaveStyle({ flex: '1 1 auto', minHeight: 0, overflowY: 'auto' });
    expect(getComputedStyle(shortcutList).overflowY).toBe('auto');
    lastButton.focus();
    expect(lastButton).toHaveFocus();
    expect(scrollIntoView).toHaveBeenCalledWith({ block: 'nearest' });
  });

  it('상세 API의 완성된 본문만 읽기 화면에 표시한다', async () => {
    renderBookReaderPage();

    expect(await screen.findByRole('heading', { name: '안개의 수호자' })).toBeInTheDocument();
    const reader = screen.getByRole('region', { name: '공동 소설 본문' });
    expect(
      within(reader).getByText(/수호자는 흩어진 문장들을 모아 오래된 문 앞에 세웠다/),
    ).toBeVisible();
    expect(screen.getByRole('link', { name: '공동 도서 정보로 돌아가기' })).toHaveAttribute(
      'href',
      '/library/books/18',
    );
  });

  it('완성 본문이 null이어도 수록 문장을 순서대로 조합해 표시한다', async () => {
    server.use(
      http.get('*/api/v1/library/books/:bookId', () =>
        HttpResponse.json({
          bookId: 18,
          bossRunId: 7,
          bossName: '안개의 수호자',
          coverImageUrl: null,
          completedAt: '2026-07-27T10:05:00Z',
          body: null,
          likeCount: 0,
          liked: false,
          bookmarked: false,
          contents: [
            {
              contentId: 102,
              sequenceNo: 2,
              contentType: 'AI_GENERATED',
              content: '두 번째 문장',
              contributor: null,
              sourceSubmissionId: null,
              contribution: null,
            },
            {
              contentId: 101,
              sequenceNo: 1,
              contentType: 'USER_ORIGINAL',
              content: '첫 번째 사용자 원문',
              contributor: { userId: 1, nickname: '문장여행자' },
              sourceSubmissionId: 55,
              contribution: null,
            },
          ],
        }),
      ),
    );

    renderBookReaderPage();

    const reader = await screen.findByRole('region', { name: '공동 소설 본문' });
    expect(reader).toHaveTextContent('첫 번째 사용자 원문 두 번째 문장');
    expect(screen.getByRole('heading', { name: '수록 문장 출처' })).toBeInTheDocument();
  });

  it('넓은 화면에서는 양면 책을 만들고 다음 펼침으로 이동한다', async () => {
    const user = userEvent.setup();
    renderBookReaderPage();

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('1–2 / 7'));
    const host = await screen.findByTestId('book-flip-engine');
    const sheets = host.querySelectorAll('.book-flip-sheet');
    const placeholder = sheets.item(sheets.length - 1);
    expect(sheets).toHaveLength(8);
    expect(placeholder).toHaveClass('book-flip-sheet-placeholder');
    expect(placeholder.querySelector('.book-flip-page-number')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '다음 페이지' }));

    expect(pageFlipSpies.flip).toHaveBeenCalledWith(2);
    expect(screen.getByRole('status')).toHaveTextContent('3–4 / 7');
  });

  it('책 읽기 영역에 포커스한 뒤 화살표 키로 페이지를 이동한다', async () => {
    renderBookReaderPage();

    const readingRegion = await screen.findByRole('region', { name: '책 읽기 영역' });
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('1–2 / 7'));
    expect(readingRegion).toHaveAttribute('tabindex', '0');
    readingRegion.focus();
    fireEvent.keyDown(readingRegion, { key: 'ArrowRight' });

    expect(pageFlipSpies.flip).toHaveBeenCalledWith(2);
  });

  it('현재 펼침 주변 페이지만 본문 DOM을 채운다', async () => {
    flowScrollWidth = 12_000;
    renderBookReaderPage();

    const host = await screen.findByTestId('book-flip-engine');
    await waitFor(() =>
      expect(host.querySelectorAll('.book-flip-sheet').length).toBeGreaterThan(20),
    );

    expect(host.querySelectorAll('.book-flip-flow').length).toBeLessThanOrEqual(6);
  });

  it('모바일에서 좌우 가장자리 탭으로 다음 페이지와 이전 페이지를 이동한다', async () => {
    stageWidth = 600;
    renderBookReaderPage();

    const stage = (await screen.findByTestId('book-flip-engine')).parentElement!;
    Object.defineProperty(stage, 'getBoundingClientRect', {
      configurable: true,
      value: () => ({ left: 0, top: 0, width: 600, height: 600, right: 600, bottom: 600 }),
    });
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('1 / 7'));

    fireEvent.pointerDown(stage, {
      clientX: 550,
      clientY: 300,
      pointerId: 1,
      pointerType: 'touch',
      isPrimary: true,
    });
    fireEvent.pointerUp(stage, {
      clientX: 550,
      clientY: 300,
      pointerId: 1,
      pointerType: 'touch',
      isPrimary: true,
    });
    expect(pageFlipSpies.flip).toHaveBeenLastCalledWith(1);
    expect(screen.getByRole('status')).toHaveTextContent('2 / 7');

    fireEvent.pointerDown(stage, {
      clientX: 50,
      clientY: 300,
      pointerId: 1,
      pointerType: 'touch',
      isPrimary: true,
    });
    fireEvent.pointerUp(stage, {
      clientX: 50,
      clientY: 300,
      pointerId: 1,
      pointerType: 'touch',
      isPrimary: true,
    });
    expect(pageFlipSpies.turnToPage).toHaveBeenLastCalledWith(0);
    expect(screen.getByRole('status')).toHaveTextContent('1 / 7');
  });

  it('가로 240px 미만에서도 단면 책 엔진을 유지한다', async () => {
    stageWidth = 220;
    renderBookReaderPage();

    const host = await screen.findByTestId('book-flip-engine');
    await waitFor(() => expect(host.firstElementChild).toBeInTheDocument());

    expect(pageFlipSpies.create).toHaveBeenLastCalledWith(expect.objectContaining({ width: 216 }));
    expect(host.querySelector('.book-flip-sheet-clip')).toHaveStyle({
      inset: '23px 19px',
    });
    expect(screen.getByRole('status').textContent).toMatch(/^1 \/ \d+$/);
  });

  it('책 본문 선택은 막고 터치 스크롤 기본 동작은 유지한다', async () => {
    stageWidth = 600;
    renderBookReaderPage();

    const host = await screen.findByTestId('book-flip-engine');
    const stage = host.parentElement!;
    await waitFor(() => expect(host.firstElementChild).toBeInTheDocument());

    const mousePointerDown = createEvent.pointerDown(stage, {
      clientX: 500,
      clientY: 300,
      pointerId: 1,
      pointerType: 'mouse',
      isPrimary: true,
      button: 0,
      cancelable: true,
    });
    fireEvent(stage, mousePointerDown);
    expect(mousePointerDown).toHaveProperty('defaultPrevented', true);
    fireEvent.pointerUp(stage, {
      clientX: 500,
      clientY: 300,
      pointerId: 1,
      pointerType: 'mouse',
      isPrimary: true,
    });

    const touchPointerDown = createEvent.pointerDown(stage, {
      clientX: 500,
      clientY: 300,
      pointerId: 2,
      pointerType: 'touch',
      isPrimary: true,
      cancelable: true,
    });
    fireEvent(stage, touchPointerDown);
    expect(touchPointerDown).toHaveProperty('defaultPrevented', false);
    fireEvent.pointerCancel(stage, {
      clientX: 500,
      clientY: 300,
      pointerId: 2,
      pointerType: 'touch',
      isPrimary: true,
    });
  });

  it('실제 모바일의 touch·호환 mouse 이벤트가 이어져도 한 페이지만 이동한다', async () => {
    stageWidth = 600;
    renderBookReaderPage();

    const host = await screen.findByTestId('book-flip-engine');
    const stage = host.parentElement!;
    await waitFor(() => expect(host.firstElementChild).toBeInTheDocument());
    const engineInput = host.firstElementChild!;
    const legacyInputHandler = vi.fn();
    engineInput.addEventListener('touchstart', legacyInputHandler);
    engineInput.addEventListener('touchend', legacyInputHandler);
    engineInput.addEventListener('mousedown', legacyInputHandler);
    engineInput.addEventListener('mouseup', legacyInputHandler);
    Object.defineProperty(stage, 'getBoundingClientRect', {
      configurable: true,
      value: () => ({ left: 0, top: 0, width: 600, height: 600, right: 600, bottom: 600 }),
    });
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('1 / 7'));

    expect(pageFlipSpies.create).toHaveBeenLastCalledWith(
      expect.objectContaining({ useMouseEvents: true }),
    );
    fireEvent.pointerDown(engineInput, {
      clientX: 550,
      clientY: 300,
      pointerId: 1,
      pointerType: 'touch',
      isPrimary: true,
    });
    fireEvent.touchStart(engineInput, { changedTouches: [{ clientX: 550, clientY: 300 }] });
    fireEvent.pointerUp(engineInput, {
      clientX: 550,
      clientY: 300,
      pointerId: 1,
      pointerType: 'touch',
      isPrimary: true,
    });
    fireEvent.touchEnd(engineInput, { changedTouches: [{ clientX: 550, clientY: 300 }] });
    fireEvent.mouseDown(engineInput, { clientX: 550, clientY: 300 });
    fireEvent.mouseUp(engineInput, { clientX: 550, clientY: 300 });

    expect(legacyInputHandler).not.toHaveBeenCalled();
    expect(pageFlipSpies.flip).toHaveBeenCalledTimes(1);
    expect(pageFlipSpies.flip).toHaveBeenCalledWith(1);
    expect(screen.getByRole('status')).toHaveTextContent('2 / 7');
  });

  it('모바일 가로 드래그는 동일한 pointer 경로로 페이지 엔진에 한 번 전달한다', async () => {
    stageWidth = 600;
    renderBookReaderPage();

    const host = await screen.findByTestId('book-flip-engine');
    const stage = host.parentElement!;
    await waitFor(() => expect(host.firstElementChild).toBeInTheDocument());
    Object.defineProperty(host.firstElementChild, 'getBoundingClientRect', {
      configurable: true,
      value: () => ({ left: 84, top: 0, width: 432, height: 600, right: 516, bottom: 600 }),
    });

    fireEvent.pointerDown(stage, {
      clientX: 500,
      clientY: 300,
      pointerId: 1,
      pointerType: 'touch',
      isPrimary: true,
    });
    fireEvent.pointerMove(stage, {
      clientX: 450,
      clientY: 302,
      pointerId: 1,
      pointerType: 'touch',
      isPrimary: true,
    });
    fireEvent.pointerUp(stage, {
      clientX: 350,
      clientY: 304,
      pointerId: 1,
      pointerType: 'touch',
      isPrimary: true,
    });

    expect(pageFlipSpies.startUserTouch).toHaveBeenCalledTimes(1);
    expect(pageFlipSpies.userMove).toHaveBeenCalledTimes(2);
    expect(pageFlipSpies.userStop).toHaveBeenCalledTimes(1);
    expect(pageFlipSpies.flip).not.toHaveBeenCalled();
  });

  it('진행률을 움직이면 애니메이션 없이 해당 페이지로 이동한다', async () => {
    renderBookReaderPage();

    const slider = await screen.findByRole('slider', { name: '읽기 진행률' });
    await waitFor(() => expect(slider).toHaveAttribute('max', '6'));
    fireEvent.change(slider, { target: { value: '4' } });

    expect(pageFlipSpies.turnToPage).toHaveBeenCalledWith(4);
  });

  it('양면 진행률은 펼침 단위로 이동하고 홀수 페이지를 엔진 상태에 맞춰 정규화한다', async () => {
    renderBookReaderPage();

    const slider = await screen.findByRole('slider', { name: '읽기 진행률' });
    await waitFor(() => expect(slider).toHaveAttribute('max', '6'));
    expect(slider).toHaveAttribute('step', '2');
    fireEvent.change(slider, { target: { value: '3' } });

    expect(pageFlipSpies.turnToPage).toHaveBeenCalledWith(3);
    expect(slider).toHaveValue('2');
    expect(screen.getByRole('status')).toHaveTextContent('3–4 / 7');
  });

  it('짝수 페이지의 마지막 펼침에서 진행률이 끝까지 찬다', async () => {
    flowScrollWidth = 3000;
    renderBookReaderPage();

    const slider = await screen.findByRole('slider', { name: '읽기 진행률' });
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('1–2 / 8'));
    expect(slider).toHaveAttribute('max', '6');

    fireEvent.change(slider, { target: { value: '6' } });

    expect(slider).toHaveValue('6');
    expect(screen.getByRole('status')).toHaveTextContent('7–8 / 8');
  });

  it('배율은 페이지 엔진에 반영하고 테마는 독서 배경만 변경한다', async () => {
    const user = userEvent.setup();
    renderBookReaderPage();

    const themeButton = await screen.findByRole('button', { name: '어두운 독서 배경으로 변경' });
    await waitFor(() =>
      expect(screen.getByTestId('book-flip-engine').children.length).toBeGreaterThan(0),
    );
    pageFlipSpies.destroy.mockClear();
    await user.click(themeButton);

    expect(screen.getByRole('button', { name: '밝은 독서 배경으로 변경' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(screen.getByRole('article')).toHaveAttribute('data-background-theme', 'dim');
    expect(pageFlipSpies.destroy).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: '본문 배율 100%' }));
    expect(screen.getByRole('button', { name: '본문 배율 110%' })).toBeVisible();
    expect(pageFlipSpies.destroy).toHaveBeenCalled();

    pageFlipSpies.destroy.mockClear();
    await user.selectOptions(screen.getByRole('combobox', { name: '본문 글꼴' }), 'pretendard');
    expect(screen.getByRole('combobox', { name: '본문 글꼴' })).toHaveValue('pretendard');
    expect(pageFlipSpies.destroy).toHaveBeenCalled();
  });

  it('완성된 본문이 없으면 빈 상태를 표시한다', async () => {
    server.use(
      http.get('*/api/v1/library/books/:bookId', () =>
        HttpResponse.json({
          bookId: 18,
          bossRunId: 7,
          bossName: '안개의 수호자',
          coverImageUrl: null,
          completedAt: '2026-07-27T10:05:00Z',
          body: null,
          likeCount: 0,
          liked: false,
          bookmarked: false,
          contents: [],
        }),
      ),
    );

    renderBookReaderPage();

    expect(await screen.findByText('아직 읽을 수 있는 본문이 없습니다')).toBeVisible();
  });

  it('잘못된 책 ID이면 오류 안내를 표시한다', () => {
    renderBookReaderPage('/library/books/not-number/read');

    expect(screen.getByRole('alert')).toHaveTextContent('잘못된 공동 책 주소입니다');
  });
});
