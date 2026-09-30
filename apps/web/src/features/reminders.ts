/**
 * Opt-in travel reminders, and an honest account of what the web can do.
 *
 * What is true: a browser can show a notification, and this app can schedule one
 * while the page is open.
 *
 * What is not true, and is therefore said plainly rather than implied: a website
 * cannot wake itself up. There is no reliable scheduled background trigger in any
 * shipping browser, so if every tab is closed the reminder will not arrive. The UI
 * states that before the reader opts in, and the native apps are where a
 * guaranteed reminder belongs.
 *
 * Payload rule: a reminder names the departure time and the place. It never
 * carries a passenger name, a booking reference, a ticket image or a barcode,
 * because a notification payload is handled by the operating system and can be
 * visible on a lock screen.
 */

export type NotificationSupport = 'unsupported' | 'default' | 'granted' | 'denied';

export const REMINDER_CHOICES = [15, 30, 60, 120] as const;
export type ReminderMinutes = (typeof REMINDER_CHOICES)[number];

export function notificationSupport(): NotificationSupport {
  try {
    if (typeof globalThis.Notification === 'undefined') return 'unsupported';
    const permission = globalThis.Notification.permission;
    if (permission === 'granted' || permission === 'denied') return permission;
    return 'default';
  } catch {
    return 'unsupported';
  }
}

export async function requestNotificationPermission(): Promise<NotificationSupport> {
  if (typeof globalThis.Notification === 'undefined') return 'unsupported';
  try {
    const result = await globalThis.Notification.requestPermission();
    return result === 'granted' ? 'granted' : result === 'denied' ? 'denied' : 'default';
  } catch {
    // Some browsers throw on the legacy callback form, or in an insecure context.
    return notificationSupport();
  }
}

/** When the reminder for a departure should fire, or null if that moment has passed. */
export function reminderInstant(departureIso: string, minutesBefore: number, now: Date = new Date()): Date | null {
  const at = new Date(Date.parse(departureIso) - minutesBefore * 60_000);
  return at.getTime() > now.getTime() ? at : null;
}

export interface ScheduledReminder {
  readonly tripId: string;
  readonly at: Date;
  cancel: () => void;
}

/**
 * Schedules a reminder for as long as this page stays open.
 *
 * The timer is deliberately not persisted as a promise of delivery: a reload
 * re-creates it from the saved trip, and if the page is gone the reminder simply
 * does not fire, which the UI has already said.
 */
export function scheduleReminder(options: {
  tripId: string;
  at: Date;
  title: string;
  body: string;
  onFired?: () => void;
  onRevoked?: () => void;
}): ScheduledReminder | null {
  const delay = options.at.getTime() - Date.now();
  if (delay <= 0) return null;
  // A timeout longer than the 32-bit limit fires immediately, which would be
  // worse than not scheduling at all.
  if (delay > 2_147_483_000) return null;

  const handle = globalThis.setTimeout(() => {
    if (notificationSupport() !== 'granted') {
      // Permission was withdrawn after the reminder was set. Say so rather than
      // failing silently.
      options.onRevoked?.();
      return;
    }
    try {
      // Title and body only. No passenger name, no reference, no image, no barcode.
      new globalThis.Notification(options.title, {
        body: options.body,
        lang: globalThis.document?.documentElement.lang || 'el',
        tag: `poravia-trip-${options.tripId}`,
        requireInteraction: false,
        silent: false,
      });
      options.onFired?.();
    } catch {
      options.onRevoked?.();
    }
  }, delay);

  return {
    tripId: options.tripId,
    at: options.at,
    cancel: () => globalThis.clearTimeout(handle),
  };
}

/**
 * Whether this browser could in principle deliver a reminder with no tab open.
 *
 * At the time of writing nothing ships a usable answer, so this returns false and
 * the UI says so. It is a function rather than a constant so that the claim is
 * re-derived from a capability check rather than remembered.
 */
export function canDeliverInBackground(): boolean {
  const registration = 'serviceWorker' in globalThis.navigator;
  const periodicSync = 'PeriodicSyncManager' in globalThis;
  const notificationTrigger =
    typeof globalThis.Notification !== 'undefined' && 'showTrigger' in (globalThis.Notification.prototype as object);
  return registration && periodicSync && notificationTrigger;
}
