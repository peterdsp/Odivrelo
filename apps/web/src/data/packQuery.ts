/**
 * The small amount of query work the client legitimately still does.
 *
 * This file used to hold a journey-search engine. It does not any more: packs
 * carry contract-shaped endpoint responses, so there is exactly one timetable
 * implementation and it lives in the release generator. What is left is text
 * matching for the place picker, and a shape check for journey links so a
 * mistyped or truncated URL is rejected before anything is fetched.
 */

/** Case- and accent-insensitive across el/en/sq, as the contract requires. */
export function foldForSearch(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/[͵᾽]/g, '')
    .toLocaleLowerCase('el')
    .replace(/ς/g, 'σ')
    .trim();
}

export interface SearchablePlace {
  readonly id: string;
  readonly kind: 'stop_place' | 'stop';
  readonly name: { readonly el: string; readonly en: string; readonly sq: string };
  readonly municipality: string;
}

/**
 * Ranks places for the picker: a prefix match beats a substring match, and a
 * terminal beats one of its own boarding points at equal strength, because the
 * terminal is nearly always what a passenger means.
 */
export function searchPlaces<T extends SearchablePlace>(places: readonly T[], query: string, limit: number): T[] {
  const needle = foldForSearch(query);
  if (needle.length === 0) {
    /*
     * An empty query is a request for the list, not for a filtered view of it.
     * Returning only terminals here was wrong: callers that need to resolve an
     * arbitrary place id, such as the results heading, silently got nothing back
     * for a boarding point and fell back to printing the raw identifier.
     *
     * Terminals still sort first, so a picker showing this list unfiltered leads
     * with them.
     */
    return [...places]
      .sort((a, b) => (a.kind === b.kind ? 0 : a.kind === 'stop_place' ? -1 : 1))
      .slice(0, limit);
  }
  const scored: { place: T; score: number }[] = [];
  for (const place of places) {
    const haystacks = [place.name.el, place.name.en, place.name.sq, place.municipality].map(foldForSearch);
    let best = -1;
    for (const hay of haystacks) {
      if (hay.startsWith(needle)) best = Math.max(best, 2);
      else if (hay.includes(needle)) best = Math.max(best, 1);
    }
    if (best >= 0) scored.push({ place, score: best + (place.kind === 'stop_place' ? 0.5 : 0) });
  }
  scored.sort((a, b) => b.score - a.score || a.place.name.en.localeCompare(b.place.name.en));
  return scored.slice(0, limit).map((s) => s.place);
}

/**
 * Journey identifiers are opaque: nothing here reads meaning out of one. This
 * only confirms that a link carries something that could be an identifier, so a
 * broken URL produces a useful screen rather than a failed request.
 */
export function isJourneyIdShape(value: string): boolean {
  return value.length > 0 && value.length <= 128 && /^[A-Za-z0-9._:~-]+$/.test(value);
}

/** Kept for call sites that only need a yes or no on a journey link. */
export function decomposeJourneyId(id: string): { tripId: string } | null {
  return isJourneyIdShape(id) ? { tripId: id } : null;
}
