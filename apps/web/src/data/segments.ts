/**
 * Journey segments, shared by the pack source and the API test double so the two
 * cannot disagree, and matched to the server so the web agrees with the contract.
 *
 * A coach runs from its own first stop to its own last. A traveller searches one
 * pair of stops along that run, so a result names the leg between them and the
 * detail headlines that leg while still showing the whole run with the boarded
 * and alighted stops marked. The id carries the leg, so a shared link or a reload
 * reopens the same one.
 */
import type { BoardingPoint, JourneyDetail, JourneyStop, JourneySummary, SegmentRole } from './contract';
import { ContractError } from './contract';
import type { StopPackEntry } from './packShapes';

export const JOURNEY_ID_SEPARATOR = '~';

export function composeJourneyId(tripId: string, boardStopId: string, alightStopId: string): string {
  return `${tripId}${JOURNEY_ID_SEPARATOR}${boardStopId}${JOURNEY_ID_SEPARATOR}${alightStopId}`;
}

export function tripIdOf(journeyId: string): string {
  return journeyId.split(JOURNEY_ID_SEPARATOR)[0] ?? journeyId;
}

export interface ParsedJourneyId {
  readonly tripId: string;
  readonly boardStopId: string | null;
  readonly alightStopId: string | null;
}

/** A bare trip id is the whole run. Any other shape is rejected, never guessed. */
export function parseJourneyId(journeyId: string): ParsedJourneyId {
  const parts = journeyId.split(JOURNEY_ID_SEPARATOR);
  if (parts.length === 1 && parts[0]) {
    return { tripId: parts[0], boardStopId: null, alightStopId: null };
  }
  if (parts.length === 3 && parts[0] && parts[1] && parts[2]) {
    return { tripId: parts[0], boardStopId: parts[1], alightStopId: parts[2] };
  }
  throw new ContractError('invalid_request', 'That journey link is not in a form this release understands.', 'id');
}

function ordered(stops: readonly JourneyStop[]): JourneyStop[] {
  return [...stops].sort((a, b) => a.sequence - b.sequence);
}

/** Minutes between two timestamps, floored, so a leg is never a minute long. */
function durationMinutes(departAt: string, arriveAt: string): number {
  return Math.floor((Date.parse(arriveAt) - Date.parse(departAt)) / 60_000);
}

/**
 * True when arrival falls on a later local day. The stored timestamps already
 * carry the Europe/Athens offset, so the date prefix is the local calendar day
 * and no clock arithmetic is redone here.
 */
function crossesMidnight(departAt: string, arriveAt: string): boolean {
  return arriveAt.slice(0, 10) > departAt.slice(0, 10);
}

/** A boarding point for a stop, read from the stops pack for an intermediate leg. */
export function boardingPointFromStop(entry: StopPackEntry | undefined, stop: JourneyStop): BoardingPoint {
  if (!entry) {
    return {
      stopId: stop.stopId,
      name: stop.name,
      terminalName: stop.name,
      bay: null,
      latitude: 0,
      longitude: 0,
      instructions: null,
      reviewState: 'published',
      reviewedAt: null,
      stepFree: null,
    };
  }
  return {
    stopId: entry.id,
    name: entry.name,
    terminalName: entry.terminal?.name ?? entry.name,
    bay: entry.bay,
    latitude: entry.latitude,
    longitude: entry.longitude,
    instructions: entry.instructions,
    reviewState: entry.reviewState ?? 'published',
    reviewedAt: entry.reviewedAt ?? null,
    stepFree: entry.stepFree,
  };
}

function roleOf(index: number, boardIndex: number, alightIndex: number): SegmentRole {
  if (index === boardIndex) return 'board';
  if (index === alightIndex) return 'alight';
  if (index > boardIndex && index < alightIndex) return 'onSegment';
  if (index < boardIndex) return 'beforeBoard';
  return 'afterAlight';
}

/**
 * The earliest origin stop and the first destination stop after it, by position
 * on the run. Boarding rules are shown on the stop, not used to pick the leg, so
 * this matches the server's own selection.
 */
function firstPair(
  stops: JourneyStop[],
  originStops: ReadonlySet<string>,
  destinationStops: ReadonlySet<string>,
): { boardIndex: number; alightIndex: number } | null {
  for (let boardIndex = 0; boardIndex < stops.length; boardIndex += 1) {
    if (!originStops.has(stops[boardIndex]!.stopId)) continue;
    for (let alightIndex = boardIndex + 1; alightIndex < stops.length; alightIndex += 1) {
      if (destinationStops.has(stops[alightIndex]!.stopId)) {
        return { boardIndex, alightIndex };
      }
    }
  }
  return null;
}

/** Resolve an explicit board and alight stop to positions, falling back whole. */
function segmentIndices(
  stops: JourneyStop[],
  boardStopId: string | null,
  alightStopId: string | null,
): { boardIndex: number; alightIndex: number } {
  const whole = { boardIndex: 0, alightIndex: stops.length - 1 };
  if (!boardStopId || !alightStopId) return whole;
  const boardIndex = stops.findIndex((stop) => stop.stopId === boardStopId);
  if (boardIndex < 0) return whole;
  let alightIndex = -1;
  for (let i = stops.length - 1; i > boardIndex; i -= 1) {
    if (stops[i]!.stopId === alightStopId) {
      alightIndex = i;
      break;
    }
  }
  if (alightIndex < 0) return whole;
  return { boardIndex, alightIndex };
}

/**
 * A search result for one leg, synthesised from the day's whole-run summary and
 * its detail. Returns null when the run does not serve the pair in order.
 */
export function segmentSummary(
  summary: JourneySummary,
  detail: JourneyDetail,
  originStops: ReadonlySet<string>,
  destinationStops: ReadonlySet<string>,
): JourneySummary | null {
  const stops = ordered(detail.stops);
  const pair = firstPair(stops, originStops, destinationStops);
  if (!pair) return null;
  const board = stops[pair.boardIndex]!;
  const alight = stops[pair.alightIndex]!;
  const departAt = board.departureAt ?? board.arrivalAt;
  const arriveAt = alight.arrivalAt ?? alight.departureAt;
  if (!departAt || !arriveAt) return null;
  return {
    ...summary,
    id: composeJourneyId(tripIdOf(summary.id), board.stopId, alight.stopId),
    departure: { at: departAt, stopId: board.stopId, stopName: board.name, quality: board.timeQuality },
    arrival: { at: arriveAt, stopId: alight.stopId, stopName: alight.name, quality: alight.timeQuality },
    durationMinutes: durationMinutes(departAt, arriveAt),
    intermediateStopCount: Math.max(0, pair.alightIndex - pair.boardIndex - 1),
    crossesMidnight: crossesMidnight(departAt, arriveAt),
  };
}

/**
 * A detail scoped to the leg named by the id. The whole run is returned as
 * published when the leg is the whole run (a bare id, or the first to last
 * stop), otherwise the headline and the stop roles are recomputed for the leg.
 *
 * `boardingPointFor` supplies a boarding point for a leg that starts at an
 * intermediate stop; for a leg that starts at the run's first stop the detail's
 * own boarding point already describes it.
 */
export function scopedDetail(
  detail: JourneyDetail,
  boardStopId: string | null,
  alightStopId: string | null,
  boardingPointFor?: (stop: JourneyStop) => JourneyDetail['boardingPoint'],
): JourneyDetail {
  const stops = ordered(detail.stops);
  const { boardIndex, alightIndex } = segmentIndices(stops, boardStopId, alightStopId);
  const board = stops[boardIndex]!;
  const alight = stops[alightIndex]!;
  const withRoles = stops.map((stop, index) => ({ ...stop, segmentRole: roleOf(index, boardIndex, alightIndex) }));
  const selectedSegment = { boardStopId: board.stopId, alightStopId: alight.stopId };

  if (boardIndex === 0 && alightIndex === stops.length - 1) {
    return { ...detail, stops: withRoles, selectedSegment };
  }

  const departAt = board.departureAt ?? board.arrivalAt ?? detail.departure.at;
  const arriveAt = alight.arrivalAt ?? alight.departureAt ?? detail.arrival.at;
  const boardingPoint =
    boardIndex === 0 ? detail.boardingPoint : boardingPointFor?.(board) ?? detail.boardingPoint;
  return {
    ...detail,
    id: composeJourneyId(tripIdOf(detail.id), board.stopId, alight.stopId),
    departure: { at: departAt, stopId: board.stopId, stopName: board.name, quality: board.timeQuality },
    arrival: { at: arriveAt, stopId: alight.stopId, stopName: alight.name, quality: alight.timeQuality },
    durationMinutes: durationMinutes(departAt, arriveAt),
    intermediateStopCount: Math.max(0, alightIndex - boardIndex - 1),
    crossesMidnight: crossesMidnight(departAt, arriveAt),
    boardingPoint,
    selectedSegment,
    stops: withRoles,
  };
}
