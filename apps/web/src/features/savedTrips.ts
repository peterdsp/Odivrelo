import type { JourneyDetail } from '../data/contract';
import {
  deleteSavedTrip,
  listSavedTrips,
  putSavedTrip,
  StorageFullError,
  type SavedTrip,
} from '../lib/db';

/**
 * Saving a trip stores the journey exactly as it was read, not a reference to it.
 *
 * That is what makes a saved trip work with no connection and with no installed
 * pack: the times, the ordered stops, the boarding instructions, the operator
 * contacts and the provenance are all in the snapshot. It also means the app can
 * say honestly which release the copy came from and how old it is, rather than
 * quietly showing today's data under yesterday's heading.
 */

export function savedTripId(journeyId: string, serviceDate: string): string {
  return `${serviceDate}#${journeyId}`;
}

export async function loadSavedTrips(): Promise<SavedTrip[]> {
  return listSavedTrips();
}

export interface SaveTripInput {
  readonly detail: JourneyDetail;
  readonly releaseId: string;
}

export async function saveTrip({ detail, releaseId }: SaveTripInput): Promise<SavedTrip> {
  const existing = (await listSavedTrips()).find((trip) => trip.id === savedTripId(detail.id, detail.serviceDate));
  const trip: SavedTrip = {
    id: savedTripId(detail.id, detail.serviceDate),
    journeyId: detail.id,
    serviceDate: detail.serviceDate,
    releaseId,
    detail,
    savedAt: existing?.savedAt ?? new Date().toISOString(),
    reminderMinutes: existing?.reminderMinutes ?? null,
    reminderScheduledFor: existing?.reminderScheduledFor ?? null,
    walletItemIds: existing?.walletItemIds ?? [],
  };
  await putSavedTrip(trip);
  return trip;
}

export async function removeTrip(id: string): Promise<void> {
  await deleteSavedTrip(id);
}

export async function updateTrip(trip: SavedTrip): Promise<void> {
  await putSavedTrip(trip);
}

export { StorageFullError };
export type { SavedTrip };

/**
 * What a saved trip can and cannot do with no connection.
 *
 * Every `true` below is something genuinely present in the snapshot. Map imagery
 * and live updates are listed precisely so they can be reported as absent: the
 * app ships no offline tiles and has no real-time feed, and claiming either would
 * be the one lie that actually strands a passenger.
 */
export interface TripReadiness {
  readonly schedule: boolean;
  readonly boarding: boolean;
  readonly contacts: boolean;
  readonly ticket: boolean;
  readonly mapData: boolean;
  readonly mapTiles: false;
  readonly live: false;
}

export function readinessOf(trip: SavedTrip): TripReadiness {
  const { detail } = trip;
  return {
    schedule: detail.stops.length > 0,
    boarding: detail.boardingPoint.instructions !== null || detail.boardingPoint.bay !== null,
    contacts: Boolean(detail.purchase.phone || detail.purchase.address || detail.purchase.url),
    ticket: trip.walletItemIds.length > 0,
    mapData: detail.stops.every((stop) => Number.isFinite(stop.latitude) && Number.isFinite(stop.longitude)),
    mapTiles: false,
    live: false,
  };
}

/** Whether a newly read copy differs from the saved snapshot in a way a passenger would care about. */
export function hasMaterialChange(saved: JourneyDetail, fresh: JourneyDetail): boolean {
  if (saved.departure.at !== fresh.departure.at) return true;
  if (saved.arrival.at !== fresh.arrival.at) return true;
  if (saved.boardingPoint.stopId !== fresh.boardingPoint.stopId) return true;
  if (saved.boardingPoint.bay !== fresh.boardingPoint.bay) return true;
  if (saved.stops.length !== fresh.stops.length) return true;
  if (saved.purchase.kind !== fresh.purchase.kind) return true;
  if (saved.purchase.url !== fresh.purchase.url) return true;
  return saved.stops.some((stop, index) => {
    const other = fresh.stops[index];
    return !other || other.stopId !== stop.stopId || other.departureAt !== stop.departureAt || other.arrivalAt !== stop.arrivalAt;
  });
}
