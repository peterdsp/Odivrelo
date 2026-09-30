import { expect, test, type Page } from '@playwright/test';
import { expectNoAxeViolations, readReleaseFacts, shoot, skipOnboarding, tabReachesLinks, waitForApp } from './support';

const facts = readReleaseFacts();

/**
 * The whole task a passenger actually comes here to do:
 * search, pick a date, read a result, open the boarding detail, take the
 * purchase or contact action, and come back with the screen exactly as it was.
 */

test.skip(facts.workingQuery === null, 'the built release carries no journey to search for');

const query = facts.workingQuery!;

/**
 * Drives the place picker the way a reader does: type part of a name, then choose
 * the suggestion.
 *
 * The listbox is found through the combobox's own `aria-controls`, which is both
 * how assistive technology finds it and the only way to tell the origin field's
 * list from the destination field's apart.
 */
async function pickPlace(page: Page, fieldLabel: RegExp, placeName: string): Promise<string> {
  const input = page.getByRole('combobox', { name: fieldLabel });
  await input.click();
  const listId = await input.getAttribute('aria-controls');
  expect(listId, 'the combobox does not name its listbox').toBeTruthy();
  const listbox = page.locator(`#${listId}`);

  // Typing a distinctive fragment of the name, as a passenger would.
  await input.fill(placeName.slice(0, Math.min(placeName.length, 10)));
  await expect(listbox).toBeVisible();
  const option = listbox.getByRole('option').filter({ hasText: placeName }).first();
  await expect(option).toBeVisible();
  await option.click();

  await expect(input).toHaveValue(placeName);
  await expect(listbox).toBeHidden();
  return placeName;
}

test.describe('search to boarding detail and back', () => {
  test('completes the whole flow and preserves state on the way back', async ({ page }, testInfo) => {
    await skipOnboarding(page);

    // --- Search -----------------------------------------------------------
    await page.goto('/search');
    await waitForApp(page);
    await expectNoAxeViolations(page, testInfo, 'search');
    await shoot(page, testInfo, 'search-empty');

    const origin = await pickPlace(page, /^Από$/, facts.placeNames[query.originId]!);
    const destination = await pickPlace(page, /^Προς$/, facts.placeNames[query.destinationId]!);
    expect(origin).not.toBe(destination);

    // --- Service date -----------------------------------------------------
    const dateField = page.getByLabel(/Ημερομηνία υπηρεσίας/);
    await dateField.fill(query.date);
    await expect(dateField).toHaveValue(query.date);
    // The readable form of the date is shown, not only the machine value.
    await expect(page.locator('.pv-date-field__readable')).not.toBeEmpty();

    await shoot(page, testInfo, 'search-filled');
    await page.getByRole('button', { name: 'Αναζήτηση δρομολογίων' }).click();

    // --- Results ----------------------------------------------------------
    await expect(page).toHaveURL(/\/results\?/);
    await waitForApp(page);
    const url = page.url();
    expect(url).toContain(`date=${query.date}`);

    const cards = page.locator('article.pv-journey');
    await expect(cards.first()).toBeVisible({ timeout: 15_000 });

    // Every fact the brief requires is on the face of the card.
    const first = cards.first();
    await expect(first).toContainText('Αναχώρηση');
    await expect(first).toContainText('Άφιξη');
    await expect(first).toContainText('Διάρκεια');
    await expect(first).toContainText('Μεταφορέας');
    await expect(first).toContainText('Στάσεις');
    await expect(first).toContainText('Κόμιστρο');
    // Freshness carries both a state and an age.
    await expect(first.locator('.pv-badge')).not.toHaveCount(0);

    await expectNoAxeViolations(page, testInfo, 'results');
    await shoot(page, testInfo, 'results');

    // --- Journey detail ---------------------------------------------------
    await first.getByRole('link').first().click();
    await waitForApp(page);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

    // The boarding point, the ordered stop list and the provenance are all here.
    await expect(page.getByRole('heading', { name: 'Από πού επιβιβάζεσαι' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Στάσεις' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Από πού προέρχεται αυτό' })).toBeVisible();
    await expect(page.locator('.pv-timeline__item').first()).toBeVisible();

    // Live tracking is stated to be unavailable rather than faked.
    await expect(page.getByTestId('live-unavailable')).toBeVisible();

    // And the app says plainly that it does not sell tickets.
    await expect(page.locator('main')).toContainText('δεν πουλά και δεν εκδίδει εισιτήρια');

    await expectNoAxeViolations(page, testInfo, 'journey-detail');
    await shoot(page, testInfo, 'journey-detail');

    const journeyUrl = page.url();

    // --- Purchase or contact action ---------------------------------------
    await page.getByRole('link', { name: 'Πώς αγοράζεις αυτό το εισιτήριο' }).click();
    await waitForApp(page);
    await expect(page.getByRole('heading', { level: 1, name: 'Πώς αγοράζεις αυτό το εισιτήριο' })).toBeVisible();

    // Exactly one honest outcome is offered, and the disclaimer is on the page.
    await expect(page.locator('main')).toContainText('Ο μεταφορέας είναι υπεύθυνος');
    const external = page.locator('a[target="_blank"]');
    if ((await external.count()) > 0) {
      // Every outbound link is safe and says it opens elsewhere.
      for (const link of await external.all()) {
        const rel = (await link.getAttribute('rel')) ?? '';
        expect(rel).toContain('noopener');
        expect(rel).toContain('noreferrer');
      }
    } else {
      // No online sale: the verified office details must be here instead.
      await expect(page.getByRole('heading', { name: 'Εκδοτήριο' })).toBeVisible();
    }

    await expectNoAxeViolations(page, testInfo, 'booking');
    await shoot(page, testInfo, 'booking');

    // --- Back, with state preserved ---------------------------------------
    await page.goBack();
    await waitForApp(page);
    expect(page.url()).toBe(journeyUrl);

    await page.goBack();
    await waitForApp(page);
    // The results page is exactly the one we left, query string and all.
    expect(page.url()).toBe(url);
    await expect(cards.first()).toBeVisible();
  });

  test('preserves the query, date, filters and selection across a reload and a resize', async ({ page }) => {
    await skipOnboarding(page);
    const search = new URLSearchParams({
      origin: query.originId,
      destination: query.destinationId,
      date: query.date,
      accessible: '1',
      from: '00:00',
    });
    await page.goto(`/results?${search.toString()}`);
    await waitForApp(page);
    const before = page.url();

    // A reload reconstructs the same screen from the same URL.
    await page.reload();
    await waitForApp(page);
    expect(page.url()).toBe(before);

    // So does a geometry change, in both directions.
    for (const size of [
      { width: 360, height: 780 },
      { width: 1280, height: 900 },
      { width: 900, height: 500 },
      { width: 390, height: 844 },
    ]) {
      await page.setViewportSize(size);
      await page.waitForTimeout(120);
      expect(page.url(), `the URL changed at ${size.width}x${size.height}`).toBe(before);
      await expect(page.getByRole('main')).toBeVisible();
    }
  });

  test('does not re-issue an identical search when only the window changes shape', async ({ page }) => {
    await skipOnboarding(page);
    const requests: string[] = [];
    page.on('request', (request) => {
      if (request.url().includes('/data/')) requests.push(request.url());
    });

    await page.goto(`/results?origin=${query.originId}&destination=${query.destinationId}&date=${query.date}`);
    await waitForApp(page);
    await expect(page.locator('article.pv-journey').first()).toBeVisible({ timeout: 15_000 });
    const afterLoad = requests.length;

    for (const size of [
      { width: 1280, height: 900 },
      { width: 380, height: 800 },
      { width: 1440, height: 900 },
    ]) {
      await page.setViewportSize(size);
      await page.waitForTimeout(200);
    }
    await expect(page.locator('article.pv-journey').first()).toBeVisible();

    // Packs are immutable and content-addressed, so a refetch would show up here.
    expect(requests.length, 'a resize caused the app to fetch data again').toBe(afterLoad);
  });

  test('shows a genuine two-pane layout when wide and a single pane when narrow', async ({ page }, testInfo) => {
    await skipOnboarding(page);
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto(`/results?origin=${query.originId}&destination=${query.destinationId}&date=${query.date}`);
    await waitForApp(page);
    await expect(page.locator('article.pv-journey').first()).toBeVisible({ timeout: 15_000 });

    const pane = page.locator('.pv-two-pane');
    await expect(pane).toHaveAttribute('data-mode', 'two');

    // Selecting a journey opens the second pane without leaving the page.
    await page.locator('.pv-results__selectable').first().getByRole('button', { name: 'Λεπτομέρειες δρομολογίου' }).click();
    await expect(page.locator('.pv-two-pane__detail')).toBeVisible();
    // The list is still there beside it.
    await expect(page.locator('.pv-two-pane__list article.pv-journey').first()).toBeVisible();
    const selected = page.url();
    expect(selected).toContain('journey=');
    await shoot(page, testInfo, 'results-two-pane');
    await expectNoAxeViolations(page, testInfo, 'results-two-pane');

    // Narrowing hands the whole width to the detail, and the selection survives.
    await page.setViewportSize({ width: 380, height: 800 });
    await page.waitForTimeout(200);
    await expect(pane).toHaveAttribute('data-mode', 'one');
    await expect(page.locator('.pv-two-pane__detail')).toBeVisible();
    expect(page.url()).toBe(selected);
    await shoot(page, testInfo, 'results-one-pane-detail');

    // And the list is still reachable from there.
    await page.locator('.pv-two-pane__detail-bar').getByRole('button').click();
    await expect(page.locator('article.pv-journey').first()).toBeVisible();
    expect(page.url()).not.toContain('journey=');
  });

  test('remains usable at 200 per cent text size', async ({ page }, testInfo) => {
    await skipOnboarding(page);
    // Doubling the root font size is what a reader's browser text-size setting
    // does, and it is the case a pixel-based layout fails.
    await page.addInitScript(() => {
      document.addEventListener('DOMContentLoaded', () => {
        document.documentElement.style.fontSize = '32px';
      });
    });
    await page.goto(`/results?origin=${query.originId}&destination=${query.destinationId}&date=${query.date}`);
    await waitForApp(page);
    await expect(page.locator('article.pv-journey').first()).toBeVisible({ timeout: 15_000 });

    // Nothing overflows horizontally, which is the WCAG 1.4.10 reflow failure.
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow, 'the page scrolls horizontally at 200% text').toBeLessThanOrEqual(1);

    await expectNoAxeViolations(page, testInfo, 'results-200-percent');
    await shoot(page, testInfo, 'results-200-percent-text');
  });

  test('is fully operable from the keyboard', async ({ page }) => {
    await skipOnboarding(page);
    await page.goto('/search');
    await waitForApp(page);

    // The skip link is the first thing in the document and it targets main.
    const skip = page.locator('.pv-skip-link');
    await expect(skip).toHaveAttribute('href', '#main');
    await expect(skip).toHaveCount(1);

    /*
     * Safari and WebKit leave links out of the sequential focus order unless the
     * reader turns on Full Keyboard Access, so the Tab assertion runs only where
     * the engine supports it. The rest of this test, which is the part the app
     * owns, runs everywhere.
     */
    if (tabReachesLinks(page)) {
      await page.keyboard.press('Tab');
      await expect(skip).toBeFocused();
    }

    // Every focused element is visible, which is WCAG 2.4.11.
    for (let i = 0; i < 25; i += 1) {
      await page.keyboard.press('Tab');
      const hidden = await page.evaluate(() => {
        const active = document.activeElement as HTMLElement | null;
        if (!active || active === document.body) return false;
        const rect = active.getBoundingClientRect();
        const style = getComputedStyle(active);
        return style.visibility === 'hidden' || style.display === 'none' || (rect.width === 0 && rect.height === 0);
      });
      expect(hidden, 'focus landed on something invisible').toBe(false);
    }
  });

  test('operates the place picker entirely from the keyboard', async ({ page }) => {
    await skipOnboarding(page);
    await page.goto('/search');
    await waitForApp(page);

    const input = page.getByRole('combobox', { name: /^Από$/ });
    await input.click();
    const listbox = page.locator(`#${await input.getAttribute('aria-controls')}`);
    await input.fill('Αλόρια');
    await page.keyboard.press('ArrowDown');
    await expect(listbox).toBeVisible();
    // The active option is announced through aria-activedescendant.
    await expect(input).toHaveAttribute('aria-activedescendant', /.+/);
    await page.keyboard.press('ArrowDown');
    await page.keyboard.press('Enter');
    await expect(input).not.toHaveValue('');
    // Escape closes the list rather than clearing the field.
    const chosen = await input.inputValue();
    await page.keyboard.press('ArrowDown');
    await page.keyboard.press('Escape');
    await expect(listbox).toBeHidden();
    await expect(input).toHaveValue(chosen);
  });
});

test.describe('the map is never the only path', () => {
  test('shows every journey fact in text with the map closed', async ({ page }, testInfo) => {
    await skipOnboarding(page);
    await page.goto(`/journey/${encodeURIComponent(facts.journeyId!)}?date=${query.date}`);
    await waitForApp(page);

    // No map has been requested, so the map bundle must not have been fetched.
    const mapRequests: string[] = [];
    page.on('request', (request) => {
      if (/maplibre/.test(request.url())) mapRequests.push(request.url());
    });
    await page.waitForTimeout(400);
    expect(mapRequests, 'the map bundle loaded on a route that never showed a map').toEqual([]);

    // Yet the ordered stop list, with pickup and drop-off rules, is fully present.
    const stops = page.locator('.pv-timeline__item');
    expect(await stops.count()).toBeGreaterThan(0);
    await expect(stops.first()).toContainText(/επιβιβαστείς|επιβίβαση/);

    await shoot(page, testInfo, 'journey-detail-no-map');
  });

  test('loads the map only when it is asked for, and says it ships no imagery', async ({ page, browserName }, testInfo) => {
    await skipOnboarding(page);
    await page.goto(`/journey/${encodeURIComponent(facts.journeyId!)}?date=${query.date}`);
    await waitForApp(page);

    await page.getByRole('button', { name: 'Εμφάνιση χάρτη' }).click();
    const map = page.locator('#journey-map');
    await expect(map).toContainText(/υπόβαθρο χάρτη|δεν φορτώθηκε/, { timeout: 20_000 });
    // Headless Firefox on a GPU-less CI runner cannot always create the WebGL
    // context the map needs, and the app then shows its text fallback, which
    // the test above covers. Only Firefox is excused: Chromium and WebKit must
    // still render the map.
    test.skip(
      browserName === 'firefox' && (await map.textContent())?.includes('δεν φορτώθηκε') === true,
      'this Firefox has no usable WebGL, so the map showed its fallback',
    );
    // The panel appears, and it states that no background imagery is loaded.
    await expect(page.locator('#journey-map')).toContainText('υπόβαθρο χάρτη', { timeout: 20_000 });
    await expect(page.getByRole('group', { name: 'Χειριστήρια χάρτη' })).toBeVisible();

    await expectNoAxeViolations(page, testInfo, 'journey-with-map');
    await shoot(page, testInfo, 'journey-with-map');
  });
});
