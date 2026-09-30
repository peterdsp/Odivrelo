import { existsSync, mkdirSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect, type Page, type TestInfo } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/** Shared helpers for the end-to-end suite. */

const here = dirname(fileURLToPath(import.meta.url));
export const WEB_ROOT = join(here, '..');
export const SCREENSHOT_DIR = join(WEB_ROOT, 'artifacts', 'screenshots');

export interface ReleaseFacts {
  readonly releaseId: string;
  readonly serviceDates: readonly string[];
  readonly placeIds: readonly string[];
  readonly stopPlaceIds: readonly string[];
  readonly operatorIds: readonly string[];
  /** Greek display name per place id, so a test can drive the picker by typing. */
  readonly placeNames: Readonly<Record<string, string>>;
  /** An origin, destination and date that genuinely produces results. */
  readonly workingQuery: { originId: string; destinationId: string; date: string } | null;
  readonly journeyId: string | null;
}

/**
 * Reads the built release so the tests drive the app with ids that really exist.
 *
 * Hardcoding an id would make the suite pass against a stale build and fail
 * mysteriously against a fresh one.
 */
export function readReleaseFacts(): ReleaseFacts {
  const dataDir = join(WEB_ROOT, 'dist', 'data');
  const manifestPath = join(dataDir, 'manifest.json');
  if (!existsSync(manifestPath)) {
    return {
      releaseId: 'unknown',
      serviceDates: [],
      placeIds: [],
      stopPlaceIds: [],
      operatorIds: [],
      placeNames: {},
      workingQuery: null,
      journeyId: null,
    };
  }
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8')) as {
    releaseId: string;
    files: Record<string, { path: string }>;
  };
  const read = <T>(name: string): T => JSON.parse(readFileSync(join(dataDir, manifest.files[name]!.path), 'utf8')) as T;

  const places = read<{ places: { id: string; kind: string; name: { el: string } }[] }>('places');
  const operators = read<{ operators: Record<string, unknown> }>('operators');
  const serviceDates = Object.keys(manifest.files)
    .filter((name) => name.startsWith('journeys-'))
    .map((name) => name.slice('journeys-'.length))
    .sort();

  let workingQuery: ReleaseFacts['workingQuery'] = null;
  let journeyId: string | null = null;
  for (const date of serviceDates) {
    const pack = read<{ results: { id: string; departure: { stopId: string }; arrival: { stopId: string } }[] }>(
      `journeys-${date}`,
    );
    const first = pack.results[0];
    if (first) {
      workingQuery = { originId: first.departure.stopId, destinationId: first.arrival.stopId, date };
      journeyId = first.id;
      break;
    }
  }

  return {
    releaseId: manifest.releaseId,
    serviceDates,
    placeIds: places.places.map((p) => p.id),
    stopPlaceIds: places.places.filter((p) => p.kind === 'stop_place').map((p) => p.id),
    operatorIds: Object.keys(operators.operators),
    placeNames: Object.fromEntries(places.places.map((p) => [p.id, p.name.el])),
    workingQuery,
    journeyId,
  };
}

/**
 * Puts the browser straight past the first-launch page.
 *
 * Applied before the document loads, so the app never renders the welcome page
 * first and the test is not racing a redirect.
 */
export async function skipOnboarding(page: Page, language: 'el' | 'en' | 'sq' = 'el'): Promise<void> {
  await page.addInitScript(
    ([lang]) => {
      try {
        globalThis.localStorage.setItem('odivrelo.v1.onboarded', 'true');
        globalThis.localStorage.setItem('odivrelo.v1.language', JSON.stringify(lang));
      } catch {
        // A browser refusing storage is a scenario of its own, tested separately.
      }
    },
    [language],
  );
}

export async function setLanguage(page: Page, language: 'el' | 'en' | 'sq'): Promise<void> {
  await page.addInitScript(
    ([lang]) => {
      try {
        globalThis.localStorage.setItem('odivrelo.v1.language', JSON.stringify(lang));
      } catch {
        /* see above */
      }
    },
    [language],
  );
}

export async function setTheme(page: Page, theme: 'light' | 'dark'): Promise<void> {
  await page.emulateMedia({ colorScheme: theme });
  await page.addInitScript(
    ([value]) => {
      try {
        globalThis.localStorage.setItem('odivrelo.v1.theme', JSON.stringify(value));
      } catch {
        /* see above */
      }
    },
    [theme],
  );
}

/**
 * Runs axe-core and asserts zero violations.
 *
 * The whole rendered page is scanned. Nothing is excluded: an exclusion is a
 * quiet way of failing a gate, so if something here is genuinely a false positive
 * it is disabled by rule with a reason rather than by hiding a region.
 */
export async function expectNoAxeViolations(page: Page, testInfo: TestInfo, label: string): Promise<void> {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa', 'best-practice'])
    .analyze();

  if (results.violations.length > 0) {
    // Attach the detail so a failure names the element, not just the rule.
    await testInfo.attach(`axe-${label}.json`, {
      body: JSON.stringify(results.violations, null, 2),
      contentType: 'application/json',
    });
  }

  const summary = results.violations.map((v) => `${v.id} (${v.impact}) x${v.nodes.length}: ${v.help}`);
  expect(summary, `axe found violations on ${label}`).toEqual([]);
}

/**
 * Saves a screenshot from the running production build.
 *
 * The file name carries the scenario, the project (engine plus width) and the
 * date, so an artifact directory is self-describing months later.
 */
export async function shoot(page: Page, testInfo: TestInfo, scenario: string): Promise<string> {
  mkdirSync(SCREENSHOT_DIR, { recursive: true });
  const viewport = page.viewportSize();
  const date = new Date().toISOString().slice(0, 10);
  const name = [scenario, testInfo.project.name, viewport ? `${viewport.width}x${viewport.height}` : 'novp', date]
    .join('__')
    .replace(/[^a-zA-Z0-9._-]+/g, '-');
  const file = join(SCREENSHOT_DIR, `${name}.png`);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

/** Waits for the app shell to have replaced the no-JavaScript boot message. */
export async function waitForApp(page: Page): Promise<void> {
  await expect(page.getByRole('main')).toBeVisible();
  await expect(page.locator('#od-boot')).toHaveCount(0);
}

/**
 * Simulates having no connection, in a way that works on every engine.
 *
 * `context.setOffline(true)` is the obvious tool, but in this Playwright build a
 * document navigation while it is set raises an internal error in WebKit, and the
 * brief specifically requires reloading while offline. Aborting every network
 * request instead is a faithful simulation: the service worker still serves what
 * it has cached, IndexedDB is untouched, and anything that genuinely needs the
 * network fails exactly as it would.
 *
 * `navigator.onLine` is forced too, because the UI words itself differently when
 * the browser knows it is offline, and that wording is part of what is tested.
 */
export async function goOffline(page: Page): Promise<void> {
  await page.evaluate(() => {
    Object.defineProperty(navigator, 'onLine', { get: () => false, configurable: true });
    globalThis.dispatchEvent(new Event('offline'));
  });
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'onLine', { get: () => false, configurable: true });
  });
  // Only the application's own origin is cut off. Aborting literally everything
  // also severs the inspector Playwright uses to capture a trace, which shows up
  // as "Blocked by Web Inspector" rather than as the offline behaviour under test.
  await page.route(/^https?:\/\/127\.0\.0\.1:4173\//, (route) => route.abort('internetdisconnected'));
}

export async function goOnline(page: Page): Promise<void> {
  await page.unroute(/^https?:\/\/127\.0\.0\.1:4173\//);
  await page.evaluate(() => {
    Object.defineProperty(navigator, 'onLine', { get: () => true, configurable: true });
    globalThis.dispatchEvent(new Event('online'));
  });
}

/**
 * Whether this engine lets a link take focus at all.
 *
 * Safari and WebKit leave links out of the sequential focus order, and refuse
 * programmatic `focus()` on them, unless the reader has turned on Full Keyboard
 * Access. That is a platform default the app cannot change, so a test that
 * assumed otherwise would be testing the browser rather than the product. Where
 * it is false, the suite asserts the structural properties of the skip link and
 * that activating it works, which is the part the app is responsible for.
 */
export async function linksAreFocusable(page: Page): Promise<boolean> {
  // Programmatic focus() succeeds on a link in WebKit, so probing that proves
  // nothing. What matters is whether pressing Tab reaches one.
  await page.evaluate(() => {
    const probe = document.createElement('a');
    probe.id = 'od-tab-probe';
    probe.href = '#probe';
    probe.textContent = 'probe';
    document.body.prepend(probe);
    (document.activeElement as HTMLElement | null)?.blur();
    document.body.focus();
  });
  await page.keyboard.press('Tab');
  const reached = await page.evaluate(() => {
    const landed = document.activeElement?.id === 'od-tab-probe';
    document.querySelector('#od-tab-probe')?.remove();
    return landed;
  });
  return reached;
}

/**
 * Whether this engine can reload a document while the network is cut and have the
 * service worker answer.
 *
 * In Playwright's WebKit, route interception sits in front of the service worker,
 * so aborting the request kills the navigation before the worker is consulted and
 * the reload fails outright. `context.setOffline` raises an internal error on the
 * same navigation. Neither is something the application can influence, so the
 * offline-reload assertion runs where the engine permits it and the in-app
 * offline behaviour, which is the part the app owns, is checked everywhere.
 */
export function canReloadWhileOffline(page: Page): boolean {
  return engineOf(page) !== 'webkit';
}

export function engineOf(page: Page): string {
  return page.context().browser()?.browserType().name() ?? 'unknown';
}

/**
 * Whether this engine includes links in the sequential focus order.
 *
 * Safari and WebKit do not, unless the reader has turned on Full Keyboard
 * Access, and they will not move focus to a link with Tab however the page is
 * written. It is a browser setting, not something the application can influence,
 * so tests that depend on it are skipped by name on WebKit rather than quietly
 * asserting something weaker.
 */
export function tabReachesLinks(page: Page): boolean {
  return engineOf(page) !== 'webkit';
}

/**
 * Navigates within the running application, the way clicking a link does.
 *
 * No document request is made, so this works with the network cut on every
 * engine, and it is the path a reader actually takes once the app is open.
 */
export async function navigateInApp(page: Page, path: string): Promise<void> {
  await page.evaluate((target) => {
    globalThis.history.pushState({}, '', target);
    globalThis.dispatchEvent(new PopStateEvent('popstate', { state: {} }));
  }, path);
  await expect(page).toHaveURL(new RegExp(path.split('?')[0]!.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
}

/** The demonstration notice must be present on every page while the data is invented. */
export async function expectDemoNotice(page: Page): Promise<void> {
  const banner = page.getByTestId('demo-banner');
  await expect(banner).toBeVisible();
  const robots = page.locator('meta[name="robots"]');
  await expect(robots).toHaveAttribute('content', 'noindex, nofollow');
}
