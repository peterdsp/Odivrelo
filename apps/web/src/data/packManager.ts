/**
 * Downloads, verifies, installs, updates, rolls back and deletes offline packs.
 *
 * Rules this implements, all of them observable in the UI:
 *  - every file is checked against the SHA-256 the manifest names before it is
 *    written, and a file that fails is discarded rather than installed;
 *  - installation is one IndexedDB transaction, so a pack is never half present;
 *  - an interrupted download resumes from the files it had already verified;
 *  - cancelling stops immediately and leaves nothing installed;
 *  - installing a newer release keeps the previous one until the new one is
 *    complete, so rollback is always possible;
 *  - what is stored is data only. No map tiles are downloaded, because none are
 *    shipped, and the UI says so rather than implying otherwise.
 */
import type { Manifest, ManifestFile, OfflinePackDescriptor } from './contract';
import type { PublicDataSource } from './PublicDataSource';
import { digestsMatch, sha256Hex } from '../lib/digest';
import {
  clearDownloadProgress,
  deleteInstalledPack,
  discardStagedFiles,
  installPackAtomically,
  stagePackFile,
  listInstalledPacks,
  packKey,
  readDownloadProgress,
  readPackFile,
  rememberManifest,
  saveDownloadProgress,
  StorageFullError,
  manifestFileFor,
  type DownloadProgress,
  type InstalledPack,
  readRememberedManifest,
  type StoredPackFile,
} from '../lib/db';

export type PackStatus =
  | 'not_installed'
  | 'installed'
  | 'update_available'
  | 'downloading'
  | 'interrupted'
  | 'failed';

export interface PackView {
  readonly descriptor: OfflinePackDescriptor;
  readonly status: PackStatus;
  readonly installed: InstalledPack | null;
  /** An installed copy of an older release, kept so rollback is possible. */
  readonly previous: InstalledPack | null;
  readonly progress: DownloadProgress | null;
  readonly fileCount: number;
}

export interface DownloadEvent {
  readonly packId: string;
  readonly filesDone: number;
  readonly filesTotal: number;
  readonly bytesDone: number;
  readonly bytesTotal: number;
}

export type DownloadFailure =
  | { kind: 'aborted' }
  | { kind: 'offline'; message: string }
  | { kind: 'integrity'; message: string; path: string }
  | { kind: 'storage_full'; message: string }
  | { kind: 'unavailable'; message: string };

export class PackDownloadError extends Error {
  readonly failure: DownloadFailure;
  constructor(failure: DownloadFailure, message: string) {
    super(message);
    this.name = 'PackDownloadError';
    this.failure = failure;
  }
}

export function resolveFiles(manifest: Manifest, descriptor: OfflinePackDescriptor): ManifestFile[] {
  const files: ManifestFile[] = [];
  for (const packName of descriptor.packNames) {
    const file = manifestFileFor(manifest, packName);
    if (file) files.push(file);
  }
  return files;
}

export async function buildPackViews(manifest: Manifest, descriptors: readonly OfflinePackDescriptor[]): Promise<PackView[]> {
  const installed = await listInstalledPacks();
  const views: PackView[] = [];
  for (const descriptor of descriptors) {
    const key = packKey(manifest.releaseId, descriptor.id);
    const current = installed.find((p) => p.key === key) ?? null;
    const older = installed
      .filter((p) => p.packId === descriptor.id && p.releaseId !== manifest.releaseId)
      .sort((a, b) => b.installedAt.localeCompare(a.installedAt))[0] ?? null;
    const progress = await readDownloadProgress(key);
    let status: PackStatus = 'not_installed';
    if (current) status = 'installed';
    else if (older) status = 'update_available';
    if (progress?.state === 'running') status = 'downloading';
    else if (progress?.state === 'paused') status = 'interrupted';
    else if (progress?.state === 'failed') status = 'failed';
    views.push({
      descriptor,
      status,
      installed: current,
      previous: older,
      progress,
      fileCount: resolveFiles(manifest, descriptor).length,
    });
  }
  return views;
}

export interface DownloadOptions {
  readonly source: PublicDataSource;
  readonly manifest: Manifest;
  readonly descriptor: OfflinePackDescriptor;
  readonly signal: AbortSignal;
  readonly onProgress?: (event: DownloadEvent) => void;
  readonly fetchImpl?: typeof fetch;
}

/**
 * Fetches and verifies every file the pack names, then installs them in one
 * transaction. Files already verified by an earlier attempt are reused, so a
 * resumed download does not re-fetch what it already holds.
 */
export async function downloadPack(options: DownloadOptions): Promise<InstalledPack> {
  const { source, manifest, descriptor, signal, onProgress } = options;
  const fetchImpl = options.fetchImpl ?? ((...args: Parameters<typeof fetch>) => globalThis.fetch(...args));
  const key = packKey(manifest.releaseId, descriptor.id);
  const files = resolveFiles(manifest, descriptor);
  const bytesTotal = files.reduce((n, f) => n + f.bytes, 0);

  const prior = await readDownloadProgress(key);
  const completed = new Set(prior?.releaseId === manifest.releaseId ? prior.completedPaths : []);
  const staged: Omit<StoredPackFile, 'installedAt'>[] = [];
  let bytesDone = 0;

  // Reuse anything an earlier attempt already verified and staged.
  for (const file of files) {
    if (!completed.has(file.path)) continue;
    const existing = await readPackFile(file.path);
    if (existing && existing.sha256 === file.sha256 && existing.bytes === file.bytes) {
      staged.push({
        path: file.path,
        releaseId: manifest.releaseId,
        sha256: file.sha256,
        bytes: file.bytes,
        mediaType: file.mediaType,
        data: existing.data,
      });
      bytesDone += file.bytes;
    } else {
      completed.delete(file.path);
    }
  }

  const startedAt = prior?.startedAt ?? new Date().toISOString();
  const persist = async (state: DownloadProgress['state'], lastError: string | null) => {
    await saveDownloadProgress({
      key,
      packId: descriptor.id,
      releaseId: manifest.releaseId,
      completedPaths: [...completed],
      totalPaths: files.length,
      bytesDone,
      bytesTotal,
      startedAt,
      updatedAt: new Date().toISOString(),
      state,
      lastError,
    });
  };

  await persist('running', null);
  onProgress?.({ packId: descriptor.id, filesDone: staged.length, filesTotal: files.length, bytesDone, bytesTotal });

  for (const file of files) {
    if (signal.aborted) {
      await discardStagedFiles(staged.map((f) => f.path));
      await clearDownloadProgress(key);
      throw new PackDownloadError({ kind: 'aborted' }, 'The download was cancelled.');
    }
    if (completed.has(file.path)) continue;

    let bytes: ArrayBuffer;
    try {
      const response = await fetchImpl(source.packUrl(file.path), { cache: 'force-cache', signal });
      if (!response.ok) {
        await persist('failed', `HTTP ${response.status} for ${file.path}`);
        throw new PackDownloadError(
          { kind: 'unavailable', message: `${file.path} answered HTTP ${response.status}.` },
          `${file.path} answered HTTP ${response.status}.`,
        );
      }
      bytes = await response.arrayBuffer();
    } catch (cause) {
      if (cause instanceof PackDownloadError) throw cause;
      if ((cause as Error)?.name === 'AbortError' || signal.aborted) {
        await discardStagedFiles(staged.map((f) => f.path));
        await clearDownloadProgress(key);
        throw new PackDownloadError({ kind: 'aborted' }, 'The download was cancelled.');
      }
      await persist('paused', 'network');
      throw new PackDownloadError(
        { kind: 'offline', message: `${file.path} could not be fetched. The connection dropped.` },
        `${file.path} could not be fetched.`,
      );
    }

    if (bytes.byteLength !== file.bytes) {
      await persist('failed', `size mismatch for ${file.path}`);
      throw new PackDownloadError(
        {
          kind: 'integrity',
          path: file.path,
          message: `${file.path} arrived as ${bytes.byteLength} bytes but the release declares ${file.bytes}. It was discarded.`,
        },
        `${file.path} is the wrong size.`,
      );
    }
    const digest = await sha256Hex(bytes);
    if (!digestsMatch(digest, file.sha256)) {
      await persist('failed', `digest mismatch for ${file.path}`);
      throw new PackDownloadError(
        {
          kind: 'integrity',
          path: file.path,
          message: `${file.path} failed its SHA-256 check and was discarded. Nothing from this download was installed.`,
        },
        `${file.path} failed its integrity check.`,
      );
    }

    const verified = {
      path: file.path,
      releaseId: manifest.releaseId,
      sha256: file.sha256,
      bytes: file.bytes,
      mediaType: file.mediaType,
      data: bytes,
    };
    // Persist as soon as it verifies, so an interruption after this point does
    // not throw the work away.
    try {
      await stagePackFile(verified);
    } catch (error) {
      await persist('failed', 'storage');
      if (error instanceof StorageFullError) {
        throw new PackDownloadError({ kind: 'storage_full', message: error.message }, error.message);
      }
      throw new PackDownloadError({ kind: 'unavailable', message: (error as Error).message }, (error as Error).message);
    }
    staged.push(verified);
    completed.add(file.path);
    bytesDone += file.bytes;
    await persist('running', null);
    onProgress?.({ packId: descriptor.id, filesDone: staged.length, filesTotal: files.length, bytesDone, bytesTotal });
  }

  if (signal.aborted) {
    // Cancelling installs nothing and leaves nothing behind.
    await discardStagedFiles(staged.map((file) => file.path));
    await clearDownloadProgress(key);
    throw new PackDownloadError({ kind: 'aborted' }, 'The download was cancelled.');
  }

  const pack: Omit<InstalledPack, 'installedAt'> = {
    key,
    packId: descriptor.id,
    releaseId: manifest.releaseId,
    nameKey: descriptor.nameKey,
    filePaths: files.map((f) => f.path),
    bytes: bytesTotal,
  };
  try {
    await installPackAtomically(pack, staged);
  } catch (error) {
    await persist('failed', 'storage');
    if (error instanceof StorageFullError) {
      throw new PackDownloadError({ kind: 'storage_full', message: error.message }, error.message);
    }
    throw new PackDownloadError(
      { kind: 'unavailable', message: (error as Error).message },
      (error as Error).message,
    );
  }
  // The manifest goes with the packs: without it the content-addressed file
  // names mean nothing, so an installed release would be unusable offline.
  await rememberManifest(manifest);
  await clearDownloadProgress(key);
  return { ...pack, installedAt: new Date().toISOString() };
}

/** Removes an older release's copy of a pack once a newer one is installed. */
export async function discardPreviousRelease(previous: InstalledPack): Promise<void> {
  await deleteInstalledPack(previous.key);
}

/**
 * Rollback: drop the current release's copy and keep the previous one, which is
 * still fully installed and verified. No download is needed.
 */
export async function rollbackToPrevious(current: InstalledPack): Promise<void> {
  await deleteInstalledPack(current.key);
}

export async function removePack(pack: InstalledPack): Promise<void> {
  await deleteInstalledPack(pack.key);
}

/**
 * Feeds StaticPackSource from installed packs, so an installed release needs no
 * network at all: neither for the packs nor for the manifest that names them.
 */
export const installedPackReader = {
  async readPack(path: string): Promise<ArrayBuffer | null> {
    const stored = await readPackFile(path);
    return stored ? stored.data : null;
  },
  async readManifest(): Promise<Manifest | null> {
    return readRememberedManifest();
  },
};
