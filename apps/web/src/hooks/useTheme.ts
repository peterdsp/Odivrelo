import { useCallback, useEffect, useState } from 'react';
import { parseBoolean, parseOneOf, readPref, writePref } from '../lib/prefs';

export type ThemeChoice = 'system' | 'light' | 'dark';

const THEME_PREF = 'theme';
const MOTION_PREF = 'reduceMotion';
const UNDERLINE_PREF = 'underlineLinks';

const THEME_VALUES = ['system', 'light', 'dark'] as const;

/**
 * Appearance, reduced motion and link underlining.
 *
 * The tokens already honour `prefers-color-scheme` and `prefers-reduced-motion`,
 * so "system" sets no attribute at all and simply lets CSS do its job. An
 * explicit choice adds `data-theme`, which the token file overrides on.
 */
export function useTheme() {
  const [theme, setThemeState] = useState<ThemeChoice>(() =>
    readPref<ThemeChoice>(THEME_PREF, 'system', parseOneOf(THEME_VALUES)),
  );
  const [forceReducedMotion, setForceReducedMotionState] = useState<boolean>(() =>
    readPref<boolean>(MOTION_PREF, false, parseBoolean),
  );
  const [underlineLinks, setUnderlineLinksState] = useState<boolean>(() =>
    readPref<boolean>(UNDERLINE_PREF, false, parseBoolean),
  );

  useEffect(() => {
    const root = globalThis.document?.documentElement;
    if (!root) return;
    if (theme === 'system') root.removeAttribute('data-theme');
    else root.setAttribute('data-theme', theme);
  }, [theme]);

  useEffect(() => {
    const root = globalThis.document?.documentElement;
    if (!root) return;
    root.toggleAttribute('data-reduce-motion', forceReducedMotion);
  }, [forceReducedMotion]);

  useEffect(() => {
    const root = globalThis.document?.documentElement;
    if (!root) return;
    root.toggleAttribute('data-underline-links', underlineLinks);
  }, [underlineLinks]);

  const setTheme = useCallback((next: ThemeChoice) => {
    writePref(THEME_PREF, next);
    setThemeState(next);
  }, []);

  const setForceReducedMotion = useCallback((next: boolean) => {
    writePref(MOTION_PREF, next);
    setForceReducedMotionState(next);
  }, []);

  const setUnderlineLinks = useCallback((next: boolean) => {
    writePref(UNDERLINE_PREF, next);
    setUnderlineLinksState(next);
  }, []);

  return { theme, setTheme, forceReducedMotion, setForceReducedMotion, underlineLinks, setUnderlineLinks };
}
