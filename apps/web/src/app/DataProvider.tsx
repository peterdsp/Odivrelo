import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import type { Manifest, Meta } from '../data/contract';
import type { PublicDataSource } from '../data/PublicDataSource';
import { StaticPackSource } from '../data/StaticPackSource';
import { HttpApiSource } from '../data/HttpApiSource';
import { installedPackReader } from '../data/packManager';
import { appConfig } from '../config';
import { useI18n } from '../i18n/I18nProvider';
import { useAsync, type AsyncState } from '../hooks/useAsync';
import { indexedDbAvailable, listInstalledPacks, readMeta, writeMeta } from '../lib/db';

/**
 * Chooses the data source and holds the release metadata the whole app depends
 * on: which release is being read, whether it is demonstration data, what
 * service dates exist, and what the coverage statement says.
 *
 * Only this module knows which implementation is in use. Everything above it
 * talks to the `PublicDataSource` interface, which is why flipping to a live API
 * is a configuration change and not a rewrite.
 */

export interface DataContextValue {
  readonly source: PublicDataSource;
  readonly meta: AsyncState<Meta>;
  readonly manifest: AsyncState<Manifest>;
  readonly reload: () => void;
  /** True once at least one offline pack from this release is installed. */
  readonly hasInstalledPacks: boolean;
  readonly refreshInstalled: () => void;
  readonly storageAvailable: boolean;
  /**
   * Set when a release this device had stored has gone: either the browser
   * evicted it or the reader cleared site data. The UI says so rather than
   * silently behaving like a first visit.
   */
  readonly storageEvicted: boolean;
}

const DataContext = createContext<DataContextValue | null>(null);

export interface DataProviderProps {
  children: ReactNode;
  /** Tests inject a source; the app builds one from configuration. */
  source?: PublicDataSource;
}

export function DataProvider({ children, source: injected }: DataProviderProps) {
  const { language } = useI18n();
  const [installedCount, setInstalledCount] = useState(0);
  const [installedTick, setInstalledTick] = useState(0);
  const [storageEvicted, setStorageEvicted] = useState(false);
  const storageAvailable = indexedDbAvailable();

  const source = useMemo<PublicDataSource>(() => {
    if (injected) return injected;
    if (appConfig.dataSource === 'http' && appConfig.apiBaseUrl) {
      return new HttpApiSource({ baseUrl: appConfig.apiBaseUrl, language: () => language });
    }
    return new StaticPackSource({
      baseUrl: appConfig.staticDataBase,
      // Installed packs are preferred over the network, so a device that has
      // downloaded the release never needs a connection to use it.
      packReader: storageAvailable ? installedPackReader : null,
    });
    // `language` deliberately excluded: rebuilding the source on a language
    // change would throw away every cached pack. The HTTP source reads the
    // language through a callback instead.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [injected, storageAvailable]);

  const manifest = useAsync((signal) => source.manifest(signal), [source]);
  const meta = useAsync((signal) => source.meta(signal), [source]);

  useEffect(() => {
    let live = true;
    void (async () => {
      const packs = await listInstalledPacks();
      if (!live) return;
      setInstalledCount(packs.length);
      // If a previous visit recorded installed packs and none are present now,
      // the browser reclaimed the storage.
      const remembered = await readMeta<number>('installedPackCount', 0);
      if (!live) return;
      if (remembered > 0 && packs.length === 0) setStorageEvicted(true);
      else if (packs.length !== remembered) await writeMeta('installedPackCount', packs.length);
    })();
    return () => {
      live = false;
    };
  }, [installedTick]);

  const value = useMemo<DataContextValue>(
    () => ({
      source,
      meta: meta.state,
      manifest: manifest.state,
      reload: () => {
        if (source instanceof StaticPackSource) source.reset();
        manifest.reload();
        meta.reload();
      },
      hasInstalledPacks: installedCount > 0,
      refreshInstalled: () => setInstalledTick((n) => n + 1),
      storageAvailable,
      storageEvicted,
    }),
    [source, meta, manifest, installedCount, storageAvailable, storageEvicted],
  );

  return <DataContext.Provider value={value}>{children}</DataContext.Provider>;
}

export function useData(): DataContextValue {
  const context = useContext(DataContext);
  if (!context) throw new Error('useData was called outside a DataProvider.');
  return context;
}

/** The release metadata, or null while it is still loading or has failed. */
export function useMetaOrNull(): Meta | null {
  const { meta } = useData();
  return meta.status === 'ready' ? meta.value : null;
}

/** `demo` until the release says otherwise, so the notice errs towards being shown. */
export function useDataMode(): 'real' | 'demo' {
  const meta = useMetaOrNull();
  return meta?.dataMode ?? 'demo';
}
