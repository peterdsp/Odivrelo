import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { expect, test } from '@playwright/test';
import { readReleaseFacts, WEB_ROOT } from './support';

const facts = readReleaseFacts();
const dist = join(WEB_ROOT, 'dist');

/**
 * Every route has a real static entry point.
 *
 * Without one, a static host answers a deep link with `404.html` and a 404 status:
 * the app renders, but the response says the page does not exist. A shared journey
 * link then reports 404 to whoever receives it, link previews treat it as broken,
 * and the operator and station pages the product exists to publish could not be
 * indexed on the day the data becomes real.
 *
 * This test fails if a route in the router has no prerendered entry point, so a
 * new route cannot silently regress. Stations and journeys are the exception:
 * they are answered by a `_redirects` rewrite to the shell, which is a 200 for
 * the same bytes, because a file each does not fit the host's file limit.
 */

/**
 * Every path pattern the router serves.
 *
 * Kept beside the router deliberately: adding a route without adding it here, or
 * without teaching the prerenderer to emit it, fails this test.
 */
const ROUTER_PATHS = [
  { pattern: '/', kind: 'static' },
  { pattern: '/welcome', kind: 'static' },
  { pattern: '/search', kind: 'static' },
  { pattern: '/results', kind: 'static' },
  { pattern: '/operators', kind: 'static' },
  { pattern: '/operators/:id', kind: 'operator' },
  { pattern: '/stations', kind: 'static' },
  { pattern: '/stations/:id', kind: 'stop' },
  { pattern: '/journey/:id', kind: 'journey' },
  { pattern: '/journey/:id/booking', kind: 'journeyBooking' },
  { pattern: '/saved', kind: 'static' },
  { pattern: '/offline', kind: 'static' },
  { pattern: '/wallet', kind: 'static' },
  { pattern: '/settings', kind: 'static' },
  { pattern: '/coverage', kind: 'static' },
  { pattern: '/licences', kind: 'static' },
] as const;

function entryPointExists(path: string): boolean {
  const directory = path === '/' ? dist : join(dist, path.replace(/^\//, ''));
  return existsSync(join(directory, 'index.html'));
}


test.describe('prerendered entry points', () => {
  test('every static route in the router has one', () => {
    const missing = ROUTER_PATHS.filter((route) => route.kind === 'static' && !entryPointExists(route.pattern)).map(
      (route) => route.pattern,
    );
    expect(missing, 'these router paths have no prerendered entry point and will return 404').toEqual([]);
  });

  test('every operator in the release has one', () => {
    test.skip(facts.releaseId === 'unknown', 'no release is published in dist');
    const missing = facts.operatorIds.map((id) => `/operators/${id}`).filter((path) => !entryPointExists(path));
    expect(missing, 'these operators are published but have no prerendered page').toEqual([]);
  });

  test('stations and journeys are answered by the shell with a 200, not by a file each', () => {
    /*
     * There are tens of thousands of stations and journeys, and a file each took
     * the deployment past the host's 20,000 file limit. `_redirects` rewrites
     * both routes to the shell instead. The target must be `/`: Cloudflare Pages
     * redirects `/index.html` to `/`, so a rewrite to it loses the address.
     */
    const rules = readFileSync(join(dist, '_redirects'), 'utf8')
      .split('\n')
      .map((line) => line.trim())
      .filter((line) => line && !line.startsWith('#'))
      .map((line) => line.split(/\s+/));
    for (const from of ['/stations/*', '/journey/*']) {
      expect(rules, `no rewrite for ${from}`).toContainEqual([from, '/', '200']);
    }
    // A rule wins over a file on Pages, so a file under a rewritten route would
    // be dead weight in the file budget and never served.
    for (const route of ['stations', 'journey']) {
      const directory = join(dist, route);
      const shadowed = existsSync(directory) ? readdirSync(directory).filter((entry) => entry !== 'index.html') : [];
      expect(shadowed, `files under /${route}/ that the rewrite shadows`).toEqual([]);
    }
  });

  test('the whole site stays far inside the host\'s file limit', () => {
    const count = (directory: string): number =>
      readdirSync(directory, { withFileTypes: true }).reduce(
        (total, entry) => total + (entry.isDirectory() ? count(join(directory, entry.name)) : 1),
        0,
      );
    expect(count(dist), 'dist holds more files than the 2,000 budget').toBeLessThanOrEqual(2_000);
  });

  test('the generated route list agrees with what is on disk', () => {
    const listPath = join(WEB_ROOT, 'artifacts', 'prerendered-routes.json');
    expect(existsSync(listPath), 'the prerenderer wrote no route list').toBe(true);
    const list = JSON.parse(readFileSync(listPath, 'utf8')) as { routes: string[] };
    const missing = list.routes.filter((path) => !entryPointExists(path));
    expect(missing, 'the prerenderer claims routes it did not emit').toEqual([]);
    expect(list.routes.length).toBeGreaterThan(10);
  });

  test('each prerendered page carries its own head rather than the generic shell', () => {
    test.skip(facts.operatorIds.length === 0, 'no operators in the release');
    const read = (path: string) =>
      readFileSync(join(dist, path.replace(/^\//, ''), 'index.html'), 'utf8');

    const search = read('/search');
    const operator = read(`/operators/${facts.operatorIds[0]}`);

    const titleOf = (html: string) => /<title>([^<]*)<\/title>/.exec(html)?.[1] ?? '';
    const canonicalOf = (html: string) => /<link rel="canonical" href="([^"]*)"/.exec(html)?.[1] ?? '';
    const descriptionOf = (html: string) => /<meta name="description" content="([^"]*)"/.exec(html)?.[1] ?? '';

    // Different pages, genuinely different metadata.
    expect(titleOf(search)).not.toBe(titleOf(operator));
    expect(descriptionOf(search)).not.toBe(descriptionOf(operator));
    expect(canonicalOf(search)).toBe('https://odivrelo.peterdsp.dev/search');
    expect(canonicalOf(operator)).toBe(`https://odivrelo.peterdsp.dev/operators/${facts.operatorIds[0]}`);

    // Open Graph follows the page, not the shell.
    expect(/<meta property="og:title" content="([^"]*)"/.exec(operator)?.[1]).toContain('Odivrelo');
    expect(/<meta property="og:url" content="([^"]*)"/.exec(operator)?.[1]).toContain(facts.operatorIds[0]!);

    // And the entity page carries structured data for that entity.
    expect(operator).toContain('application/ld+json');
  });

  test('every shipped page carries a robots directive', () => {
    /*
     * A crawler or link-preview fetcher that never runs JavaScript must still see
     * a directive. robots.txt governs crawling, but a disallowed URL can be indexed
     * from an external link, which is exactly what this guards against.
     */
    const files = [
      'index.html',
      '404.html',
      'search/index.html',
      'operators/index.html',
      'coverage/index.html',
      'stations/index.html',
    ];
    for (const file of files) {
      const html = readFileSync(join(dist, file), 'utf8');
      const robots = /<meta name="robots" content="([^"]*)"/.exec(html)?.[1];
      expect(robots, `${file} ships without a robots directive`).toBeTruthy();
      expect(['index, follow', 'noindex, nofollow'], `${file} ships a directive nobody chose`).toContain(robots);
    }
  });

  test('only the pages the product publishes are indexable, and only on real data', () => {
    /*
     * The shell, the 404 and the search tool are not content: they are indexable
     * never, whatever the data says. The operator, station and coverage pages are
     * the whole reason the product publishes anything, so they follow the release.
     * A demonstration release makes them deny too, which is what kept the invented
     * Aloria region out of every index.
     */
    const list = JSON.parse(
      readFileSync(join(WEB_ROOT, 'artifacts', 'prerendered-routes.json'), 'utf8'),
    ) as { indexable: boolean };
    const expected = list.indexable ? 'index, follow' : 'noindex, nofollow';

    const directive = (file: string) =>
      /<meta name="robots" content="([^"]*)"/.exec(readFileSync(join(dist, file), 'utf8'))?.[1];

    for (const file of ['index.html', '404.html', 'search/index.html']) {
      expect(directive(file), `${file} is not content and must never be indexable`).toBe('noindex, nofollow');
    }
    for (const file of ['operators/index.html', 'stations/index.html', 'coverage/index.html']) {
      expect(directive(file), `${file} disagrees with the release's data mode`).toBe(expected);
    }
  });

  test('404.html is kept, because an unknown path really is a 404', () => {
    expect(existsSync(join(dist, '404.html'))).toBe(true);
    const notFound = readFileSync(join(dist, '404.html'), 'utf8');
    // It boots the same app, so the reader gets a useful screen rather than a
    // bare server error page.
    expect(notFound).toContain('id="root"');
    expect(notFound).toContain('/assets/');
  });

  test('the PWA start URL has a real entry point', () => {
    const manifest = JSON.parse(readFileSync(join(dist, 'manifest.webmanifest'), 'utf8')) as { start_url: string };
    const start = manifest.start_url.split('?')[0]!;
    expect(entryPointExists(start), `the PWA start_url ${manifest.start_url} would return 404`).toBe(true);
  });

  test('every prerendered page is served with a 200', async ({ request }) => {
    // Through the preview server, which serves a directory's index.html.
    for (const path of ['/search', '/settings', '/operators', '/coverage', '/stations', '/offline', '/wallet']) {
      const response = await request.get(path);
      expect(response.status(), `${path} did not return 200`).toBe(200);
    }
    if (facts.operatorIds[0]) {
      const response = await request.get(`/operators/${facts.operatorIds[0]}`);
      expect(response.status()).toBe(200);
    }
    if (facts.journeyId) {
      const response = await request.get(`/journey/${facts.journeyId}`);
      expect(response.status()).toBe(200);
    }
  });
});
