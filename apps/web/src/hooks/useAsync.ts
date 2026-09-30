import { useCallback, useEffect, useInsertionEffect, useRef, useState } from 'react';

export type AsyncState<T> =
  | { readonly status: 'idle' }
  | { readonly status: 'loading' }
  | { readonly status: 'ready'; readonly value: T }
  | { readonly status: 'error'; readonly error: unknown };

export interface UseAsyncResult<T> {
  readonly state: AsyncState<T>;
  readonly reload: () => void;
  /** The last successful value, kept while a reload is in flight. */
  readonly last: T | null;
}

/**
 * Runs an async task, aborts it when the inputs change or the component goes
 * away, and exposes an explicit retry.
 *
 * The previous value is kept across a reload so a refresh does not blank the
 * page the reader is looking at, and an abort is never reported as an error:
 * navigating away is not a failure.
 */
export function useAsync<T>(
  task: (signal: AbortSignal) => Promise<T>,
  deps: readonly unknown[],
  options: { readonly enabled?: boolean } = {},
): UseAsyncResult<T> {
  const enabled = options.enabled ?? true;
  const [state, setState] = useState<AsyncState<T>>(enabled ? { status: 'loading' } : { status: 'idle' });
  const [attempt, setAttempt] = useState(0);
  const lastRef = useRef<T | null>(null);
  const taskRef = useRef(task);
  // Assigned in an effect rather than during render: a render can be discarded,
  // and a ref written during a discarded render would leave a task behind that
  // was never meant to run.
  useInsertionEffect(() => {
    taskRef.current = task;
  }, [task]);

  useEffect(() => {
    if (!enabled) {
      setState({ status: 'idle' });
      return;
    }
    const controller = new AbortController();
    let live = true;
    setState({ status: 'loading' });
    taskRef
      .current(controller.signal)
      .then((value) => {
        if (!live || controller.signal.aborted) return;
        lastRef.current = value;
        setState({ status: 'ready', value });
      })
      .catch((error: unknown) => {
        if (!live || controller.signal.aborted) return;
        if ((error as Error)?.name === 'AbortError') return;
        setState({ status: 'error', error });
      });
    return () => {
      live = false;
      controller.abort();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, attempt, ...deps]);

  const reload = useCallback(() => setAttempt((n) => n + 1), []);

  return { state, reload, last: lastRef.current };
}
