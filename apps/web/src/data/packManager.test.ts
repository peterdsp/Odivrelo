import { beforeEach, describe, expect, it, vi } from 'vitest';
import { StaticPackSource } from './StaticPackSource';
import { buildPackViews, downloadPack, PackDownloadError, removePack, rollbackToPrevious } from './packManager';
import { offlineGroupsFor } from '../features/offlineGroups';
import { clearEverything, listInstalledPacks, readDownloadProgress, readPackFile, measureStorage } from '../lib/db';
import { makeStaticFetch, readManifest, releaseIsPresent } from '../test/release';
import type { Manifest } from './contract';

/**
 * Offline packs, end to end against the real release.
 *
 * Every requirement in the brief is exercised here as behaviour rather than as an
 * intention: integrity verification and the rejection of a corrupted pack, an
 * atomic install that leaves nothing behind on failure, resume after an
 * interruption, cancellation, update to a newer release, rollback to the previous
 * one, and delete.
 */

const hasRelease = releaseIsPresent();
const describeRelease = hasRelease ? describe : describe.skip;

function source(fetchImpl: typeof fetch): StaticPackSource {
  return new StaticPackSource({ baseUrl: '/data/', fetchImpl });
}

function coreGroup(manifest: Manifest) {
  const group = offlineGroupsFor(manifest).find((g) => g.id === 'core');
  if (!group) throw new Error('The release has no core pack group.');
  return group;
}

describeRelease('offline pack download', () => {
  beforeEach(async () => {
    await clearEverything();
  });

  it('groups the release into downloadable packs with real byte counts', () => {
    const manifest = readManifest();
    const groups = offlineGroupsFor(manifest);
    expect(groups.length).toBeGreaterThan(0);
    const core = coreGroup(manifest);
    expect(core.required).toBe(true);
    // The size shown before a download is the sum of the manifest entries, so it
    // is the size that will actually be transferred.
    const expected = core.packNames.reduce((n, name) => n + (manifest.files[name]?.bytes ?? 0), 0);
    expect(core.bytes).toBe(expected);
    expect(core.bytes).toBeGreaterThan(0);
  });

  it('downloads, verifies and installs a pack, reporting progress as it goes', async () => {
    const manifest = readManifest();
    const descriptor = coreGroup(manifest);
    const events: number[] = [];
    const installed = await downloadPack({
      source: source(makeStaticFetch()),
      manifest,
      descriptor,
      signal: new AbortController().signal,
      onProgress: (event) => events.push(event.bytesDone),
      fetchImpl: makeStaticFetch(),
    });

    expect(installed.packId).toBe('core');
    expect(installed.releaseId).toBe(manifest.releaseId);
    expect(installed.filePaths).toHaveLength(descriptor.packNames.length);
    // Progress is monotonic and finishes at the declared total.
    expect(events.at(-1)).toBe(descriptor.bytes);
    expect([...events].sort((a, b) => a - b)).toEqual(events);

    const stored = await listInstalledPacks();
    expect(stored).toHaveLength(1);
    for (const path of installed.filePaths) {
      const file = await readPackFile(path);
      expect(file, path).not.toBeNull();
      expect(file!.bytes).toBe(file!.data.byteLength);
    }

    const usage = await measureStorage();
    expect(usage.packCount).toBe(1);
    expect(usage.packBytes).toBe(descriptor.bytes);
  });

  it('serves an installed pack without touching the network again', async () => {
    const manifest = readManifest();
    await downloadPack({
      source: source(makeStaticFetch()),
      manifest,
      descriptor: coreGroup(manifest),
      signal: new AbortController().signal,
      fetchImpl: makeStaticFetch(),
    });
    const { installedPackReader } = await import('./packManager');
    const log: string[] = [];
    const offlineSource = new StaticPackSource({
      baseUrl: '/data/',
      fetchImpl: makeStaticFetch({ log }),
      packReader: installedPackReader,
    });
    const places = await offlineSource.places('', 10);
    expect(places.places.length).toBeGreaterThan(0);
    // Only the manifest was fetched; every pack came from storage.
    expect(log).toEqual(['manifest.json']);
  });

  it('rejects a corrupted pack and installs nothing at all', async () => {
    const manifest = readManifest();
    const descriptor = coreGroup(manifest);
    const corruptName = descriptor.packNames[1] ?? descriptor.packNames[0]!;
    const corruptPath = manifest.files[corruptName]!.path;

    const failure = await downloadPack({
      source: source(makeStaticFetch()),
      manifest,
      descriptor,
      signal: new AbortController().signal,
      fetchImpl: makeStaticFetch({ corruptPaths: new Set([corruptPath]) }),
    }).catch((error: unknown) => error);

    expect(failure).toBeInstanceOf(PackDownloadError);
    expect((failure as PackDownloadError).failure).toMatchObject({ kind: 'integrity', path: corruptPath });
    // Nothing was installed, so what the device already had is untouched.
    expect(await listInstalledPacks()).toHaveLength(0);
    expect(await readPackFile(corruptPath)).toBeNull();
  });

  it('rejects a pack whose length does not match the manifest', async () => {
    const manifest = readManifest();
    const descriptor = coreGroup(manifest);
    const truncating = (async (input: RequestInfo | URL) => {
      const response = await makeStaticFetch()(input);
      const bytes = await response.arrayBuffer();
      return new Response(bytes.slice(0, Math.max(0, bytes.byteLength - 4)), { status: 200 });
    }) as typeof fetch;

    const failure = await downloadPack({
      source: source(makeStaticFetch()),
      manifest,
      descriptor,
      signal: new AbortController().signal,
      fetchImpl: truncating,
    }).catch((error: unknown) => error);

    expect((failure as PackDownloadError).failure.kind).toBe('integrity');
    expect(await listInstalledPacks()).toHaveLength(0);
  });

  it('reports an interrupted connection as offline and keeps what it verified', async () => {
    const manifest = readManifest();
    const descriptor = coreGroup(manifest);
    const failAfter = manifest.files[descriptor.packNames[1] ?? descriptor.packNames[0]!]!.path;

    const failure = await downloadPack({
      source: source(makeStaticFetch()),
      manifest,
      descriptor,
      signal: new AbortController().signal,
      fetchImpl: makeStaticFetch({ failPaths: new Set([failAfter]) }),
    }).catch((error: unknown) => error);

    expect((failure as PackDownloadError).failure.kind).toBe('offline');
    expect(await listInstalledPacks()).toHaveLength(0);

    // The attempt left resume state naming what it had already verified.
    const progress = await readDownloadProgress(`${manifest.releaseId}:core`);
    expect(progress).not.toBeNull();
    expect(progress!.state).toBe('paused');
    expect(progress!.completedPaths.length).toBeGreaterThan(0);
    expect(progress!.completedPaths).not.toContain(failAfter);
  });

  it('resumes without re-fetching the files it already verified', async () => {
    const manifest = readManifest();
    const descriptor = coreGroup(manifest);
    const failAfter = manifest.files[descriptor.packNames[1] ?? descriptor.packNames[0]!]!.path;

    await downloadPack({
      source: source(makeStaticFetch()),
      manifest,
      descriptor,
      signal: new AbortController().signal,
      fetchImpl: makeStaticFetch({ failPaths: new Set([failAfter]) }),
    }).catch(() => undefined);

    const progress = await readDownloadProgress(`${manifest.releaseId}:core`);
    const alreadyDone = new Set(progress!.completedPaths);
    expect(alreadyDone.size).toBeGreaterThan(0);

    const log: string[] = [];
    const installed = await downloadPack({
      source: source(makeStaticFetch()),
      manifest,
      descriptor,
      signal: new AbortController().signal,
      fetchImpl: makeStaticFetch({ log }),
    });

    expect(installed.filePaths).toHaveLength(descriptor.packNames.length);
    for (const path of alreadyDone) {
      expect(log, `${path} should not have been fetched again`).not.toContain(path);
    }
    // The resume state is cleared once the install succeeds.
    expect(await readDownloadProgress(`${manifest.releaseId}:core`)).toBeNull();
  });

  it('cancels immediately and installs nothing', async () => {
    const manifest = readManifest();
    const controller = new AbortController();
    const slowFetch = (async (input: RequestInfo | URL) => {
      controller.abort();
      return makeStaticFetch()(input);
    }) as typeof fetch;

    const failure = await downloadPack({
      source: source(makeStaticFetch()),
      manifest,
      descriptor: coreGroup(manifest),
      signal: controller.signal,
      fetchImpl: slowFetch,
    }).catch((error: unknown) => error);

    expect((failure as PackDownloadError).failure.kind).toBe('aborted');
    expect(await listInstalledPacks()).toHaveLength(0);
  });

  it('keeps the previous release installed when a newer one arrives, then rolls back', async () => {
    const manifest = readManifest();
    const descriptor = coreGroup(manifest);

    const older = await downloadPack({
      source: source(makeStaticFetch()),
      manifest,
      descriptor,
      signal: new AbortController().signal,
      fetchImpl: makeStaticFetch(),
    });

    // The same packs published under a new release id, which is what an update
    // that changed only some files looks like.
    const newer: Manifest = { ...manifest, releaseId: 'ffffffffffffffff' };
    const updated = await downloadPack({
      source: source(makeStaticFetch()),
      manifest: newer,
      descriptor: coreGroup(newer),
      signal: new AbortController().signal,
      fetchImpl: makeStaticFetch(),
    });

    const installed = await listInstalledPacks();
    expect(installed).toHaveLength(2);

    const views = await buildPackViews(newer, offlineGroupsFor(newer));
    const core = views.find((v) => v.descriptor.id === 'core')!;
    expect(core.status).toBe('installed');
    expect(core.installed!.releaseId).toBe(newer.releaseId);
    expect(core.previous!.releaseId).toBe(older.releaseId);

    // Rolling back removes the new copy and leaves the old one fully installed.
    await rollbackToPrevious(updated);
    const afterRollback = await listInstalledPacks();
    expect(afterRollback).toHaveLength(1);
    expect(afterRollback[0]!.releaseId).toBe(older.releaseId);
    // The shared files survive, because the older release still needs them.
    for (const path of older.filePaths) {
      expect(await readPackFile(path), path).not.toBeNull();
    }
  });

  it('deletes a pack and the files nothing else needs', async () => {
    const manifest = readManifest();
    const installed = await downloadPack({
      source: source(makeStaticFetch()),
      manifest,
      descriptor: coreGroup(manifest),
      signal: new AbortController().signal,
      fetchImpl: makeStaticFetch(),
    });
    await removePack(installed);
    expect(await listInstalledPacks()).toHaveLength(0);
    for (const path of installed.filePaths) {
      expect(await readPackFile(path), path).toBeNull();
    }
    expect((await measureStorage()).packBytes).toBe(0);
  });

  it('reports a full quota as storage_full rather than as a generic failure', async () => {
    const manifest = readManifest();
    const db = await import('../lib/db');
    const spy = vi.spyOn(db, 'installPackAtomically').mockRejectedValue(
      new db.StorageFullError('There is not enough room left in browser storage for this pack.'),
    );
    const failure = await downloadPack({
      source: source(makeStaticFetch()),
      manifest,
      descriptor: coreGroup(manifest),
      signal: new AbortController().signal,
      fetchImpl: makeStaticFetch(),
    }).catch((error: unknown) => error);
    expect((failure as PackDownloadError).failure.kind).toBe('storage_full');
    spy.mockRestore();
  });

  it('shows a pack as not installed before anything is downloaded', async () => {
    const manifest = readManifest();
    const views = await buildPackViews(manifest, offlineGroupsFor(manifest));
    for (const view of views) {
      expect(view.status).toBe('not_installed');
      expect(view.installed).toBeNull();
      expect(view.fileCount).toBe(view.descriptor.packNames.length);
    }
  });
});
