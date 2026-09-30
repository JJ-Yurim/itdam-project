declare module 'page-flip' {
  export type PageFlipOrientation = 'portrait' | 'landscape';
  export type PageFlipState = 'read' | 'user_fold' | 'fold_corner' | 'flipping';

  export interface PageFlipEvent<T> {
    data: T;
    object: PageFlip;
  }

  export interface PageFlipSettings {
    width: number;
    height: number;
    size?: 'fixed' | 'stretch';
    startPage?: number;
    minWidth?: number;
    maxWidth?: number;
    minHeight?: number;
    maxHeight?: number;
    drawShadow?: boolean;
    flippingTime?: number;
    usePortrait?: boolean;
    startZIndex?: number;
    autoSize?: boolean;
    maxShadowOpacity?: number;
    showCover?: boolean;
    mobileScrollSupport?: boolean;
    clickEventForward?: boolean;
    useMouseEvents?: boolean;
    swipeDistance?: number;
    showPageCorners?: boolean;
    disableFlipByClick?: boolean;
  }

  export interface PageFlipPoint {
    x: number;
    y: number;
  }

  export class PageFlip {
    constructor(element: HTMLElement, settings: PageFlipSettings);
    loadFromHTML(elements: HTMLElement[] | NodeListOf<HTMLElement>): void;
    destroy(): void;
    turnToPage(page: number): void;
    flip(page: number, corner?: 'top' | 'bottom'): void;
    flipNext(corner?: 'top' | 'bottom'): void;
    flipPrev(corner?: 'top' | 'bottom'): void;
    startUserTouch(point: PageFlipPoint): void;
    userMove(point: PageFlipPoint, isTouch: boolean): void;
    userStop(point: PageFlipPoint, isSwipe?: boolean): void;
    getCurrentPageIndex(): number;
    getPageCount(): number;
    getOrientation(): PageFlipOrientation;
    on<T>(eventName: string, callback: (event: PageFlipEvent<T>) => void): PageFlip;
    off(eventName: string): void;
  }
}
