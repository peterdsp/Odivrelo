import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

/**
 * Layout decided by measurement, never by device name.
 *
 * Breakpoints are expressed in `rem`, so they move with the reader's text size:
 * a 1024 px window at 200% text has the same usable columns as a 512 px window
 * at 100%, and it gets the compact layout accordingly. Safe-area insets, the
 * virtual keyboard and viewport segments are all read from the platform rather
 * than assumed.
 */

export type LayoutMode = 'compact' | 'medium' | 'expanded';

export interface SafeArea {
  readonly top: number;
  readonly right: number;
  readonly bottom: number;
  readonly left: number;
}

export interface ViewportSegment {
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
}

export interface AdaptiveLayout {
  /** Measured width of the layout container, in CSS pixels. */
  readonly width: number;
  readonly height: number;
  /** Width expressed in root-font-size units, which is what the breakpoints use. */
  readonly widthRem: number;
  readonly rootFontSize: number;
  readonly mode: LayoutMode;
  /** True when there is genuinely room for a list and a detail pane side by side. */
  readonly twoPane: boolean;
  readonly landscape: boolean;
  readonly safeArea: SafeArea;
  /** Pixels of viewport currently covered by the virtual keyboard. */
  readonly keyboardInset: number;
  readonly keyboardVisible: boolean;
  /** Present only on a device that reports more than one viewport segment. */
  readonly segments: readonly ViewportSegment[] | null;
  readonly segmentsSupported: boolean;
  readonly prefersReducedMotion: boolean;
}

/**
 * 48rem: two readable columns stop overlapping.
 * 64rem: a results list and a journey detail both fit at a sensible measure.
 */
const MEDIUM_REM = 48;
const EXPANDED_REM = 64;
/** A two-pane layout needs vertical room too; a short landscape window does not get one. */
const MIN_TWO_PANE_HEIGHT = 480;

function readRootFontSize(): number {
  try {
    const root = globalThis.document?.documentElement;
    if (!root) return 16;
    const size = Number.parseFloat(globalThis.getComputedStyle(root).fontSize);
    return Number.isFinite(size) && size > 0 ? size : 16;
  } catch {
    return 16;
  }
}

function readSafeArea(): SafeArea {
  const fallback: SafeArea = { top: 0, right: 0, bottom: 0, left: 0 };
  try {
    const probe = globalThis.document?.getElementById('pv-safe-area-probe');
    if (!probe) return fallback;
    const style = globalThis.getComputedStyle(probe);
    const pick = (value: string) => {
      const n = Number.parseFloat(value);
      return Number.isFinite(n) ? n : 0;
    };
    return {
      top: pick(style.paddingTop),
      right: pick(style.paddingRight),
      bottom: pick(style.paddingBottom),
      left: pick(style.paddingLeft),
    };
  } catch {
    return fallback;
  }
}

interface SegmentCapableVisualViewport extends VisualViewport {
  readonly segments?: readonly DOMRect[];
}

function readSegments(): { segments: readonly ViewportSegment[] | null; supported: boolean } {
  try {
    const supported =
      typeof globalThis.CSS?.supports === 'function' &&
      (globalThis.CSS.supports('width: env(viewport-segment-width 0 0)') ||
        globalThis.CSS.supports('left: env(viewport-segment-left 0 0)'));
    const viewport = globalThis.visualViewport as SegmentCapableVisualViewport | null;
    const raw = viewport?.segments;
    if (!raw || raw.length < 2) return { segments: null, supported };
    return {
      segments: [...raw].map((r) => ({ x: r.x, y: r.y, width: r.width, height: r.height })),
      supported: true,
    };
  } catch {
    return { segments: null, supported: false };
  }
}

function readReducedMotion(): boolean {
  try {
    return globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
  } catch {
    return false;
  }
}

export interface UseAdaptiveLayoutResult extends AdaptiveLayout {
  /** Attach to the element whose width should drive the layout. */
  readonly containerRef: (node: HTMLElement | null) => void;
}

export function useAdaptiveLayout(): UseAdaptiveLayoutResult {
  const nodeRef = useRef<HTMLElement | null>(null);
  const [measured, setMeasured] = useState<{ width: number; height: number }>(() => ({
    width: globalThis.innerWidth || 1024,
    height: globalThis.innerHeight || 768,
  }));
  const [rootFontSize, setRootFontSize] = useState(readRootFontSize);
  const [safeArea, setSafeArea] = useState<SafeArea>(readSafeArea);
  const [keyboardInset, setKeyboardInset] = useState(0);
  const [segmentState, setSegmentState] = useState(readSegments);
  const [prefersReducedMotion, setPrefersReducedMotion] = useState(readReducedMotion);

  const remeasure = useCallback(() => {
    const node = nodeRef.current;
    const width = node?.clientWidth || globalThis.innerWidth || 1024;
    const height = node?.clientHeight || globalThis.innerHeight || 768;
    setMeasured((previous) =>
      previous.width === width && previous.height === height ? previous : { width, height },
    );
    setRootFontSize(readRootFontSize());
    setSafeArea((previous) => {
      const next = readSafeArea();
      return previous.top === next.top &&
        previous.right === next.right &&
        previous.bottom === next.bottom &&
        previous.left === next.left
        ? previous
        : next;
    });
    setSegmentState(readSegments());
  }, []);

  const containerRef = useCallback(
    (node: HTMLElement | null) => {
      nodeRef.current = node;
      remeasure();
    },
    [remeasure],
  );

  // Container size, not window size: a pane inside a two-pane layout adapts too.
  useEffect(() => {
    const node = nodeRef.current;
    if (typeof ResizeObserver === 'undefined' || !node) {
      const onResize = () => remeasure();
      globalThis.addEventListener?.('resize', onResize);
      globalThis.addEventListener?.('orientationchange', onResize);
      remeasure();
      return () => {
        globalThis.removeEventListener?.('resize', onResize);
        globalThis.removeEventListener?.('orientationchange', onResize);
      };
    }
    const observer = new ResizeObserver(() => remeasure());
    observer.observe(node);
    const onResize = () => remeasure();
    globalThis.addEventListener?.('resize', onResize);
    globalThis.addEventListener?.('orientationchange', onResize);
    return () => {
      observer.disconnect();
      globalThis.removeEventListener?.('resize', onResize);
      globalThis.removeEventListener?.('orientationchange', onResize);
    };
  }, [remeasure]);

  // The virtual keyboard. visualViewport is the only honest source for this:
  // guessing from focus state gets it wrong on every platform.
  useEffect(() => {
    const viewport = globalThis.visualViewport;
    if (!viewport) return;
    const update = () => {
      const covered = Math.max(0, (globalThis.innerHeight || viewport.height) - viewport.height - viewport.offsetTop);
      setKeyboardInset(Math.round(covered));
    };
    update();
    viewport.addEventListener('resize', update);
    viewport.addEventListener('scroll', update);
    return () => {
      viewport.removeEventListener('resize', update);
      viewport.removeEventListener('scroll', update);
    };
  }, []);

  useEffect(() => {
    const query = globalThis.matchMedia?.('(prefers-reduced-motion: reduce)');
    if (!query) return;
    const update = () => setPrefersReducedMotion(query.matches);
    query.addEventListener('change', update);
    return () => query.removeEventListener('change', update);
  }, []);

  // Publish the keyboard inset as a custom property so CSS can react without
  // every component subscribing to this hook.
  useEffect(() => {
    const root = globalThis.document?.documentElement;
    if (root) root.style.setProperty('--pv-keyboard-inset', `${keyboardInset}px`);
  }, [keyboardInset]);

  return useMemo<UseAdaptiveLayoutResult>(() => {
    const widthRem = measured.width / rootFontSize;
    const mode: LayoutMode = widthRem >= EXPANDED_REM ? 'expanded' : widthRem >= MEDIUM_REM ? 'medium' : 'compact';
    const twoPane = mode === 'expanded' && measured.height >= MIN_TWO_PANE_HEIGHT && keyboardInset === 0;
    return {
      containerRef,
      width: measured.width,
      height: measured.height,
      widthRem,
      rootFontSize,
      mode,
      twoPane,
      landscape: measured.width > measured.height,
      safeArea,
      keyboardInset,
      keyboardVisible: keyboardInset > 80,
      segments: segmentState.segments,
      segmentsSupported: segmentState.supported,
      prefersReducedMotion,
    };
  }, [containerRef, measured, rootFontSize, safeArea, keyboardInset, segmentState, prefersReducedMotion]);
}
