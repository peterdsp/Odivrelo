import { parseBoolean, readPref, writePref } from '../lib/prefs';

const PREF = 'onboarded';

/**
 * Whether the reader has seen the first-launch page.
 *
 * Stored as a UI preference, not a record: if the browser throws the flag away
 * the worst outcome is seeing a short honest page a second time, which is not a
 * failure. If storage is refused entirely, `markOnboarded` cannot persist, so the
 * session-level flag below keeps the page from reappearing during the same visit.
 */
let seenThisSession = false;

export function hasOnboarded(): boolean {
  return seenThisSession || readPref<boolean>(PREF, false, parseBoolean);
}

export function markOnboarded(): void {
  seenThisSession = true;
  writePref(PREF, true);
}

/** Only the tests reset this. */
export function resetOnboardingForTests(): void {
  seenThisSession = false;
}
