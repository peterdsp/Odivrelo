import { useEffect, useState } from 'react';

/**
 * Whether the browser believes it has a connection.
 *
 * `navigator.onLine` only ever proves the negative reliably: false means
 * definitely offline, true means "a network interface exists", which is not the
 * same as reachable. Every data path therefore still handles a failed fetch on
 * its own, and this flag is used for wording and for disabling actions that
 * cannot possibly work.
 */
export function useOnline(): boolean {
  const [online, setOnline] = useState<boolean>(() => globalThis.navigator?.onLine ?? true);

  useEffect(() => {
    const goOnline = () => setOnline(true);
    const goOffline = () => setOnline(false);
    globalThis.addEventListener?.('online', goOnline);
    globalThis.addEventListener?.('offline', goOffline);
    setOnline(globalThis.navigator?.onLine ?? true);
    return () => {
      globalThis.removeEventListener?.('online', goOnline);
      globalThis.removeEventListener?.('offline', goOffline);
    };
  }, []);

  return online;
}
