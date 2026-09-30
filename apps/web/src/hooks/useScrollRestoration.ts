import { useEffect, useRef } from 'react';
import { useLocation, useNavigationType } from 'react-router-dom';

const KEY = 'odivrelo.scroll.v1';

/**
 * Remembers scroll position per history entry.
 *
 * React Router gives every entry a stable `key`, so the position is stored
 * against that rather than against the path: two visits to the same results page
 * from different points in the back stack keep their own positions.
 *
 * Restoring happens on a POP (back or forward) and on a reload of the same entry.
 * A brand new PUSH starts at the top, which is what a reader expects from a new
 * page. sessionStorage is used rather than IndexedDB because losing a scroll
 * offset costs nothing, and every access is guarded.
 */
function readMap(): Record<string, number> {
  try {
    const raw = globalThis.sessionStorage?.getItem(KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as unknown;
    return parsed && typeof parsed === 'object' ? (parsed as Record<string, number>) : {};
  } catch {
    return {};
  }
}

function writeMap(map: Record<string, number>): void {
  try {
    globalThis.sessionStorage?.setItem(KEY, JSON.stringify(map));
  } catch {
    // Full or blocked storage: the reader loses a scroll offset, nothing more.
  }
}

export function useScrollRestoration(): void {
  const location = useLocation();
  const navigationType = useNavigationType();
  const keyRef = useRef(location.key);

  // Save the outgoing position before the new entry paints.
  useEffect(() => {
    const previousKey = keyRef.current;
    return () => {
      const map = readMap();
      map[previousKey] = globalThis.scrollY ?? 0;
      writeMap(map);
    };
  }, [location.key]);

  useEffect(() => {
    keyRef.current = location.key;
    // The browser's own restoration would fight ours on a reload.
    if ('scrollRestoration' in globalThis.history) globalThis.history.scrollRestoration = 'manual';
    const saved = readMap()[location.key];
    if (navigationType === 'POP' && typeof saved === 'number') {
      // Two frames: one for the route to commit, one for its content to lay out.
      requestAnimationFrame(() => requestAnimationFrame(() => globalThis.scrollTo({ top: saved, behavior: 'instant' })));
    } else if (navigationType === 'PUSH') {
      globalThis.scrollTo({ top: 0, behavior: 'instant' });
    } else if (typeof saved === 'number') {
      requestAnimationFrame(() => requestAnimationFrame(() => globalThis.scrollTo({ top: saved, behavior: 'instant' })));
    }
  }, [location.key, navigationType]);

  // A reload, a tab switch or the app being backgrounded must not lose the position.
  useEffect(() => {
    const save = () => {
      const map = readMap();
      map[keyRef.current] = globalThis.scrollY ?? 0;
      writeMap(map);
    };
    globalThis.addEventListener?.('pagehide', save);
    globalThis.addEventListener?.('visibilitychange', save);
    return () => {
      globalThis.removeEventListener?.('pagehide', save);
      globalThis.removeEventListener?.('visibilitychange', save);
    };
  }, []);
}
