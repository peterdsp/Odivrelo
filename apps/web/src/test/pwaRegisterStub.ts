/**
 * Stands in for `virtual:pwa-register`, which only exists when vite-plugin-pwa is
 * in the config. Vitest does not load the plugin, so without this the update
 * prompt would be untestable rather than merely untested.
 *
 * It behaves like a browser with no service worker: registration succeeds and
 * nothing is ever waiting, which is the correct baseline. The end-to-end suite
 * exercises the real update flow against a real worker.
 */
export interface RegisterSWOptions {
  immediate?: boolean;
  onNeedRefresh?: () => void;
  onOfflineReady?: () => void;
  onRegisteredSW?: (url: string, registration: ServiceWorkerRegistration | undefined) => void;
  onRegisterError?: (error: unknown) => void;
}

export function registerSW(options: RegisterSWOptions = {}): (reloadPage?: boolean) => Promise<void> {
  options.onRegisteredSW?.('/sw.js', undefined);
  return async () => {};
}
