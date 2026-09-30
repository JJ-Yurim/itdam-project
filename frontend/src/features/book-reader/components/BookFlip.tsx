import { PageFlip, type PageFlipOrientation, type PageFlipState } from 'page-flip';
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';

import type { BookContentResponse } from '@/features/library';
import { Button, Modal } from '@/shared/components';
import { getMoodLabel } from '@/shared/moods';

const LANDSCAPE_MIN_WIDTH = 820;
const PAGE_RATIO = 0.72;
const PAGE_RENDER_BUFFER = 2;
const TAP_MAX_DISTANCE = 12;
const TAP_MAX_DURATION_MS = 450;
const SWIPE_MAX_DURATION_MS = 250;
const SWIPE_DISTANCE = 28;

interface BookFlipProps {
  body: string;
  contents: BookContentResponse[];
  bodyPages?: readonly string[];
  sources?: BookContentResponse[];
  fontScale: number;
  fontFamily: string;
  turnPrompt?: string;
  completionLabel?: string;
  onComplete?: () => void;
  onInteract?: () => void;
}

interface BookSize {
  pageWidth: number;
  pageHeight: number;
  landscape: boolean;
  horizontalPadding: number;
  verticalPadding: number;
}

interface FlowOptions extends BookSize {
  body: string;
  contents: BookContentResponse[];
  bodyPages?: readonly string[];
  sources: BookContentResponse[];
  fontScale: number;
  fontFamily: string;
}

interface PageFlipPoint {
  x: number;
  y: number;
}

interface PointerGesture {
  pointerId: number;
  pointerType: string;
  startX: number;
  startY: number;
  startTime: number;
  maxDistance: number;
  engineStarted: boolean;
  contributionId: number | null;
}

interface LocatedContentRange {
  start: number;
  end: number;
  content: BookContentResponse;
}

function isUserContribution(content: BookContentResponse) {
  return content.contentType === 'USER_ORIGINAL' || content.contentType === 'USER_EDITED';
}

function contributionTypeLabel(content: BookContentResponse) {
  return content.contentType === 'USER_EDITED' ? '다듬어진 사용자 문장' : '사용자 원문';
}

function locateContentRanges(body: string, contents: BookContentResponse[]) {
  let searchFrom = 0;
  const ranges: LocatedContentRange[] = [];

  [...contents]
    .sort((left, right) => left.sequenceNo - right.sequenceNo)
    .forEach((content) => {
      const start = body.indexOf(content.content, searchFrom);
      if (start < 0) return;

      const end = start + content.content.length;
      ranges.push({ start, end, content });
      searchFrom = end;
    });

  return ranges;
}

function createTextElement(tagName: string, className: string, text: string) {
  const element = document.createElement(tagName);
  element.className = className;
  element.textContent = text;
  return element;
}

function createContributionElement(content: BookContentResponse) {
  const element = document.createElement('span');
  const contributorName = content.contributor?.nickname ?? '알 수 없는 작가';

  element.className = 'book-flip-user-sentence';
  if (content.contentType === 'USER_EDITED') {
    element.classList.add('book-flip-user-sentence--edited');
  }
  element.dataset.bookContentId = String(content.contentId);
  element.title = `${contributorName} · ${contributionTypeLabel(content)} · 눌러서 정보 보기`;
  element.textContent = content.content;
  return element;
}

function appendParagraphContent(
  paragraphElement: HTMLParagraphElement,
  body: string,
  paragraphStart: number,
  paragraphEnd: number,
  ranges: LocatedContentRange[],
) {
  let cursor = paragraphStart;

  ranges.forEach((range) => {
    if (range.start < paragraphStart || range.end > paragraphEnd) return;

    if (range.start > cursor) {
      paragraphElement.append(document.createTextNode(body.slice(cursor, range.start)));
    }
    paragraphElement.append(
      isUserContribution(range.content)
        ? createContributionElement(range.content)
        : document.createTextNode(range.content.content),
    );
    cursor = range.end;
  });

  if (cursor < paragraphEnd) {
    paragraphElement.append(document.createTextNode(body.slice(cursor, paragraphEnd)));
  }
}

function createBodyMain(
  body: string,
  contents: BookContentResponse[],
  bodyPages?: readonly string[],
) {
  const main = document.createElement('div');
  const ranges = locateContentRanges(body, contents);
  const pageStarts = new Set<number>();
  let pageSearchFrom = 0;
  let searchFrom = 0;
  let paragraphIndex = 0;

  bodyPages?.forEach((page, pageIndex) => {
    const pageStart = body.indexOf(page, pageSearchFrom);
    if (pageStart < 0) return;
    if (pageIndex > 0) pageStarts.add(pageStart);
    pageSearchFrom = pageStart + page.length;
  });

  main.className = 'book-flip-main';
  body
    .split(/\n\s*\n/u)
    .map((paragraph) => paragraph.trim())
    .filter(Boolean)
    .forEach((paragraph) => {
      const paragraphStart = body.indexOf(paragraph, searchFrom);
      if (paragraphStart < 0) return;

      const paragraphEnd = paragraphStart + paragraph.length;
      const paragraphElement = document.createElement('p');
      paragraphElement.className = [
        paragraphIndex === 0 ? 'book-flip-opening' : '',
        pageStarts.has(paragraphStart) ? 'book-flip-page-start' : '',
      ]
        .filter(Boolean)
        .join(' ');
      appendParagraphContent(paragraphElement, body, paragraphStart, paragraphEnd, ranges);
      main.append(paragraphElement);
      paragraphIndex += 1;
      searchFrom = paragraphEnd;
    });

  return main;
}

function createFlow({
  body,
  contents,
  bodyPages,
  sources,
  pageWidth,
  pageHeight,
  horizontalPadding,
  verticalPadding,
  fontScale,
  fontFamily,
}: FlowOptions) {
  const flow = document.createElement('div');
  const contentWidth = pageWidth - horizontalPadding * 2;
  const contentHeight = pageHeight - verticalPadding * 2;

  flow.className = 'book-flip-flow';
  flow.style.width = `${contentWidth}px`;
  flow.style.height = `${contentHeight}px`;
  flow.style.columnWidth = `${contentWidth}px`;
  flow.style.columnGap = `${horizontalPadding * 2}px`;
  flow.style.fontSize = `${fontScale}rem`;
  flow.style.fontFamily = fontFamily;

  flow.append(createBodyMain(body, contents, bodyPages));

  if (sources.length > 0) {
    const sourceSection = document.createElement('section');
    sourceSection.className = 'book-flip-sources';
    sourceSection.append(
      createTextElement('p', 'book-flip-kicker', 'SOURCE'),
      createTextElement('h2', 'book-flip-source-title', '수록 문장 출처'),
      createTextElement(
        'p',
        'book-flip-source-description',
        '공동 소설집에 수록된 사용자 문장입니다.',
      ),
    );

    const list = document.createElement('ol');
    list.className = 'book-flip-source-list';
    sources.forEach((source) => {
      const item = document.createElement('li');
      item.append(
        createTextElement('p', 'book-flip-contributor', source.contributor?.nickname ?? ''),
        createTextElement('blockquote', 'book-flip-quote', `“${source.content}”`),
      );
      list.append(item);
    });
    sourceSection.append(list);
    flow.append(sourceSection);
  }

  return flow;
}

function measurePageCount(host: HTMLElement, options: FlowOptions, flow: HTMLElement) {
  const measurer = document.createElement('div');
  measurer.className = 'book-flip-measurer';
  measurer.append(flow);
  host.append(measurer);
  const contentWidth = options.pageWidth - options.horizontalPadding * 2;
  const columnStep = contentWidth + options.horizontalPadding * 2;
  const measuredCount = Math.max(
    1,
    Math.ceil((flow.scrollWidth + options.horizontalPadding * 2) / columnStep),
  );
  flow.remove();
  measurer.remove();
  const minimumPageCount = options.bodyPages?.length
    ? options.bodyPages.length + (options.sources.length > 0 ? 1 : 0)
    : 1;
  return Math.max(measuredCount, minimumPageCount);
}

function createPage(options: FlowOptions, pageIndex: number, placeholder = false) {
  const page = document.createElement('div');
  page.className = 'book-flip-sheet';
  page.setAttribute('aria-hidden', 'true');
  if (placeholder) {
    page.classList.add('book-flip-sheet-placeholder');
    page.dataset.placeholder = 'true';
  } else {
    page.dataset.pageNumber = String(pageIndex + 1);
  }

  const surface = document.createElement('div');
  surface.className = 'book-flip-sheet-surface';
  const clip = document.createElement('div');
  clip.className = 'book-flip-sheet-clip';
  clip.style.inset = `${options.verticalPadding}px ${options.horizontalPadding}px`;
  surface.append(clip);
  if (!placeholder) {
    surface.append(createTextElement('span', 'book-flip-page-number', String(pageIndex + 1)));
  }
  page.append(surface);
  return page;
}

function syncPageWindow(
  pages: HTMLElement[],
  flowTemplate: HTMLElement,
  pageWidth: number,
  centerPage: number,
  visiblePages: number,
) {
  const firstPage = Math.max(0, centerPage - PAGE_RENDER_BUFFER);
  const lastPage = Math.min(pages.length - 1, centerPage + visiblePages - 1 + PAGE_RENDER_BUFFER);

  pages.forEach((page, pageIndex) => {
    const clip = page.querySelector<HTMLElement>('.book-flip-sheet-clip');
    if (!clip) return;

    if (page.dataset.placeholder === 'true') {
      clip.replaceChildren();
      return;
    }

    if (pageIndex < firstPage || pageIndex > lastPage) {
      clip.replaceChildren();
      return;
    }

    if (clip.firstElementChild) return;
    const flow = flowTemplate.cloneNode(true) as HTMLElement;
    flow.style.transform = `translate3d(-${pageIndex * pageWidth}px, 0, 0)`;
    clip.append(flow);
  });
}

function calculatePagePadding(
  pageSize: number,
  preferredMin: number,
  preferredMax: number,
  ratio: number,
) {
  const preferred = Math.max(preferredMin, Math.min(preferredMax, pageSize * ratio));
  return Math.round(Math.min(preferred, pageSize * 0.2));
}

function calculateBookSize(width: number, height: number): BookSize | null {
  if (width <= 0 || height <= 0) return null;

  const landscape = width >= LANDSCAPE_MIN_WIDTH;
  const availableLeafWidth = Math.max(1, landscape ? (width - 22) / 2 : width - 4);
  const pageWidth = Math.max(1, Math.floor(Math.min(availableLeafWidth, height * PAGE_RATIO)));
  const pageHeight = Math.max(1, Math.floor(Math.min(height, pageWidth / PAGE_RATIO)));
  const horizontalPadding = calculatePagePadding(pageWidth, 12, 44, 0.09);
  const verticalPadding = calculatePagePadding(pageHeight, 16, 48, 0.075);

  return { pageWidth, pageHeight, landscape, horizontalPadding, verticalPadding };
}

function getPageFlipInputElement(host: HTMLElement | null) {
  return (
    host?.querySelector<HTMLElement>('.stf__block') ??
    host?.querySelector<HTMLElement>('.book-flip-engine') ??
    null
  );
}

function getPageFlipPoint(
  host: HTMLElement | null,
  clientX: number,
  clientY: number,
): PageFlipPoint | null {
  const input = getPageFlipInputElement(host);
  if (!input) return null;

  const rect = input.getBoundingClientRect();
  return { x: clientX - rect.left, y: clientY - rect.top };
}

function isPageFlipCorner(host: HTMLElement | null, clientX: number, clientY: number) {
  const input = getPageFlipInputElement(host);
  if (!input) return false;

  const rect = input.getBoundingClientRect();
  if (rect.width <= 0 || rect.height <= 0) return false;

  const x = clientX - rect.left;
  const y = clientY - rect.top;
  if (x < 0 || x > rect.width || y < 0 || y > rect.height) return false;

  const cornerSize = Math.hypot(rect.width, rect.height) / 5;
  const nearHorizontalEdge = x < cornerSize || x > rect.width - cornerSize;
  const nearVerticalEdge = y < cornerSize || y > rect.height - cornerSize;
  return nearHorizontalEdge && nearVerticalEdge;
}

function contributionIdFromTarget(target: EventTarget | null) {
  if (!(target instanceof Element)) return null;
  const contentId = target.closest<HTMLElement>('[data-book-content-id]')?.dataset.bookContentId;
  if (!contentId) return null;

  const parsed = Number(contentId);
  return Number.isInteger(parsed) ? parsed : null;
}

export function BookFlip({
  body,
  contents,
  bodyPages,
  sources,
  fontScale,
  fontFamily,
  turnPrompt,
  completionLabel,
  onComplete,
  onInteract,
}: BookFlipProps) {
  const stageRef = useRef<HTMLDivElement>(null);
  const engineHostRef = useRef<HTMLDivElement>(null);
  const engineRef = useRef<PageFlip | null>(null);
  const syncPageWindowRef = useRef<((pageIndex: number) => void) | null>(null);
  const currentPageRef = useRef(0);
  const pointerGestureRef = useRef<PointerGesture | null>(null);
  const [size, setSize] = useState<BookSize | null>(null);
  const [pageCount, setPageCount] = useState(1);
  const [currentPage, setCurrentPage] = useState(0);
  const [isReady, setIsReady] = useState(false);
  const [orientation, setOrientation] = useState<PageFlipOrientation>('portrait');
  const [state, setState] = useState<PageFlipState>('read');
  const [selectedContent, setSelectedContent] = useState<BookContentResponse | null>(null);
  const sourceContents = useMemo(
    () =>
      sources ??
      contents.filter(
        (content) => content.contentType === 'USER_ORIGINAL' && content.contributor !== null,
      ),
    [contents, sources],
  );
  const userContributions = useMemo(
    () =>
      [...contents]
        .filter(isUserContribution)
        .sort((left, right) => left.sequenceNo - right.sequenceNo),
    [contents],
  );
  const reducedMotion = useMemo(
    () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false,
    [],
  );

  useLayoutEffect(() => {
    const stage = stageRef.current;
    if (!stage) return;

    const update = () => {
      const next = calculateBookSize(stage.clientWidth, stage.clientHeight);
      setSize((current) =>
        current &&
        next &&
        current.pageWidth === next.pageWidth &&
        current.pageHeight === next.pageHeight &&
        current.landscape === next.landscape
          ? current
          : next,
      );
    };
    update();

    if (typeof ResizeObserver === 'undefined') {
      window.addEventListener('resize', update);
      return () => window.removeEventListener('resize', update);
    }

    const observer = new ResizeObserver(update);
    observer.observe(stage);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const host = engineHostRef.current;
    if (!host || !size) return;

    let disposed = false;
    let engine: PageFlip | null = null;
    let syncPages: ((pageIndex: number) => void) | null = null;

    const mount = async () => {
      setIsReady(false);
      await document.fonts?.ready;
      if (disposed) return;

      const options = {
        ...size,
        body,
        bodyPages,
        contents,
        sources: sourceContents,
        fontScale,
        fontFamily,
      };
      const flowTemplate = createFlow(options);
      const nextPageCount = measurePageCount(host, options, flowTemplate);
      const startPage = Math.min(currentPageRef.current, nextPageCount - 1);
      const root = document.createElement('div');
      root.className = 'book-flip-engine';
      root.style.width = `${size.pageWidth * (size.landscape ? 2 : 1)}px`;
      root.style.height = `${size.pageHeight}px`;
      const needsLandscapePlaceholder =
        size.landscape && nextPageCount > 1 && nextPageCount % 2 === 1;
      const enginePageCount = nextPageCount + (needsLandscapePlaceholder ? 1 : 0);
      const pages = Array.from({ length: enginePageCount }, (_, index) =>
        createPage(options, index, index >= nextPageCount),
      );
      const visiblePageCount = size.landscape ? 2 : 1;
      const updatePageWindow = (pageIndex: number) =>
        syncPageWindow(pages, flowTemplate, size.pageWidth, pageIndex, visiblePageCount);
      syncPages = updatePageWindow;
      updatePageWindow(startPage);
      root.append(...pages);
      host.replaceChildren(root);

      engine = new PageFlip(root, {
        width: size.pageWidth,
        height: size.pageHeight,
        size: 'fixed',
        startPage,
        usePortrait: true,
        autoSize: false,
        drawShadow: true,
        maxShadowOpacity: 0.32,
        flippingTime: reducedMotion ? 100 : 720,
        showCover: false,
        mobileScrollSupport: true,
        useMouseEvents: true,
        swipeDistance: SWIPE_DISTANCE,
        showPageCorners: !reducedMotion,
        disableFlipByClick: true,
      });

      engine.on<number>('flip', ({ data }) => {
        updatePageWindow(data);
        currentPageRef.current = data;
        setCurrentPage(data);
      });
      engine.on<PageFlipOrientation>('changeOrientation', ({ data }) => setOrientation(data));
      engine.on<PageFlipState>('changeState', ({ data }) => {
        setState(data);
      });
      engine.loadFromHTML(pages);
      const normalizedStartPage = engine.getCurrentPageIndex();
      engineRef.current = engine;
      syncPageWindowRef.current = updatePageWindow;
      updatePageWindow(normalizedStartPage);
      setState('read');
      setPageCount(nextPageCount);
      currentPageRef.current = normalizedStartPage;
      setCurrentPage(normalizedStartPage);
      setOrientation(size.landscape ? 'landscape' : 'portrait');
      setIsReady(true);
    };

    void mount();
    return () => {
      disposed = true;
      pointerGestureRef.current = null;
      engine?.off('flip');
      engine?.off('changeOrientation');
      engine?.off('changeState');
      engine?.destroy();
      if (engineRef.current === engine) engineRef.current = null;
      if (syncPageWindowRef.current === syncPages) syncPageWindowRef.current = null;
      host.replaceChildren();
      setIsReady(false);
    };
  }, [body, bodyPages, contents, fontFamily, fontScale, reducedMotion, size, sourceContents]);

  const goToPage = useCallback(
    (target: number, animate = false) => {
      const engine = engineRef.current;
      if (!engine) return;
      const bounded = Math.max(0, Math.min(target, pageCount - 1));
      syncPageWindowRef.current?.(bounded);
      if (animate && !reducedMotion && bounded > currentPageRef.current) {
        engine.flip(bounded, 'bottom');
        return;
      }
      engine.turnToPage(bounded);
      const normalized = engine.getCurrentPageIndex();
      syncPageWindowRef.current?.(normalized);
      currentPageRef.current = normalized;
      setCurrentPage(normalized);
    },
    [pageCount, reducedMotion],
  );
  const selectContribution = useCallback(
    (contentId: number) => {
      const content = contents.find((candidate) => candidate.contentId === contentId);
      if (!content || !isUserContribution(content)) return;
      setSelectedContent(content);
    },
    [contents],
  );

  const visiblePages = orientation === 'landscape' ? 2 : 1;
  const lastSpreadStart = Math.max(0, Math.floor((pageCount - 1) / visiblePages) * visiblePages);
  const endPage = Math.min(pageCount, currentPage + visiblePages);
  const status =
    endPage > currentPage + 1
      ? `${currentPage + 1}–${endPage} / ${pageCount}`
      : `${currentPage + 1} / ${pageCount}`;

  return (
    <section className="book-flip" data-state={state}>
      <div
        ref={stageRef}
        className="book-flip-stage"
        data-orientation={orientation}
        role="region"
        aria-label="책 읽기 영역"
        tabIndex={0}
        onKeyDown={(event) => {
          if (event.key === 'ArrowLeft') {
            event.preventDefault();
            goToPage(currentPage - visiblePages, true);
          }
          if (event.key === 'ArrowRight') {
            event.preventDefault();
            goToPage(currentPage + visiblePages, true);
          }
        }}
        onMouseDownCapture={(event) => event.stopPropagation()}
        onMouseMoveCapture={(event) => event.stopPropagation()}
        onMouseUpCapture={(event) => event.stopPropagation()}
        onTouchStartCapture={(event) => event.stopPropagation()}
        onTouchMoveCapture={(event) => event.stopPropagation()}
        onTouchEndCapture={(event) => event.stopPropagation()}
        onTouchCancelCapture={(event) => event.stopPropagation()}
        onPointerDown={(event) => {
          if (event.isPrimary === false || (event.pointerType === 'mouse' && event.button !== 0)) {
            return;
          }

          onInteract?.();
          const pointerType = event.pointerType || 'touch';
          if (pointerType !== 'touch') event.preventDefault();
          event.currentTarget.focus({ preventScroll: true });
          const engine = engineRef.current;
          const point = getPageFlipPoint(engineHostRef.current, event.clientX, event.clientY);
          const contributionId = contributionIdFromTarget(event.target);
          const engineStarted =
            contributionId === null &&
            !reducedMotion &&
            pointerType !== 'touch' &&
            engine !== null &&
            point !== null;

          if (engineStarted) {
            engine.startUserTouch(point);
          }

          pointerGestureRef.current = {
            pointerId: event.pointerId,
            pointerType,
            startX: event.clientX,
            startY: event.clientY,
            startTime: Date.now(),
            maxDistance: 0,
            engineStarted,
            contributionId,
          };
          event.currentTarget.setPointerCapture?.(event.pointerId);
        }}
        onPointerMove={(event) => {
          const gesture = pointerGestureRef.current;
          if (gesture?.pointerId === event.pointerId) {
            gesture.maxDistance = Math.max(
              gesture.maxDistance,
              Math.hypot(event.clientX - gesture.startX, event.clientY - gesture.startY),
            );
          }

          const engine = engineRef.current;
          const point = getPageFlipPoint(engineHostRef.current, event.clientX, event.clientY);
          if (!engine || !point || reducedMotion) return;
          if (!gesture) {
            if (event.pointerType === 'mouse') engine.userMove(point, false);
            return;
          }
          if (gesture.pointerId !== event.pointerId) return;

          if (!gesture.engineStarted) {
            const deltaX = event.clientX - gesture.startX;
            const deltaY = event.clientY - gesture.startY;
            if (Math.abs(deltaX) <= TAP_MAX_DISTANCE || Math.abs(deltaX) <= Math.abs(deltaY)) {
              return;
            }

            const startPoint = getPageFlipPoint(
              engineHostRef.current,
              gesture.startX,
              gesture.startY,
            );
            if (!startPoint) return;
            engine.startUserTouch(startPoint);
            gesture.engineStarted = true;
          }

          engine.userMove(point, gesture.pointerType === 'touch');
        }}
        onPointerUp={(event) => {
          const gesture = pointerGestureRef.current;
          if (!gesture || gesture.pointerId !== event.pointerId) return;
          pointerGestureRef.current = null;
          if (event.currentTarget.hasPointerCapture?.(event.pointerId)) {
            event.currentTarget.releasePointerCapture(event.pointerId);
          }

          const deltaX = event.clientX - gesture.startX;
          const deltaY = event.clientY - gesture.startY;
          const distance = Math.hypot(deltaX, deltaY);
          gesture.maxDistance = Math.max(gesture.maxDistance, distance);
          const duration = Date.now() - gesture.startTime;
          const engine = engineRef.current;
          const point = getPageFlipPoint(engineHostRef.current, event.clientX, event.clientY);

          if (gesture.engineStarted && engine && point) {
            engine.userMove(point, gesture.pointerType === 'touch');
            engine.userStop(point);
          }

          if (gesture.maxDistance > TAP_MAX_DISTANCE) {
            const isSwipe =
              !gesture.engineStarted &&
              duration < SWIPE_MAX_DURATION_MS &&
              Math.abs(deltaX) > SWIPE_DISTANCE &&
              Math.abs(deltaY) < SWIPE_DISTANCE * 2;
            if (isSwipe) {
              goToPage(currentPageRef.current + (deltaX > 0 ? -visiblePages : visiblePages), true);
            }
            return;
          }

          if (duration > TAP_MAX_DURATION_MS) return;

          if (gesture.contributionId !== null) {
            selectContribution(gesture.contributionId);
            return;
          }

          const rect = event.currentTarget.getBoundingClientRect();
          const relativeX = (event.clientX - rect.left) / rect.width;
          if (
            gesture.engineStarted &&
            isPageFlipCorner(engineHostRef.current, event.clientX, event.clientY)
          ) {
            return;
          }

          if (relativeX <= 0.28 && currentPageRef.current > 0) {
            goToPage(currentPageRef.current - visiblePages, true);
          } else if (relativeX >= 0.72 && currentPageRef.current + visiblePages < pageCount) {
            goToPage(currentPageRef.current + visiblePages, true);
          }
        }}
        onPointerCancel={(event) => {
          const gesture = pointerGestureRef.current;
          if (!gesture || gesture.pointerId !== event.pointerId) return;
          pointerGestureRef.current = null;

          const engine = engineRef.current;
          const point = getPageFlipPoint(engineHostRef.current, event.clientX, event.clientY);
          if (gesture.engineStarted && engine && point) {
            engine.userStop(point);
          }
        }}
      >
        <div ref={engineHostRef} className="book-flip-engine-host" data-testid="book-flip-engine" />
      </div>

      {isReady && currentPage === 0 && endPage < pageCount && turnPrompt && (
        <button
          type="button"
          aria-label={turnPrompt}
          className="book-flip-turn-coach"
          onClick={() => goToPage(currentPage + visiblePages, true)}
        >
          <span>페이지를 눌러</span>
          <strong>
            다음 장 넘기기 <span aria-hidden="true">›</span>
          </strong>
        </button>
      )}

      <nav aria-label="소설 페이지 이동" className="book-flip-controls">
        <button
          type="button"
          aria-label="이전 페이지"
          disabled={currentPage === 0}
          onClick={() => goToPage(currentPage - visiblePages, true)}
        >
          <span aria-hidden="true">‹</span>
        </button>
        <input
          aria-label="읽기 진행률"
          type="range"
          min="0"
          max={lastSpreadStart}
          step={visiblePages}
          value={currentPage}
          onChange={(event) => goToPage(Number(event.currentTarget.value))}
        />
        <p role="status" aria-live="polite">
          {status}
        </p>
        <button
          type="button"
          aria-label="다음 페이지"
          disabled={endPage >= pageCount}
          onClick={() => {
            onInteract?.();
            goToPage(currentPage + visiblePages, true);
          }}
        >
          <span aria-hidden="true">›</span>
        </button>
      </nav>

      <div className="book-flip-footer">
        {isReady && endPage >= pageCount && completionLabel && onComplete ? (
          <button type="button" className="book-flip-completion" onClick={onComplete}>
            <span aria-hidden="true">✓</span>
            <span>{completionLabel}</span>
          </button>
        ) : (
          <p className="book-flip-hint">책 모서리를 잡아 천천히 넘겨 보세요</p>
        )}
      </div>
      {userContributions.length > 0 && (
        <section
          className="book-flip-contribution-shortcuts"
          aria-label="사용자 기여 문장 정보"
          style={{
            display: 'flex',
            flexDirection: 'column',
            maxHeight: `${Math.max(112, Math.min(288, (size?.pageHeight ?? 400) - 112))}px`,
            overflow: 'hidden',
          }}
        >
          <p>강조 문장 정보 보기</p>
          <div
            className="book-flip-contribution-shortcut-list"
            style={{ flex: '1 1 auto', minHeight: 0, overflowY: 'auto' }}
          >
            {userContributions.map((content) => {
              const contributorName = content.contributor?.nickname ?? '알 수 없는 작가';
              const typeLabel = contributionTypeLabel(content);
              return (
                <button
                  key={content.contentId}
                  type="button"
                  aria-label={`${contributorName}의 ${typeLabel}: ${content.content}`}
                  onFocus={(event) => {
                    event.currentTarget.scrollIntoView?.({ block: 'nearest' });
                  }}
                  onClick={() => {
                    onInteract?.();
                    selectContribution(content.contentId);
                  }}
                >
                  <strong>{contributorName}</strong>
                  <span>{typeLabel}</span>
                </button>
              );
            })}
          </div>
        </section>
      )}
      <section className="sr-only" role="region" aria-label="공동 소설 본문">
        <p>{body}</p>
        {sourceContents.length > 0 && (
          <section>
            <h2>수록 문장 출처</h2>
            {sourceContents.map((source) => (
              <div key={source.contentId}>
                <p>{source.contributor?.nickname}</p>
                <blockquote>{source.content}</blockquote>
              </div>
            ))}
          </section>
        )}
      </section>

      <Modal
        open={selectedContent !== null}
        title="문장 정보"
        onClose={() => setSelectedContent(null)}
        footer={
          <Button variant="secondary" onClick={() => setSelectedContent(null)}>
            닫기
          </Button>
        }
      >
        {selectedContent && (
          <div className="space-y-4">
            <div>
              <span
                className={
                  selectedContent.contentType === 'USER_EDITED'
                    ? 'inline-flex rounded-full bg-amber-100 px-2.5 py-1 text-xs font-bold text-amber-800'
                    : 'inline-flex rounded-full bg-violet-100 px-2.5 py-1 text-xs font-bold text-violet-800'
                }
              >
                {contributionTypeLabel(selectedContent)}
              </span>
              <blockquote className="mt-3 rounded-2xl bg-stone-50 px-4 py-3 font-serif text-sm leading-7 text-stone-800">
                “{selectedContent.content}”
              </blockquote>
            </div>

            <dl className="grid gap-2 text-sm">
              <div className="flex items-center justify-between gap-4 rounded-xl border border-stone-200 px-3 py-2.5">
                <dt className="text-stone-500">작성자</dt>
                <dd className="font-bold text-stone-900">
                  {selectedContent.contributor?.nickname ?? '알 수 없는 작가'}
                </dd>
              </div>
            </dl>

            {selectedContent.contentType === 'USER_EDITED' &&
              selectedContent.contribution?.originalContent && (
                <section className="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3">
                  <p className="text-xs font-extrabold text-amber-800">제출한 원문</p>
                  <blockquote className="mt-2 font-serif text-sm leading-6 text-stone-700">
                    “{selectedContent.contribution.originalContent}”
                  </blockquote>
                  <p className="mt-2 text-xs leading-5 text-amber-700">
                    이야기 흐름에 맞게 AI가 최소한으로 다듬었습니다.
                  </p>
                </section>
              )}

            {selectedContent.contribution && (
              <section className="space-y-3 rounded-2xl bg-violet-50/70 px-4 py-3">
                <div className="flex items-center justify-between gap-4">
                  <p className="text-xs font-extrabold text-violet-800">보스전 기여</p>
                  <p className="text-sm font-black text-violet-900">
                    {selectedContent.contribution.finalDamage.toLocaleString()} 피해
                  </p>
                </div>

                {selectedContent.contribution.moods.length > 0 && (
                  <div>
                    <p className="text-xs font-bold text-stone-500">문장 분위기</p>
                    <div className="mt-1.5 flex flex-wrap gap-1.5">
                      {selectedContent.contribution.moods.map((mood) => (
                        <span
                          key={`${mood.code}-${mood.rank}`}
                          className="rounded-full bg-white px-2.5 py-1 text-xs font-bold text-violet-700 shadow-sm"
                          title={`감지 강도 ${Math.round(mood.strength * 100)}%`}
                        >
                          {getMoodLabel(mood.code)} {Math.round(mood.strength * 100)}%
                        </span>
                      ))}
                    </div>
                  </div>
                )}

                {selectedContent.contribution.usedDailyWords.length > 0 && (
                  <div>
                    <p className="text-xs font-bold text-stone-500">사용한 제시어</p>
                    <div className="mt-1.5 flex flex-wrap gap-1.5">
                      {selectedContent.contribution.usedDailyWords.map((word) => (
                        <span
                          key={word}
                          className="rounded-full bg-emerald-100 px-2.5 py-1 text-xs font-bold text-emerald-800"
                        >
                          #{word}
                        </span>
                      ))}
                    </div>
                  </div>
                )}
              </section>
            )}
          </div>
        )}
      </Modal>
    </section>
  );
}
