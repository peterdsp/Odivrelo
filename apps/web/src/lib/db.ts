/**
 * Everything the app keeps on the device.
 *
 * Offline packs, saved trips, favourites and the travel wallet all live here in
 * IndexedDB. Nothing in this file ever leaves the device: there is no upload
 * path, wallet bytes are never handed to the service worker cache, and no value
 * stored here is ever put into a URL, a notification payload or a log line.
 *
 * Every entry point tolerates the database being unavailable. Browsers evict
 * storage, private windows refuse it, and a passenger can clear it at any
 * moment; when that happens the app degrades to online-only rather than
 * breaking.
 */
import { openDB, type DBSchema, type IDBPDatabase } from 'idb';
import type { JourneyDetail, Localized, Manifest, ManifestFile } from '../data/contract';
import { BRAND } from '../brand/brand';

export const DB_NAME = `${BRAND.slug}-store`;
export const DB_VERSION = 1;

export interface StoredPackFile {
  /** Manifest path, e.g. `packs/stops-3aefebdcb9654cfd.json`. Content-addressed, so it is a stable key. */
  path: string;
  releaseId: string;
  sha256: string;
  bytes: number;
  mediaType: string;
  data: ArrayBuffer;
  installedAt: string;
}

export interface InstalledPack {
  /** `${releaseId}:${packId}` */
  key: string;
  packId: string;
  releaseId: string;
  /** Message key for the group's name, so a stored pack renames with the language. */
  nameKey: string;
  filePaths: string[];
  bytes: number;
  installedAt: string;
}

export interface DownloadProgress {
  key: string;
  packId: string;
  releaseId: string;
  /** Manifest paths already verified and staged. A resumed download skips these. */
  completedPaths: string[];
  totalPaths: number;
  bytesDone: number;
  bytesTotal: number;
  startedAt: string;
  updatedAt: string;
  state: 'running' | 'paused' | 'failed';
  lastError: string | null;
}

export interface SavedTrip {
  id: string;
  journeyId: string;
  serviceDate: string;
  releaseId: string;
  /** Snapshot of the journey exactly as it was when saved, so it works with no network. */
  detail: JourneyDetail;
  savedAt: string;
  /** Minutes before departure the passenger asked to be reminded, or null. */
  reminderMinutes: number | null;
  reminderScheduledFor: string | null;
  walletItemIds: string[];
}

export type FavoriteKind = 'place' | 'operator';

export interface Favorite {
  id: string;
  kind: FavoriteKind;
  ref: string;
  label: Localized;
  savedAt: string;
}

export interface WalletItem {
  id: string;
  /** The file name the passenger's own file had. Never sent anywhere. */
  fileName: string;
  /** The type sniffed from the bytes, not the one the picker claimed. */
  mediaType: string;
  bytes: number;
  /*
   * Raw bytes rather than a Blob. Blob support in IndexedDB is uneven across
   * browsers, and a Blob that survives storage on one engine and not another is
   * exactly the kind of failure a passenger would meet at a ticket barrier. An
   * ArrayBuffer is stored identically everywhere, and the viewer builds a Blob
   * from it when it renders.
   */
  data: ArrayBuffer;
  importedAt: string;
  /** Free-text label the passenger typed, if any. */
  label: string;
}

export interface MetaRecord {
  key: string;
  value: unknown;
}

interface OdivreloSchema extends DBSchema {
  packFiles: { key: string; value: StoredPackFile; indexes: { byRelease: string } };
  installedPacks: { key: string; value: InstalledPack; indexes: { byRelease: string } };
  downloads: { key: string; value: DownloadProgress };
  savedTrips: { key: string; value: SavedTrip; indexes: { byServiceDate: string } };
  favorites: { key: string; value: Favorite };
  wallet: { key: string; value: WalletItem };
  meta: { key: string; value: MetaRecord };
}

export type OdivreloDB = IDBPDatabase<OdivreloSchema>;

let dbPromise: Promise<OdivreloDB | null> | null = null;

export function indexedDbAvailable(): boolean {
  try {
    return typeof globalThis.indexedDB !== 'undefined' && globalThis.indexedDB !== null;
  } catch {
    return false;
  }
}

export function getDb(): Promise<OdivreloDB | null> {
  if (!dbPromise) {
    dbPromise = (async () => {
      if (!indexedDbAvailable()) return null;
      try {
        return await openDB<OdivreloSchema>(DB_NAME, DB_VERSION, {
          upgrade(db) {
            if (!db.objectStoreNames.contains('packFiles')) {
              const store = db.createObjectStore('packFiles', { keyPath: 'path' });
              store.createIndex('byRelease', 'releaseId');
            }
            if (!db.objectStoreNames.contains('installedPacks')) {
              const store = db.createObjectStore('installedPacks', { keyPath: 'key' });
              store.createIndex('byRelease', 'releaseId');
            }
            if (!db.objectStoreNames.contains('downloads')) db.createObjectStore('downloads', { keyPath: 'key' });
            if (!db.objectStoreNames.contains('savedTrips')) {
              const store = db.createObjectStore('savedTrips', { keyPath: 'id' });
              store.createIndex('byServiceDate', 'serviceDate');
            }
            if (!db.objectStoreNames.contains('favorites')) db.createObjectStore('favorites', { keyPath: 'id' });
            if (!db.objectStoreNames.contains('wallet')) db.createObjectStore('wallet', { keyPath: 'id' });
            if (!db.objectStoreNames.contains('meta')) db.createObjectStore('meta', { keyPath: 'key' });
          },
          blocked() {
            // Another tab holds an older version open. The caller sees null and
            // stays online-only rather than hanging.
          },
        });
      } catch {
        return null;
      }
    })();
  }
  return dbPromise;
}

/** Only for tests, which open a fresh fake database per case. */
export function resetDbForTests(): void {
  dbPromise = null;
}

export class StorageFullError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'StorageFullError';
  }
}

export function isQuotaError(error: unknown): boolean {
  const name = (error as { name?: string } | null)?.name;
  return name === 'QuotaExceededError' || name === 'NS_ERROR_DOM_QUOTA_REACHED';
}

// ---------------------------------------------------------------------------
// Pack storage
// ---------------------------------------------------------------------------

export function packKey(releaseId: string, packId: string): string {
  return `${releaseId}:${packId}`;
}

export async function readPackFile(path: string): Promise<StoredPackFile | null> {
  const db = await getDb();
  if (!db) return null;
  try {
    return (await db.get('packFiles', path)) ?? null;
  } catch {
    return null;
  }
}

/**
 * Writes one verified file to the staging area.
 *
 * A file lands here the moment its SHA-256 matches, before the pack as a whole is
 * installed. That is what makes a resumed download genuinely skip work rather than
 * merely claiming to. It is safe because pack names are content-addressed: a file
 * present under its digest name is, by construction, the right bytes, so a reader
 * that finds one can always use it.
 *
 * A staged file is not an installed pack. Only an `installedPacks` record makes a
 * pack count as held, so a half-finished download never looks complete.
 */
export async function stagePackFile(file: Omit<StoredPackFile, 'installedAt'>): Promise<void> {
  const db = await getDb();
  if (!db) throw new Error('This browser is not offering storage, so packs cannot be downloaded.');
  try {
    await db.put('packFiles', { ...file, installedAt: new Date().toISOString() });
  } catch (error) {
    if (isQuotaError(error)) {
      throw new StorageFullError('There is not enough room left in browser storage for this pack.');
    }
    throw error;
  }
}

/**
 * Deletes staged files that no installed pack needs.
 *
 * Called when a download is cancelled or abandoned, so an interrupted attempt
 * cannot leave bytes on the device that nothing will ever claim.
 */
export async function discardStagedFiles(paths: readonly string[]): Promise<void> {
  const db = await getDb();
  if (!db || paths.length === 0) return;
  try {
    const tx = db.transaction(['packFiles', 'installedPacks'], 'readwrite');
    const packs = await tx.objectStore('installedPacks').getAll();
    const needed = new Set(packs.flatMap((pack) => pack.filePaths));
    const files = tx.objectStore('packFiles');
    for (const path of paths) {
      if (!needed.has(path)) await files.delete(path);
    }
    await tx.done;
  } catch {
    // Leaving a verified, content-addressed file behind costs space, not
    // correctness, and the storage screen can still clear it.
  }
}

/**
 * Records the installed pack, and writes any files that are not already staged,
 * in a single transaction. A pack is either wholly present or wholly absent:
 * there is no state in which the app believes it holds data it cannot read.
 */
export async function installPackAtomically(
  pack: Omit<InstalledPack, 'installedAt'>,
  files: readonly Omit<StoredPackFile, 'installedAt'>[],
): Promise<void> {
  const db = await getDb();
  if (!db) throw new Error('This browser is not offering storage, so packs cannot be installed.');
  const installedAt = new Date().toISOString();
  try {
    const tx = db.transaction(['packFiles', 'installedPacks', 'downloads'], 'readwrite');
    const packFiles = tx.objectStore('packFiles');
    for (const file of files) await packFiles.put({ ...file, installedAt });
    await tx.objectStore('installedPacks').put({ ...pack, installedAt });
    await tx.objectStore('downloads').delete(pack.key);
    await tx.done;
  } catch (error) {
    if (isQuotaError(error)) {
      throw new StorageFullError('There is not enough room left in browser storage for this pack.');
    }
    throw error;
  }
}

export async function listInstalledPacks(): Promise<InstalledPack[]> {
  const db = await getDb();
  if (!db) return [];
  try {
    return await db.getAll('installedPacks');
  } catch {
    return [];
  }
}

/** Removes a pack, and the files it holds that no other installed pack still needs. */
export async function deleteInstalledPack(key: string): Promise<void> {
  const db = await getDb();
  if (!db) return;
  const tx = db.transaction(['packFiles', 'installedPacks', 'downloads'], 'readwrite');
  const packsStore = tx.objectStore('installedPacks');
  const target = await packsStore.get(key);
  if (!target) {
    await tx.done;
    return;
  }
  const others = (await packsStore.getAll()).filter((p) => p.key !== key);
  const stillNeeded = new Set(others.flatMap((p) => p.filePaths));
  const files = tx.objectStore('packFiles');
  for (const path of target.filePaths) {
    if (!stillNeeded.has(path)) await files.delete(path);
  }
  await packsStore.delete(key);
  await tx.objectStore('downloads').delete(key);
  await tx.done;
}

export async function saveDownloadProgress(progress: DownloadProgress): Promise<void> {
  const db = await getDb();
  if (!db) return;
  try {
    await db.put('downloads', progress);
  } catch {
    // Losing resume state costs a restart, not correctness.
  }
}

export async function readDownloadProgress(key: string): Promise<DownloadProgress | null> {
  const db = await getDb();
  if (!db) return null;
  try {
    return (await db.get('downloads', key)) ?? null;
  } catch {
    return null;
  }
}

export async function listDownloadProgress(): Promise<DownloadProgress[]> {
  const db = await getDb();
  if (!db) return [];
  try {
    return await db.getAll('downloads');
  } catch {
    return [];
  }
}

export async function clearDownloadProgress(key: string): Promise<void> {
  const db = await getDb();
  if (!db) return;
  try {
    await db.delete('downloads', key);
  } catch {
    // Same as above.
  }
}

// ---------------------------------------------------------------------------
// Saved trips, favourites, wallet
// ---------------------------------------------------------------------------

export async function listSavedTrips(): Promise<SavedTrip[]> {
  const db = await getDb();
  if (!db) return [];
  try {
    const trips = await db.getAll('savedTrips');
    return trips.sort((a, b) => a.serviceDate.localeCompare(b.serviceDate) || a.savedAt.localeCompare(b.savedAt));
  } catch {
    return [];
  }
}

export async function putSavedTrip(trip: SavedTrip): Promise<void> {
  const db = await getDb();
  if (!db) throw new Error('This browser is not offering storage, so trips cannot be saved.');
  try {
    await db.put('savedTrips', trip);
  } catch (error) {
    if (isQuotaError(error)) throw new StorageFullError('There is not enough room left in browser storage for this trip.');
    throw error;
  }
}

export async function deleteSavedTrip(id: string): Promise<void> {
  const db = await getDb();
  if (!db) return;
  try {
    await db.delete('savedTrips', id);
  } catch {
    // The trip is already gone as far as the passenger is concerned.
  }
}

export async function listFavorites(): Promise<Favorite[]> {
  const db = await getDb();
  if (!db) return [];
  try {
    return await db.getAll('favorites');
  } catch {
    return [];
  }
}

export async function putFavorite(favorite: Favorite): Promise<void> {
  const db = await getDb();
  if (!db) throw new Error('This browser is not offering storage, so favourites cannot be saved.');
  await db.put('favorites', favorite);
}

export async function deleteFavorite(id: string): Promise<void> {
  const db = await getDb();
  if (!db) return;
  try {
    await db.delete('favorites', id);
  } catch {
    // Already absent.
  }
}

export async function listWalletItems(): Promise<WalletItem[]> {
  const db = await getDb();
  if (!db) return [];
  try {
    const items = await db.getAll('wallet');
    return items.sort((a, b) => b.importedAt.localeCompare(a.importedAt));
  } catch {
    return [];
  }
}

export async function putWalletItem(item: WalletItem): Promise<void> {
  const db = await getDb();
  if (!db) throw new Error('This browser is not offering storage, so ticket files cannot be kept.');
  try {
    await db.put('wallet', item);
  } catch (error) {
    if (isQuotaError(error)) throw new StorageFullError('There is not enough room left in browser storage for this file.');
    throw error;
  }
}

/** Really deletes. There is no copy elsewhere, because a copy was never made. */
export async function deleteWalletItem(id: string): Promise<void> {
  const db = await getDb();
  if (!db) return;
  const tx = db.transaction(['wallet', 'savedTrips'], 'readwrite');
  await tx.objectStore('wallet').delete(id);
  const trips = tx.objectStore('savedTrips');
  for (const trip of await trips.getAll()) {
    if (trip.walletItemIds.includes(id)) {
      await trips.put({ ...trip, walletItemIds: trip.walletItemIds.filter((w) => w !== id) });
    }
  }
  await tx.done;
}

// ---------------------------------------------------------------------------
// Meta
// ---------------------------------------------------------------------------

export async function readMeta<T>(key: string, fallback: T): Promise<T> {
  const db = await getDb();
  if (!db) return fallback;
  try {
    const record = await db.get('meta', key);
    return record ? (record.value as T) : fallback;
  } catch {
    return fallback;
  }
}

export async function writeMeta(key: string, value: unknown): Promise<void> {
  const db = await getDb();
  if (!db) return;
  try {
    await db.put('meta', { key, value });
  } catch {
    // Non-critical bookkeeping.
  }
}

// ---------------------------------------------------------------------------
// Whole-store operations
// ---------------------------------------------------------------------------

export interface StorageUsage {
  readonly packBytes: number;
  readonly walletBytes: number;
  readonly tripCount: number;
  readonly walletCount: number;
  readonly packCount: number;
  readonly quotaBytes: number | null;
  readonly usageBytes: number | null;
  readonly persisted: boolean;
}

export async function measureStorage(): Promise<StorageUsage> {
  const db = await getDb();
  let packBytes = 0;
  let walletBytes = 0;
  let tripCount = 0;
  let walletCount = 0;
  let packCount = 0;
  if (db) {
    try {
      for (const file of await db.getAll('packFiles')) packBytes += file.bytes;
      for (const item of await db.getAll('wallet')) {
        walletBytes += item.bytes;
        walletCount += 1;
      }
      tripCount = await db.count('savedTrips');
      packCount = await db.count('installedPacks');
    } catch {
      // Report what could be measured rather than nothing.
    }
  }
  let quotaBytes: number | null = null;
  let usageBytes: number | null = null;
  let persisted = false;
  try {
    const estimate = await navigator.storage?.estimate?.();
    quotaBytes = estimate?.quota ?? null;
    usageBytes = estimate?.usage ?? null;
    persisted = (await navigator.storage?.persisted?.()) ?? false;
  } catch {
    // Storage estimation is optional and is absent in several browsers.
  }
  return { packBytes, walletBytes, tripCount, walletCount, packCount, quotaBytes, usageBytes, persisted };
}

export async function clearOfflineData(): Promise<void> {
  const db = await getDb();
  if (!db) return;
  const tx = db.transaction(['packFiles', 'installedPacks', 'downloads'], 'readwrite');
  await tx.objectStore('packFiles').clear();
  await tx.objectStore('installedPacks').clear();
  await tx.objectStore('downloads').clear();
  await tx.done;
}

export async function clearEverything(): Promise<void> {
  const db = await getDb();
  if (!db) return;
  const names = ['packFiles', 'installedPacks', 'downloads', 'savedTrips', 'favorites', 'wallet', 'meta'] as const;
  const tx = db.transaction(names, 'readwrite');
  for (const name of names) await tx.objectStore(name).clear();
  await tx.done;
}

/** Ask the browser to keep this origin's storage rather than evicting it under pressure. */
export async function requestPersistentStorage(): Promise<boolean> {
  try {
    return (await navigator.storage?.persist?.()) ?? false;
  } catch {
    return false;
  }
}

export function manifestFileFor(manifest: Manifest, packName: string): ManifestFile | null {
  return manifest.files[packName] ?? null;
}

const INSTALLED_MANIFEST_KEY = 'installedManifest';

/**
 * Keeps the manifest a pack was installed from.
 *
 * A device holding packs must also hold the manifest that names them, or it
 * cannot use what it downloaded: pack file names are content-addressed, so
 * without the manifest there is no way to know which file answers which
 * question. Storing it here is what makes an installed release genuinely usable
 * with no connection at all, rather than only until the next navigation.
 */
export async function rememberManifest(manifest: Manifest): Promise<void> {
  await writeMeta(INSTALLED_MANIFEST_KEY, manifest);
}

export async function readRememberedManifest(): Promise<Manifest | null> {
  return readMeta<Manifest | null>(INSTALLED_MANIFEST_KEY, null);
}

export async function forgetManifest(): Promise<void> {
  await writeMeta(INSTALLED_MANIFEST_KEY, null);
}
