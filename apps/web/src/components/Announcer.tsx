import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

/**
 * The application's live regions.
 *
 * Two regions exist for the whole app rather than one per component, because a
 * live region that is created at the same moment its text appears is frequently
 * not announced at all. These are in the DOM from first paint and only their
 * text changes.
 *
 * `status` is polite: results loaded, pack installed, preference changed.
 * `alert` is assertive: validation failures and errors that block the task.
 */
export interface AnnouncerApi {
  announce: (message: string) => void;
  alert: (message: string) => void;
}

const AnnouncerContext = createContext<AnnouncerApi | null>(null);

export function AnnouncerProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState('');
  const [assertive, setAssertive] = useState('');
  const counter = useRef(0);

  const api = useMemo<AnnouncerApi>(
    () => ({
      announce: (message: string) => {
        // Screen readers skip an identical string. A zero-width joiner alternates
        // so a repeated announcement is still heard, without changing the text.
        counter.current += 1;
        setStatus(counter.current % 2 === 0 ? message : `${message}‍`);
      },
      alert: (message: string) => {
        counter.current += 1;
        setAssertive(counter.current % 2 === 0 ? message : `${message}‍`);
      },
    }),
    [],
  );

  return (
    <AnnouncerContext.Provider value={api}>
      {children}
      <div className="pv-visually-hidden" role="status" aria-live="polite" aria-atomic="true">
        {status}
      </div>
      <div className="pv-visually-hidden" role="alert" aria-live="assertive" aria-atomic="true">
        {assertive}
      </div>
    </AnnouncerContext.Provider>
  );
}

export function useAnnouncer(): AnnouncerApi {
  const context = useContext(AnnouncerContext);
  // Components are usable in isolation (and in unit tests) without the provider.
  const fallback = useMemo<AnnouncerApi>(() => ({ announce: () => {}, alert: () => {} }), []);
  return context ?? fallback;
}

/**
 * Announces once when `message` changes, for components that render a status line
 * anyway.
 *
 * The announcement happens in an effect rather than during render. Speaking during
 * render would fire on every re-render and, under concurrent rendering, on renders
 * that are then thrown away, so a reader could hear something that was never shown.
 */
export function useAnnounceOnChange(message: string | null, assertive = false): void {
  const { announce, alert } = useAnnouncer();
  useEffect(() => {
    if (message === null) return;
    if (assertive) alert(message);
    else announce(message);
  }, [message, assertive, alert, announce]);
}
