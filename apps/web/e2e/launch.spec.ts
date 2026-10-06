import { expect, test } from '@playwright/test';
import { THEME_COLORS } from '../src/styles/brandColors.generated';
import { expectDemoNotice, expectNoAxeViolations, readReleaseFacts, setLanguage, setTheme, shoot, skipOnboarding, waitForApp } from './support';

/** `#RRGGBB` from the design tokens, as getComputedStyle reports it. */
function rgb(hex: string): string {
  const value = Number.parseInt(hex.slice(1), 16);
  return `rgb(${(value >> 16) & 255}, ${(value >> 8) & 255}, ${value & 255})`;
}

const facts = readReleaseFacts();

/**
 * Cold launch, in all three languages and both themes.
 *
 * "Cold" means a browser with no storage at all: no language preference, no
 * onboarding flag, no packs. That is the state a first-time reader arrives in,
 * and it is the one most likely to be broken by an assumption about stored state.
 */

test.describe('cold launch', () => {
  for (const [language, heading, startLabel] of [
    ['el', 'Ταξίδεψε με βεβαιότητα', 'Ξεκίνα την αναζήτηση'],
    ['en', 'Travel with certainty', 'Start searching'],
    ['sq', 'Udhëto me siguri', 'Fillo kërkimin'],
  ] as const) {
    test(`shows the first-launch page in ${language} with no account and no permission prompt`, async ({ page }, testInfo) => {
      // Fail the test if anything asks for a permission on first load.
      const permissionRequests: string[] = [];
      await page.addInitScript(() => {
        const marker: string[] = [];
        (globalThis as unknown as { __pvPermissions: string[] }).__pvPermissions = marker;
        if (typeof Notification !== 'undefined') {
          const original = Notification.requestPermission.bind(Notification);
          Notification.requestPermission = async (...args: unknown[]) => {
            marker.push('notifications');
            return original(...(args as []));
          };
        }
        if (navigator.geolocation) {
          const original = navigator.geolocation.getCurrentPosition.bind(navigator.geolocation);
          navigator.geolocation.getCurrentPosition = (...args: Parameters<typeof original>) => {
            marker.push('geolocation');
            return original(...args);
          };
        }
      });
      await setLanguage(page, language);

      await page.goto('/');
      await waitForApp(page);

      await expect(page.getByRole('heading', { level: 1 })).toContainText(heading);
      await expect(page).toHaveURL(/\/welcome$/);
      expect(await page.locator('html').getAttribute('lang')).toBe(language);

      // The honest statement of what it does and does not do is on this page.
      await expect(page.getByRole('heading', { level: 2 })).toBeTruthy();
      const body = await page.locator('main').innerText();
      expect(body.length).toBeGreaterThan(200);

      permissionRequests.push(
        ...(await page.evaluate(() => (globalThis as unknown as { __pvPermissions: string[] }).__pvPermissions ?? [])),
      );
      expect(permissionRequests, 'the first launch asked for a permission').toEqual([]);

      await expectDemoNotice(page);
      await expectNoAxeViolations(page, testInfo, `welcome-${language}`);
      await shoot(page, testInfo, `welcome-${language}`);

      // And it leads straight into search, with no account step in between.
      await page.getByRole('button', { name: startLabel }).click();
      await expect(page).toHaveURL(/\/search$/);
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    });
  }

  for (const theme of ['light', 'dark'] as const) {
    test(`renders search in the ${theme} theme`, async ({ page }, testInfo) => {
      await skipOnboarding(page);
      await setTheme(page, theme);
      await page.goto('/search');
      await waitForApp(page);
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

      // The theme has actually taken effect, rather than the class merely being set.
      const background = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
      expect(background).toBe(rgb(THEME_COLORS[theme].background));

      await expectNoAxeViolations(page, testInfo, `search-${theme}`);
      await shoot(page, testInfo, `search-${theme}`);
    });
  }

  test('works with JavaScript disabled to the extent of saying so', async ({ browser }, testInfo) => {
    const context = await browser.newContext({ javaScriptEnabled: false });
    const page = await context.newPage();
    await page.goto('/');
    // The shell says what has happened in all three languages rather than
    // presenting a blank white page.
    await expect(page.locator('#od-boot')).toBeVisible();
    await expect(page.locator('#od-boot')).toContainText('JavaScript');
    await shoot(page, testInfo, 'no-javascript');
    await context.close();
  });
});

test.describe('the demonstration notice', () => {
  test('is on every route and cannot be dismissed', async ({ page }) => {
    await skipOnboarding(page);
    const routes = ['/search', '/operators', '/stations', '/saved', '/offline', '/wallet', '/settings', '/coverage', '/licences'];
    for (const route of routes) {
      await page.goto(route);
      await waitForApp(page);
      await expectDemoNotice(page);
      const banner = page.getByTestId('demo-banner');
      // Nothing inside it closes it.
      const closers = banner.getByRole('button', { name: /κλείσιμο|απόρριψη|close|dismiss/i });
      await expect(closers).toHaveCount(0);
      // It says the region is invented and does not exist.
      await expect(banner).toContainText(/Αλόρια/);
      await expect(banner).toContainText(/δεν υπάρχει/);
    }
  });

  test('keeps the whole site out of search indexes while the data is invented', async ({ page, request }) => {
    await skipOnboarding(page);
    await page.goto('/operators');
    await waitForApp(page);
    await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', 'noindex, nofollow');

    const robots = await request.get('/robots.txt');
    expect(robots.ok()).toBe(true);
    expect(await robots.text()).toContain('Disallow: /');

    // No sitemap is published while the data is invented. A static host with an
    // SPA fallback answers any unknown path with the app shell, so the check is
    // that nothing sitemap-shaped comes back rather than that the status is 404.
    const sitemap = await request.get('/sitemap.xml');
    const sitemapBody = sitemap.ok() ? await sitemap.text() : '';
    expect(sitemapBody, 'a sitemap was published for demonstration data').not.toContain('<urlset');
  });
});

test.describe('the static host files', () => {
  test('serves the SPA fallback, the CNAME and the manifest', async ({ request }) => {
    const cname = await request.get('/CNAME');
    expect(cname.ok()).toBe(true);
    expect((await cname.text()).trim()).toBe('odivrelo.peterdsp.dev');

    const webmanifest = await request.get('/manifest.webmanifest');
    expect(webmanifest.ok()).toBe(true);
    const manifest = JSON.parse(await webmanifest.text()) as { name: string; icons: { sizes: string; purpose?: string }[] };
    expect(manifest.name).toBe('Odivrelo');
    const purposes = manifest.icons.map((icon) => icon.purpose ?? 'any');
    expect(purposes, 'the web manifest has no maskable icon').toContain('maskable');
    expect(manifest.icons.map((icon) => icon.sizes)).toContain('512x512');

    const headers = await request.get('/_headers');
    expect(headers.ok()).toBe(true);
    const text = await headers.text();
    for (const header of ['Content-Security-Policy', 'X-Content-Type-Options', 'Referrer-Policy', 'Permissions-Policy']) {
      expect(text, `_headers does not document ${header}`).toContain(header);
    }
  });

  test('applies the content security policy from the document itself', async ({ page }) => {
    await page.goto('/search');
    const csp = await page.locator('meta[http-equiv="Content-Security-Policy"]').getAttribute('content');
    expect(csp).toBeTruthy();
    expect(csp).toContain("default-src 'self'");
    expect(csp).toContain("object-src 'none'");
    // OpenFreeMap may be connected to for the basemap, while Unsplash is only
    // permitted as an editorial image source. Every other https origin remains
    // forbidden.
    const allowedThirdParty = 'https://tiles.openfreemap.org';
    const withoutAllowed = csp!
      .split(allowedThirdParty).join('')
      .split('https://images.unsplash.com').join('');
    expect(withoutAllowed).not.toMatch(/https:\/\/(?!odivrelo)/);
    expect(csp).toContain(`connect-src 'self' ${allowedThirdParty}`);
    expect(csp).toContain(`img-src 'self' blob: data: ${allowedThirdParty} https://images.unsplash.com`);
  });

  test('serves the release packs the static source reads', async ({ request }) => {
    const manifest = await request.get('/data/manifest.json');
    expect(manifest.ok()).toBe(true);
    const parsed = JSON.parse(await manifest.text()) as { releaseId: string; files: Record<string, { path: string }> };
    expect(parsed.releaseId).toBe(facts.releaseId);
    for (const name of ['meta', 'places', 'operators', 'stops', 'coverage', 'sources']) {
      expect(Object.keys(parsed.files), `the published release has no ${name} pack`).toContain(name);
      const pack = await request.get(`/data/${parsed.files[name]!.path}`);
      expect(pack.ok(), `${name} pack is not served`).toBe(true);
    }
  });

  test('makes no request to any third party', async ({ page, baseURL }) => {
    const ownHost = new URL(baseURL ?? 'http://127.0.0.1:4173').host;
    const foreign: string[] = [];
    page.on('request', (request) => {
      const url = new URL(request.url());
      if (url.protocol === 'data:' || url.protocol === 'blob:') return;
      if (url.host !== ownHost) foreign.push(request.url());
    });
    await skipOnboarding(page);
    await page.goto('/search');
    await waitForApp(page);
    await page.goto('/coverage');
    await waitForApp(page);
    await page.goto('/settings');
    await waitForApp(page);
    expect(foreign, 'the app contacted a third party').toEqual([]);
  });
});
