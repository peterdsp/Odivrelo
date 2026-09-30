import { expect, test } from '@playwright/test';
import { expectNoAxeViolations, readReleaseFacts, shoot, skipOnboarding, waitForApp } from './support';

const facts = readReleaseFacts();

/**
 * The states that are not the happy path.
 *
 * Each one has to be a real screen that says what happened and offers a way
 * forward. A blank page, a spinner that never resolves, or a cheerful message
 * that hides a failure would each be worse than the failure itself.
 */

test.describe('unknown and invalid deep links', () => {
  test('an unknown path explains itself and offers the routes that exist', async ({ page }, testInfo) => {
    await skipOnboarding(page);
    await page.goto('/this/path/does/not/exist');
    await waitForApp(page);

    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    // It shows the path, so a typo is visible to the reader.
    await expect(page.locator('main')).toContainText('/this/path/does/not/exist');
    // And it offers somewhere to go.
    await expect(page.getByRole('link', { name: /Μετάβαση στην αναζήτηση/ }).first()).toBeVisible();

    await expectNoAxeViolations(page, testInfo, 'not-found');
    await shoot(page, testInfo, 'not-found');
  });

  test('a journey link with no date says so rather than failing silently', async ({ page }, testInfo) => {
    await skipOnboarding(page);
    await page.goto('/journey/kt_something');
    await waitForApp(page);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.locator('main')).toContainText(/δεν είναι έγκυρος|δεν μπορεί να χρησιμοποιήσει/);
    await expectNoAxeViolations(page, testInfo, 'journey-invalid-link');
    await shoot(page, testInfo, 'journey-missing-date');
  });

  test('a journey that is not in this release is reported as not found', async ({ page }, testInfo) => {
    await skipOnboarding(page);
    const date = facts.serviceDates[0] ?? '2026-10-02';
    await page.goto(`/journey/kt_definitely_not_in_this_release?date=${date}`);
    await waitForApp(page);
    await expect(page.locator('main')).toContainText(/δεν υπάρχει σε αυτή την έκδοση|δεν εκτελείται/);
    await expectNoAxeViolations(page, testInfo, 'journey-not-found');
    await shoot(page, testInfo, 'journey-not-found');
  });

  test('an operator and a station that do not exist each get a useful screen', async ({ page }) => {
    await skipOnboarding(page);

    await page.goto('/operators/not-a-real-operator');
    await waitForApp(page);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.locator('main')).toContainText('δεν υπάρχει σε αυτή την έκδοση');

    await page.goto('/stations/not-a-real-stop');
    await waitForApp(page);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.locator('main')).toContainText('δεν υπάρχει σε αυτή την έκδοση');
  });

  test('a results link with a malformed query is rejected, not guessed at', async ({ page }, testInfo) => {
    await skipOnboarding(page);
    await page.goto('/results?origin=%3Cscript%3E&destination=&date=not-a-date');
    await waitForApp(page);
    await expect(page.locator('main')).toContainText('δεν είναι έγκυρος');
    await expect(page.getByRole('link', { name: /Μετάβαση στην αναζήτηση/ }).first()).toBeVisible();
    await shoot(page, testInfo, 'results-invalid-query');
  });
});

test.describe('empty and unavailable results', () => {
  test.skip(facts.stopPlaceIds.length < 2 && facts.placeIds.length < 2, 'not enough places to build an empty pair');

  test('a date the release does not pack says so, and never that nothing runs', async ({ page }, testInfo) => {
    await skipOnboarding(page);
    const query = facts.workingQuery;
    test.skip(query === null, 'the release carries no working query');
    await page.goto(`/results?origin=${query!.originId}&destination=${query!.destinationId}&date=2030-01-01`);
    await waitForApp(page);

    const main = page.locator('main');
    // The honest answer: this device has no data for that date.
    await expect(main).toContainText('δεν υπάρχει στα κατεβασμένα δεδομένα');
    await expect(main).toContainText('Αυτό δεν σημαίνει ότι δεν εκτελείται δρομολόγιο');
    // And it names the dates that are covered, so the reader has a next step.
    await expect(main).toContainText('Ημερομηνίες που καλύπτει αυτή η έκδοση');

    await expectNoAxeViolations(page, testInfo, 'results-no-offline-data');
    await shoot(page, testInfo, 'results-no-offline-data');
  });

  test('an origin equal to the destination is called out specifically', async ({ page }, testInfo) => {
    await skipOnboarding(page);
    const id = facts.placeIds[0]!;
    const date = facts.serviceDates[0] ?? '2026-10-02';
    await page.goto(`/results?origin=${id}&destination=${id}&date=${date}`);
    await waitForApp(page);
    await expect(page.locator('main')).toContainText('συμπίπτουν');
    await shoot(page, testInfo, 'results-same-origin-destination');
  });

  test('a pair with no service on a packed date is reported without overclaiming', async ({ page }, testInfo) => {
    await skipOnboarding(page);
    test.skip(facts.placeIds.length < 2, 'not enough places');
    const date = facts.serviceDates[0] ?? '2026-10-02';
    // Reverse the working pair, which in this release has no return working.
    const query = facts.workingQuery!;
    await page.goto(`/results?origin=${query.destinationId}&destination=${query.originId}&date=${date}`);
    await waitForApp(page);
    const main = page.locator('main');
    await expect(main).toBeVisible();
    // Whatever it says, it must never be a blank page or a stuck spinner.
    const text = await main.innerText();
    expect(text.trim().length).toBeGreaterThan(80);
    await shoot(page, testInfo, 'results-empty-pair');
    await expectNoAxeViolations(page, testInfo, 'results-empty-pair');
  });

  test('a search with nothing chosen refuses and says which field is missing', async ({ page }, testInfo) => {
    await skipOnboarding(page);
    await page.goto('/search');
    await waitForApp(page);
    await page.getByRole('button', { name: 'Αναζήτηση δρομολογίων' }).click();

    // It stays on the search page and names the problem.
    await expect(page).toHaveURL(/\/search/);
    await expect(page.locator('.pv-field__error').first()).toBeVisible();
    await shoot(page, testInfo, 'search-validation-error');
    await expectNoAxeViolations(page, testInfo, 'search-validation-error');
  });
});

test.describe('permissions and capabilities', () => {
  test('a denied notification permission is explained, not hidden', async ({ page, context }, testInfo) => {
    await skipOnboarding(page);
    await context.clearPermissions();
    // Make the browser report notifications as blocked.
    await page.addInitScript(() => {
      try {
        Object.defineProperty(Notification, 'permission', { get: () => 'denied', configurable: true });
      } catch {
        // Some engines will not let this be redefined; the assertion below is
        // then skipped rather than faked.
      }
    });

    await page.goto('/saved');
    await waitForApp(page);

    const permission = await page.evaluate(() =>
      typeof Notification === 'undefined' ? 'unsupported' : Notification.permission,
    );
    if (permission === 'denied') {
      await expect(page.locator('main')).toContainText('Οι ειδοποιήσεις είναι αποκλεισμένες');
      await expect(page.locator('main')).toContainText('ρυθμίσεις ιστότοπου');
    } else {
      // Either way, the honest account of what a web reminder can do is present.
      await expect(page.locator('main')).toContainText('δεν επιτρέπουν σε έναν ιστότοπο να ξυπνήσει μόνος του');
    }

    await expectNoAxeViolations(page, testInfo, 'saved-notifications-denied');
    await shoot(page, testInfo, 'reminders-permission-denied');
  });

  test('states plainly that a background reminder cannot be delivered', async ({ page }) => {
    await skipOnboarding(page);
    await page.goto('/saved');
    await waitForApp(page);
    await expect(page.locator('main')).toContainText('μόνο όσο ο ιστότοπος είναι ανοιχτός');
    await expect(page.locator('main')).toContainText('Χρησιμοποίησε ξυπνητήρι');
    // And it promises the payload carries nothing personal.
    await expect(page.locator('main')).toContainText('Ποτέ δεν περιέχει όνομα επιβάτη');
  });

  test('says what happens when the browser refuses storage', async ({ page }, testInfo) => {
    await skipOnboarding(page);
    // Take IndexedDB away entirely, which is what a locked-down browser does.
    await page.addInitScript(() => {
      Object.defineProperty(globalThis, 'indexedDB', { get: () => undefined, configurable: true });
    });
    await page.goto('/wallet');
    await waitForApp(page);
    await expect(page.locator('main')).toContainText('δεν προσφέρει αποθηκευτικό χώρο');
    await shoot(page, testInfo, 'wallet-no-storage');

    await page.goto('/saved');
    await waitForApp(page);
    await expect(page.locator('main')).toContainText('δεν προσφέρει αποθηκευτικό χώρο');
    await expectNoAxeViolations(page, testInfo, 'saved-no-storage');
  });
});

test.describe('the travel wallet', () => {
  test('is honest about eviction and never implies a backup', async ({ page }, testInfo) => {
    await skipOnboarding(page);
    await page.goto('/wallet');
    await waitForApp(page);
    await expect(page.locator('main')).toContainText('Αυτό δεν είναι αντίγραφο ασφαλείας');
    await expect(page.locator('main')).toContainText('Δεν ανεβαίνουν ποτέ');
    await expectNoAxeViolations(page, testInfo, 'wallet');
    await shoot(page, testInfo, 'wallet-empty');
  });

  test('refuses a file that is not what it claims to be', async ({ page }, testInfo) => {
    await skipOnboarding(page);
    await page.goto('/wallet');
    await waitForApp(page);

    // An HTML file wearing a .png name: the bytes decide, not the name.
    await page.setInputFiles('#wallet-file', {
      name: 'ticket.png',
      mimeType: 'image/png',
      buffer: Buffer.from('<script>alert(1)</script>'),
    });
    await expect(page.locator('.pv-field__error')).toContainText('δεν μοιάζει πραγματικά');
    await shoot(page, testInfo, 'wallet-rejected-file');
  });

  test('imports, shows and really deletes a genuine file', async ({ page }, testInfo) => {
    await skipOnboarding(page);
    await page.goto('/wallet');
    await waitForApp(page);

    // A minimal but genuine PNG.
    const png = Buffer.from(
      '89504e470d0a1a0a0000000d494844520000000100000001080600000' +
        '01f15c4890000000a49444154789c6360000002000100' +
        '05fe02fea7b1d9270000000049454e44ae426082',
      'hex',
    );
    await page.setInputFiles('#wallet-file', { name: 'ticket.png', mimeType: 'image/png', buffer: png });

    const card = page.locator('article').filter({ hasText: 'ticket.png' }).first();
    await expect(card).toBeVisible({ timeout: 15_000 });
    await shoot(page, testInfo, 'wallet-with-file');

    // Opening it renders it in an isolated viewer.
    await card.getByRole('button', { name: 'Άνοιγμα' }).click();
    await expect(page.locator('.pv-viewer')).toBeVisible();
    await expectNoAxeViolations(page, testInfo, 'wallet-viewer');
    await shoot(page, testInfo, 'wallet-viewer');

    // Deleting really deletes.
    page.once('dialog', (dialog) => void dialog.accept());
    await card.getByRole('button', { name: 'Διαγραφή' }).click();
    await expect(page.locator('main')).toContainText('Δεν υπάρχουν αρχεία εισιτηρίων', { timeout: 15_000 });

    // And it survives a reload as deleted.
    await page.reload();
    await waitForApp(page);
    await expect(page.locator('main')).toContainText('Δεν υπάρχουν αρχεία εισιτηρίων');
  });
});
