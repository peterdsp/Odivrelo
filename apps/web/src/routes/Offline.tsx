import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { OfflinePackDescriptor } from '../data/contract';
import { useI18n } from '../i18n/I18nProvider';
import type { MessageKey } from '../i18n/catalogues';
import { useHead } from '../hooks/useHead';
import { useData, useDataMode } from '../app/DataProvider';
import { useOnline } from '../hooks/useOnline';
import { Badge, Button, Card, Disclosure, Fact, FactList, ProgressBar, Section } from '../components/primitives';
import { DataError, Loading, StateBlock } from '../components/states';
import { useAnnouncer } from '../components/Announcer';
import { formatBytes } from '../lib/digest';
import { measureStorage, requestPersistentStorage, type StorageUsage } from '../lib/db';
import {
  buildPackViews,
  downloadPack,
  PackDownloadError,
  removePack,
  rollbackToPrevious,
  type DownloadEvent,
  type PackView,
} from '../data/packManager';
import { offlineGroupsFor, serviceDatesOf } from '../features/offlineGroups';

/**
 * Offline data management.
 *
 * Every state in the brief is reachable here and none of them is faked: not
 * installed, downloading with real per-file progress, cancelled, interrupted and
 * resumable, an integrity failure that names the file and confirms nothing was
 * installed, storage full, offline, installed, update available, rollback to the
 * kept previous release, and delete.
 *
 * It also states what is *not* in a pack. Poravia ships no map imagery, so the
 * page says the map will draw the route on a plain background rather than letting
 * "offline maps" be inferred.
 */
export function Offline() {
  const { t, language, formatDateTime } = useI18n();
  const { source, manifest, reload, refreshInstalled, storageAvailable } = useData();
  const dataMode = useDataMode();
  const online = useOnline();
  const { announce, alert } = useAnnouncer();

  const [views, setViews] = useState<PackView[] | null>(null);
  const [progress, setProgress] = useState<Record<string, DownloadEvent>>({});
  const [errors, setErrors] = useState<Record<string, { title: string; body: string }>>({});
  const [usage, setUsage] = useState<StorageUsage | null>(null);
  const [persistResult, setPersistResult] = useState<boolean | null>(null);
  const controllers = useRef(new Map<string, AbortController>());
  // Which packs are downloading is rendered, so it has to be state. The
  // controllers themselves stay in a ref because aborting is not a render input.
  const [downloading, setDownloading] = useState<ReadonlySet<string>>(new Set());

  useHead({ title: t('meta.offline.title'), description: t('offline.intro'), path: '/offline', language, dataMode });

  const manifestValue = manifest.status === 'ready' ? manifest.value : null;
  const groups = useMemo<OfflinePackDescriptor[]>(
    () => (manifestValue ? offlineGroupsFor(manifestValue) : []),
    [manifestValue],
  );
  const serviceDates = useMemo(() => (manifestValue ? serviceDatesOf(manifestValue) : []), [manifestValue]);

  const refresh = useCallback(async () => {
    if (!manifestValue || !storageAvailable) {
      setViews([]);
      return;
    }
    setViews(await buildPackViews(manifestValue, groups));
    setUsage(await measureStorage());
    refreshInstalled();
  }, [manifestValue, groups, storageAvailable, refreshInstalled]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const start = async (view: PackView) => {
    if (!manifestValue) return;
    const controller = new AbortController();
    controllers.current.set(view.descriptor.id, controller);
    setDownloading((current) => new Set(current).add(view.descriptor.id));
    setErrors((current) => {
      const next = { ...current };
      delete next[view.descriptor.id];
      return next;
    });
    try {
      await downloadPack({
        source,
        manifest: manifestValue,
        descriptor: view.descriptor,
        signal: controller.signal,
        onProgress: (event) => setProgress((current) => ({ ...current, [event.packId]: event })),
      });
      announce(`${t(view.descriptor.nameKey as MessageKey)}: ${t('offline.installed')}`);
    } catch (error) {
      if (error instanceof PackDownloadError) {
        const failure = error.failure;
        if (failure.kind === 'aborted') {
          announce(t('offline.cancelled'));
        } else {
          const copy =
            failure.kind === 'integrity'
              ? { title: t('offline.integrityFailed'), body: t('offline.integrityBody', { message: failure.message }) }
              : failure.kind === 'storage_full'
                ? { title: t('offline.storageFull'), body: t('offline.storageFullBody') }
                : failure.kind === 'offline'
                  ? { title: t('state.offlineTitle'), body: t('offline.offlineBody') }
                  : { title: t('offline.failed'), body: failure.message };
          setErrors((current) => ({ ...current, [view.descriptor.id]: copy }));
          alert(`${copy.title}. ${copy.body}`);
        }
      } else {
        const copy = { title: t('offline.failed'), body: (error as Error).message };
        setErrors((current) => ({ ...current, [view.descriptor.id]: copy }));
        alert(copy.title);
      }
    } finally {
      controllers.current.delete(view.descriptor.id);
      setDownloading((current) => {
        const next = new Set(current);
        next.delete(view.descriptor.id);
        return next;
      });
      setProgress((current) => {
        const next = { ...current };
        delete next[view.descriptor.id];
        return next;
      });
      await refresh();
    }
  };

  if (manifest.status === 'loading') return <Loading />;
  if (manifest.status === 'error') {
    return (
      <div className="pv-page pv-page--narrow">
        <h1 className="pv-page__title">{t('offline.title')}</h1>
        <DataError error={manifest.error} onRetry={reload} />
      </div>
    );
  }

  return (
    <div className="pv-page pv-page--narrow">
      <h1 className="pv-page__title">{t('offline.title')}</h1>
      <p className="pv-page__lede">{t('offline.intro')}</p>

      {manifestValue ? (
        <p className="pv-muted">
          {t('offline.release', {
            releaseId: manifestValue.releaseId,
            when: formatDateTime(manifestValue.publishedAt),
          })}
        </p>
      ) : null}

      <Card tone="muted">
        <p>{t('offline.tilesNotice')}</p>
      </Card>

      {!storageAvailable ? (
        <StateBlock
          kind="unavailable"
          title={t('offline.storageUnavailable')}
          body={<p>{t('offline.storageUnavailableBody')}</p>}
        />
      ) : null}

      {!online ? (
        <StateBlock kind="offline" title={t('state.offlineTitle')} body={<p>{t('offline.offlineBody')}</p>} announce={false} />
      ) : null}

      {views === null ? <Loading /> : null}

      {views?.map((view) => {
        const live = progress[view.descriptor.id];
        const failure = errors[view.descriptor.id];
        const isDownloading = downloading.has(view.descriptor.id) || live !== undefined;
        const resumable = view.progress !== null && view.status !== 'installed';
        return (
          <Section
            key={view.descriptor.id}
            title={t(view.descriptor.nameKey as MessageKey)}
            level={2}
            id={`pack-${view.descriptor.id}`}
            description={t(view.descriptor.descriptionKey as MessageKey)}
          >
            <Card>
              <p className="pv-pack__badges">
                {view.descriptor.required ? <Badge tone="info" icon="alert">{t('offline.required')}</Badge> : null}
                {view.status === 'installed' ? (
                  <Badge tone="success" icon="check">
                    {t('offline.installed')}
                  </Badge>
                ) : view.status === 'update_available' ? (
                  <Badge tone="warning" icon="download">
                    {t('offline.updateAvailable')}
                  </Badge>
                ) : (
                  <Badge tone="neutral" icon="download">
                    {t('offline.notInstalled')}
                  </Badge>
                )}
                <Badge tone="neutral">{formatBytes(view.descriptor.bytes, language)}</Badge>
              </p>

              <FactList>
                <Fact label={t('offline.whatsIncluded')}>
                  {t('offline.packNames', { count: view.descriptor.packNames.length })}
                </Fact>
                {view.installed ? (
                  <Fact label={t('offline.installed')}>
                    {t('offline.installedAt', { when: formatDateTime(view.installed.installedAt) })}
                  </Fact>
                ) : null}
                <Fact label={t('offline.availableOffline')}>
                  {view.status === 'installed' ? t('app.yes') : t('app.no')}
                </Fact>
                <Fact label={t('tripReady.mapTiles')}>{t('tripReady.mapTilesNever')}</Fact>
              </FactList>

              {live ? (
                <ProgressBar
                  value={live.bytesDone}
                  max={live.bytesTotal}
                  label={t('offline.progressLabel', { name: t(view.descriptor.nameKey as MessageKey) })}
                  text={t('offline.progress', {
                    done: live.filesDone,
                    total: live.filesTotal,
                    bytesDone: formatBytes(live.bytesDone, language),
                    bytesTotal: formatBytes(live.bytesTotal, language),
                  })}
                />
              ) : null}

              {resumable && !isDownloading ? <p className="pv-muted">{t('offline.interrupted')}</p> : null}

              {failure ? (
                <StateBlock
                  kind={failure.title === t('offline.storageFull') ? 'storage_full' : 'integrity'}
                  headingLevel={3}
                  title={failure.title}
                  body={<p>{failure.body}</p>}
                  announce={false}
                />
              ) : null}

              <div className="pv-pack__actions">
                {isDownloading ? (
                  <Button
                    tone="secondary"
                    onClick={() => {
                      controllers.current.get(view.descriptor.id)?.abort();
                    }}
                  >
                    {t('offline.cancel')}
                  </Button>
                ) : !storageAvailable ? (
                  <Button tone="primary" unavailableReason={t('offline.storageUnavailableBody')}>
                    {t('offline.download')}
                  </Button>
                ) : !online && view.status !== 'installed' ? (
                  <Button tone="primary" unavailableReason={t('offline.offlineBody')}>
                    {t('offline.download')}
                  </Button>
                ) : view.status === 'installed' ? null : (
                  <Button tone="primary" onClick={() => void start(view)}>
                    {failure
                      ? t('offline.retry')
                      : resumable
                        ? t('offline.resume')
                        : view.status === 'update_available'
                          ? t('offline.update')
                          : t('offline.download')}
                  </Button>
                )}

                {view.installed ? (
                  <Button
                    tone="danger"
                    onClick={async () => {
                      await removePack(view.installed!);
                      announce(t('offline.deleted'));
                      await refresh();
                    }}
                  >
                    {t('offline.delete')}
                  </Button>
                ) : null}

                {view.installed && view.previous ? (
                  <Button
                    tone="secondary"
                    onClick={async () => {
                      await rollbackToPrevious(view.installed!);
                      announce(t('offline.rolledBack'));
                      await refresh();
                    }}
                  >
                    {t('offline.rollback')}
                  </Button>
                ) : null}
              </div>

              {view.previous ? <p className="pv-muted">{t('offline.previousKept')}</p> : null}

              <Disclosure summary={t('offline.whatsIncluded')}>
                <ul className="pv-list pv-list--plain pv-mono">
                  {view.descriptor.packNames.map((packName) => (
                    <li key={packName}>{packName}</li>
                  ))}
                </ul>
              </Disclosure>
            </Card>
          </Section>
        );
      })}

      {serviceDates.length > 0 ? (
        <Section title={t('coverage.dateRange', { from: serviceDates[0] ?? '', to: serviceDates.at(-1) ?? '' })} level={2}>
          <p className="pv-muted">{t('offline.packNames', { count: serviceDates.length })}</p>
        </Section>
      ) : null}

      <Section title={t('settings.storage')} level={2}>
        {usage ? (
          <FactList>
            <Fact label={t('settings.storagePacks')}>{formatBytes(usage.packBytes, language)}</Fact>
            <Fact label={t('settings.storageWallet')}>{formatBytes(usage.walletBytes, language)}</Fact>
            <Fact label={t('settings.storage')}>
              {usage.quotaBytes !== null && usage.usageBytes !== null
                ? t('settings.storageOf', {
                    used: formatBytes(usage.usageBytes, language),
                    quota: formatBytes(usage.quotaBytes, language),
                  })
                : t('settings.storageUnknown')}
            </Fact>
          </FactList>
        ) : null}
        <p className="pv-muted">{t('offline.evictionWarning')}</p>
        <Button
          tone="secondary"
          onClick={async () => {
            const granted = await requestPersistentStorage();
            setPersistResult(granted);
            announce(granted ? t('offline.persistGranted') : t('offline.persistDenied'));
          }}
        >
          {t('offline.requestPersist')}
        </Button>
        {persistResult !== null ? (
          <p className={persistResult ? 'pv-muted' : 'pv-field__error'}>
            {persistResult ? t('offline.persistGranted') : t('offline.persistDenied')}
          </p>
        ) : null}
      </Section>
    </div>
  );
}
