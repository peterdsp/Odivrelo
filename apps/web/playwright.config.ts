import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests run against the production build, not the dev server.
 *
 * That matters: the service worker, the code-split map chunk, the minified
 * bundle and the real `dist/data/` packs only exist in a production build, and
 * three of the scenarios in the brief (offline reload, the update flow and the
 * lazy map) are meaningless without them.
 *
 * Every project runs the same specs. Three engines, two widths each, because the
 * adaptive layout and the accessibility gates are both stated release gates and
 * neither is credible on one engine at one size.
 */

const PORT = 4173;
const BASE_URL = `http://127.0.0.1:${PORT}`;

// A narrow and a wide viewport. The narrow one is below the 48rem compact
// threshold, the wide one above the 64rem two-pane threshold, so both layouts
// are genuinely exercised rather than assumed.
const MOBILE = { width: 390, height: 844 };
const DESKTOP = { width: 1440, height: 900 };

export default defineConfig({
  testDir: './e2e',
  outputDir: './artifacts/playwright-output',
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 2 : undefined,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [
    ['list'],
    ['html', { outputFolder: './artifacts/playwright-report', open: 'never' }],
    ['json', { outputFile: './artifacts/playwright-results.json' }],
  ],
  use: {
    baseURL: BASE_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
    // Every scenario is checked in Greek by default, because Greek is the
    // default language and the one most passengers will read.
    locale: 'el-GR',
    timezoneId: 'Europe/Athens',
  },
  projects: [
    { name: 'chromium-mobile', use: { ...devices['Desktop Chrome'], viewport: MOBILE, isMobile: false, hasTouch: true } },
    { name: 'chromium-desktop', use: { ...devices['Desktop Chrome'], viewport: DESKTOP } },
    { name: 'firefox-mobile', use: { ...devices['Desktop Firefox'], viewport: MOBILE } },
    { name: 'firefox-desktop', use: { ...devices['Desktop Firefox'], viewport: DESKTOP } },
    { name: 'webkit-mobile', use: { ...devices['Desktop Safari'], viewport: MOBILE, hasTouch: true } },
    { name: 'webkit-desktop', use: { ...devices['Desktop Safari'], viewport: DESKTOP } },
  ],
  webServer: {
    /*
     * `vite preview` serves dist exactly as a static host would, including the
     * service worker, the SPA fallback and the release packs under /data/.
     *
     * The build is deliberately not part of this command. CI runs
     * `npm run build` and then `npm run test:e2e`, and folding the build in here
     * makes the server look hung for the length of a build and hides its output.
     */
    command: 'npx vite preview --port 4173 --strictPort --host 127.0.0.1',
    url: BASE_URL,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
    stdout: 'ignore',
    stderr: 'pipe',
  },
});
