import { expect, test } from '@playwright/test';
import { expectNoAxeViolations, readReleaseFacts, setTheme, shoot, skipOnboarding, tabReachesLinks, waitForApp } from './support';

const facts = readReleaseFacts();

/**
 * An axe-core scan on every route, in both themes, at whichever width the
 * project is running.
 *
 * Accessibility is a stated release gate, so this is a gate: zero violations, no
 * excluded regions and no disabled rules. A route that cannot pass is a route
 * that is not finished.
 */

interface RouteUnderTest {
  readonly name: string;
  readonly path: string;
}

function routes(): RouteUnderTest[] {
  const date = facts.serviceDates[0] ?? '2026-10-02';
  const list: RouteUnderTest[] = [
    { name: 'welcome', path: '/welcome' },
    { name: 'search', path: '/search' },
    { name: 'operators', path: '/operators' },
    { name: 'stations', path: '/stations' },
    { name: 'saved', path: '/saved' },
    { name: 'offline', path: '/offline' },
    { name: 'wallet', path: '/wallet' },
    { name: 'settings', path: '/settings' },
    { name: 'coverage', path: '/coverage' },
    { name: 'licences', path: '/licences' },
    { name: 'not-found', path: '/no-such-page' },
  ];
  if (facts.operatorIds[0]) list.push({ name: 'operator-detail', path: `/operators/${facts.operatorIds[0]}` });
  if (facts.placeIds[0]) list.push({ name: 'station-detail', path: `/stations/${facts.placeIds[0]}?date=${date}` });
  if (facts.workingQuery) {
    const q = facts.workingQuery;
    list.push({ name: 'results', path: `/results?origin=${q.originId}&destination=${q.destinationId}&date=${q.date}` });
  }
  if (facts.journeyId && facts.workingQuery) {
    list.push({ name: 'journey', path: `/journey/${encodeURIComponent(facts.journeyId)}?date=${facts.workingQuery.date}` });
    list.push({
      name: 'booking',
      path: `/journey/${encodeURIComponent(facts.journeyId)}/booking?date=${facts.workingQuery.date}`,
    });
  }
  return list;
}

for (const theme of ['light', 'dark'] as const) {
  test.describe(`axe, ${theme} theme`, () => {
    for (const route of routes()) {
      test(`${route.name} has no accessibility violations`, async ({ page }, testInfo) => {
        await skipOnboarding(page);
        await setTheme(page, theme);
        await page.goto(route.path);
        await waitForApp(page);
        // Let any data-dependent content settle, so the scan sees the real page
        // rather than a loading state.
        await page.waitForTimeout(600);
        await expectNoAxeViolations(page, testInfo, `${route.name}-${theme}`);
      });
    }
  });
}

test.describe('structure', () => {
  test('every route has exactly one h1 and a correct heading order', async ({ page }) => {
    await skipOnboarding(page);
    for (const route of routes()) {
      await page.goto(route.path);
      await waitForApp(page);
      await page.waitForTimeout(300);

      const h1Count = await page.getByRole('heading', { level: 1 }).count();
      expect(h1Count, `${route.name} has ${h1Count} level-1 headings`).toBe(1);

      const levels = await page.evaluate(() =>
        [...document.querySelectorAll('main h1, main h2, main h3, main h4, main h5, main h6')].map((h) =>
          Number(h.tagName.slice(1)),
        ),
      );
      let previous = levels[0] ?? 1;
      for (const level of levels.slice(1)) {
        expect(level - previous, `${route.name} jumped from h${previous} to h${level}`).toBeLessThanOrEqual(1);
        previous = level;
      }
    }
  });

  test('every route provides the same landmarks', async ({ page }) => {
    await skipOnboarding(page);
    for (const route of routes()) {
      await page.goto(route.path);
      await waitForApp(page);
      await expect(page.getByRole('banner'), route.name).toBeVisible();
      await expect(page.getByRole('main'), route.name).toBeVisible();
      await expect(page.getByRole('contentinfo'), route.name).toBeVisible();

      // Every navigation region has a unique accessible name.
      const names = await page.evaluate(() =>
        [...document.querySelectorAll('nav')].map((nav) => nav.getAttribute('aria-label') ?? ''),
      );
      expect(names.every((name) => name.length > 0), `${route.name} has an unnamed nav`).toBe(true);
      expect(new Set(names).size, `${route.name} has two navs with the same name`).toBe(names.length);
    }
  });

  test('the skip link is first in the document and targets the main landmark', async ({ page }) => {
    await skipOnboarding(page);
    await page.goto('/search');
    await waitForApp(page);

    const skip = page.locator('.od-skip-link');
    await expect(skip).toHaveCount(1);
    await expect(skip).toHaveAttribute('href', '#main');
    await expect(page.locator('#main')).toHaveCount(1);

    // It is genuinely the first focusable thing, which is what makes it a skip link.
    const isFirst = await page.evaluate(() => {
      const candidates = document.querySelectorAll('a[href], button, input, select, textarea, [tabindex]');
      return candidates[0]?.classList.contains('od-skip-link') ?? false;
    });
    expect(isFirst, 'the skip link is not the first focusable element').toBe(true);

    // It is hidden until focused, and focusing it brings it on screen.
    await skip.focus();
    await expect(skip).toBeFocused();
    await expect(skip).toBeInViewport();
  });

  test('the skip link is the first Tab stop [skipped on WebKit: Safari omits links from the Tab order]', async ({
    page,
  }) => {
    test.skip(
      !tabReachesLinks(page),
      'Safari and WebKit leave links out of the sequential focus order unless Full Keyboard Access is on. That is a browser setting, not something this app can change.',
    );
    await skipOnboarding(page);
    await page.goto('/search');
    await waitForApp(page);

    await page.keyboard.press('Tab');
    await expect(page.locator('.od-skip-link')).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/#main$/);
  });

  test('every interactive control on a page is reachable and operable by keyboard', async ({ page }) => {
    /*
     * The contract the product owns, which holds on every engine: everything
     * interactive is rendered, carries an accessible name, and is the kind of
     * element a browser puts in the focus order.
     *
     * Two deliberate distinctions. Content inside a closed `<details>` is not
     * rendered, so it is out of scope until the disclosure is opened. And a link's
     * focusability is asserted through its `href` rather than by calling `focus()`:
     * Safari refuses programmatic focus on links unless Full Keyboard Access is on,
     * so calling it would test the browser's setting rather than the markup.
     */
    await skipOnboarding(page);
    for (const path of ['/search', '/offline', '/settings']) {
      await page.goto(path);
      await waitForApp(page);

      const problems = await page.evaluate(() => {
        const selector =
          'a[href], button, input:not([type="hidden"]), select, textarea, summary, [tabindex]:not([tabindex="-1"])';
        const found: string[] = [];
        for (const element of document.querySelectorAll<HTMLElement>(selector)) {
          // Not rendered, or inside a closed disclosure. Engines differ in whether
          // closed `<details>` content reports client rects, so the disclosure is
          // checked explicitly rather than inferred from layout.
          if (element.getClientRects().length === 0) continue;
          const closedDisclosure = element.closest('details:not([open])');
          if (closedDisclosure && !element.closest('summary')) continue;

          const describe = `${element.tagName.toLowerCase()}.${element.className || '(no class)'} "${(element.textContent ?? '').trim().slice(0, 40)}"`;

          const name =
            element.getAttribute('aria-label') ??
            (element.getAttribute('aria-labelledby')
              ? (document.getElementById(element.getAttribute('aria-labelledby')!)?.textContent ?? '')
              : null) ??
            (element.id ? (document.querySelector(`label[for="${CSS.escape(element.id)}"]`)?.textContent ?? '') : '') ??
            '';
          const ownText = (element.textContent ?? '').trim();
          // A label that wraps the control names it, which is how the language and
          // theme radio groups are written.
          const wrappingLabel = (element.closest('label')?.textContent ?? '').trim();
          const hasName =
            name.trim().length > 0 || ownText.length > 0 || wrappingLabel.length > 0 || Boolean(element.getAttribute('title'));
          if (!hasName) found.push(`no accessible name: ${describe}`);

          if (element.tagName === 'A') {
            const href = element.getAttribute('href') ?? '';
            if (href.length === 0) found.push(`link with no href: ${describe}`);
            continue;
          }

          element.focus();
          if (document.activeElement !== element) found.push(`cannot take focus: ${describe}`);
        }
        return found;
      });

      expect(problems, `${path} has controls that are not keyboard-operable`).toEqual([]);
    }
  });

  test('honours a reduced-motion preference', async ({ page }, testInfo) => {
    await skipOnboarding(page);
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.goto('/search');
    await waitForApp(page);
    // The motion tokens collapse to zero, so nothing animates.
    const duration = await page.evaluate(() =>
      getComputedStyle(document.documentElement).getPropertyValue('--od-motion-standard').trim(),
    );
    expect(duration).toBe('0ms');
    await shoot(page, testInfo, 'search-reduced-motion');
  });

  test('every control that does nothing explains why', async ({ page }) => {
    await skipOnboarding(page);
    await page.goto('/saved');
    await waitForApp(page);
    // An unavailable control stays focusable and carries the reason, rather than
    // being a greyed-out mystery.
    const unavailable = page.locator('[data-unavailable]');
    for (const control of await unavailable.all()) {
      await expect(control).toHaveAttribute('aria-disabled', 'true');
      const text = await control.innerText();
      expect(text.trim().length, 'an unavailable control has no label').toBeGreaterThan(0);
    }
  });

  test('text meets the contrast requirement in both themes', async ({ page }, testInfo) => {
    // axe checks contrast, so this is really a second pass with the theme forced
    // rather than inherited, which is where a token override usually breaks.
    await skipOnboarding(page);
    for (const theme of ['light', 'dark'] as const) {
      await page.emulateMedia({ colorScheme: theme });
      await page.addInitScript(
        ([value]) => {
          document.addEventListener('DOMContentLoaded', () => {
            document.documentElement.setAttribute('data-theme', value);
          });
        },
        [theme],
      );
      await page.goto('/journey/does-not-exist?date=2026-10-02');
      await waitForApp(page);
      await expectNoAxeViolations(page, testInfo, `forced-${theme}`);
    }
  });
});
