import { createContext, useContext } from 'react';
import type { AdaptiveLayout } from '../hooks/useAdaptiveLayout';

/**
 * The shell measures once and shares the result, so a route does not attach a
 * second ResizeObserver to make the same decision differently.
 */
export const LayoutContext = createContext<AdaptiveLayout | null>(null);

const FALLBACK: AdaptiveLayout = {
  width: 1024,
  height: 768,
  widthRem: 64,
  rootFontSize: 16,
  mode: 'expanded',
  twoPane: true,
  landscape: true,
  safeArea: { top: 0, right: 0, bottom: 0, left: 0 },
  keyboardInset: 0,
  keyboardVisible: false,
  segments: null,
  segmentsSupported: false,
  prefersReducedMotion: false,
};

/** Usable outside the shell (and in unit tests) without crashing. */
export function useLayout(): AdaptiveLayout {
  return useContext(LayoutContext) ?? FALLBACK;
}
