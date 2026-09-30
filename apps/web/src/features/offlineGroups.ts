import type { Manifest, OfflinePackDescriptor } from '../data/contract';
import { serviceDateFromPackName } from '../data/packShapes';

/**
 * Groups the release's packs into the three things a passenger would actually
 * choose between.
 *
 * The manifest publishes packs; how they are offered for download is a
 * presentation decision, so it is made here rather than baked into the release.
 * Every byte count is the sum of real manifest entries, so the size shown before
 * a download is the size that will be transferred, not an estimate.
 */
export function offlineGroupsFor(manifest: Manifest): OfflinePackDescriptor[] {
  const names = Object.keys(manifest.files);
  const bytesOf = (list: readonly string[]) => list.reduce((total, name) => total + (manifest.files[name]?.bytes ?? 0), 0);

  const core = ['meta', 'places', 'operators', 'stops', 'coverage', 'sources'].filter((name) => names.includes(name));
  const timetables = names.filter((name) => serviceDateFromPackName(name) !== null).sort();
  const gtfs = names.filter((name) => name === 'gtfs');

  const groups: OfflinePackDescriptor[] = [];
  if (core.length > 0) {
    groups.push({
      id: 'core',
      nameKey: 'offline.packCore',
      descriptionKey: 'offline.packCoreBody',
      required: true,
      packNames: core,
      bytes: bytesOf(core),
    });
  }
  if (timetables.length > 0) {
    groups.push({
      id: 'timetables',
      nameKey: 'offline.packTimetables',
      descriptionKey: 'offline.packTimetablesBody',
      required: false,
      packNames: timetables,
      bytes: bytesOf(timetables),
    });
  }
  if (gtfs.length > 0) {
    groups.push({
      id: 'gtfs',
      nameKey: 'offline.packGtfs',
      descriptionKey: 'offline.packGtfsBody',
      required: false,
      packNames: gtfs,
      bytes: bytesOf(gtfs),
    });
  }
  return groups;
}

/** Service dates the release holds timetables for, in order. */
export function serviceDatesOf(manifest: Manifest): string[] {
  return Object.keys(manifest.files)
    .map(serviceDateFromPackName)
    .filter((value): value is string => value !== null)
    .sort();
}
