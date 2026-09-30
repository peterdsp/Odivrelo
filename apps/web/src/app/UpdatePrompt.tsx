import { useEffect, useRef, useState } from 'react';
import { useI18n } from '../i18n/I18nProvider';
import { Button } from '../components/primitives';
import { useAnnouncer } from '../components/Announcer';
import { useData } from './DataProvider';

/**
 * Service-worker update flow, and the storage-eviction recovery notice.
 *
 * The rule the app follows is that a new build never replaces the running one
 * underneath the reader. A waiting worker produces a visible prompt; only when
 * the reader accepts does the app tell the worker to `skipWaiting` and reload.
 * That means a passenger reading boarding instructions on a platform does not
 * have the page swapped out from under them.
 *
 * `virtual:pwa-register` is provided by vite-plugin-pwa. It is imported
 * dynamically so that a dev server without the plugin, and the unit tests, both
 * still work.
 */
export function UpdatePrompt() {
  const { t } = useI18n();
  const { announce } = useAnnouncer();
  const { storageEvicted } = useData();
  const [needRefresh, setNeedRefresh] = useState(false);
  const [dismissed, setDismissed] = useState(false);
  const [evictionDismissed, setEvictionDismissed] = useState(false);
  const updateRef = useRef<((reload?: boolean) => Promise<void>) | null>(null);
  const promptRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const { registerSW } = await import('virtual:pwa-register');
        if (cancelled) return;
        updateRef.current = registerSW({
          immediate: true,
          onNeedRefresh() {
            setNeedRefresh(true);
            setDismissed(false);
          },
          onOfflineReady() {
            announce(t('update.offlineReady'));
          },
        });
      } catch {
        // No service worker in this environment (dev without the plugin, or a
        // browser that refuses one). Nothing to prompt about.
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // The prompt is an interruption, so it takes focus once. It is not a modal:
  // the reader can ignore it and carry on.
  useEffect(() => {
    if (needRefresh && !dismissed) promptRef.current?.focus();
  }, [needRefresh, dismissed]);

  const showUpdate = needRefresh && !dismissed;
  const showEviction = storageEvicted && !evictionDismissed;
  if (!showUpdate && !showEviction) return null;

  return (
    <div className="pv-toasts">
      {showUpdate ? (
        <div
          className="pv-toast pv-toast--info"
          role="alertdialog"
          aria-labelledby="pv-update-title"
          tabIndex={-1}
          ref={promptRef}
          data-testid="update-prompt"
        >
          <p className="pv-toast__title" id="pv-update-title">
            {t('update.available')}
          </p>
          <p className="pv-toast__body">{t('update.body')}</p>
          <div className="pv-toast__actions">
            <Button
              tone="primary"
              onClick={() => {
                const update = updateRef.current;
                if (update) void update(true);
                else globalThis.location.reload();
              }}
            >
              {t('update.action')}
            </Button>
            <Button tone="quiet" onClick={() => setDismissed(true)}>
              {t('update.later')}
            </Button>
          </div>
        </div>
      ) : null}

      {showEviction ? (
        <div className="pv-toast pv-toast--warning" role="status" data-testid="eviction-notice">
          <p className="pv-toast__body">{t('update.storageEvicted')}</p>
          <div className="pv-toast__actions">
            <Button tone="quiet" onClick={() => setEvictionDismissed(true)}>
              {t('app.dismiss')}
            </Button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
