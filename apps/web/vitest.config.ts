import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      // Supplied by vite-plugin-pwa at build time only.
      'virtual:pwa-register': new URL('./src/test/pwaRegisterStub.ts', import.meta.url).pathname,
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    // The e2e suite is Playwright's; Vitest must not try to run it.
    exclude: ['e2e/**', 'node_modules/**', 'dist/**'],
    restoreMocks: true,
    clearMocks: true,
    coverage: {
      provider: 'v8',
      reportsDirectory: 'artifacts/coverage',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/test/**', 'src/**/*.test.{ts,tsx}', 'src/main.tsx'],
    },
  },
  define: {
    __APP_VERSION__: JSON.stringify('1.0.0-test'),
    __GIT_COMMIT__: JSON.stringify('testcommit00'),
    __BUILD_TIME__: JSON.stringify('2026-09-30T00:00:00.000Z'),
  },
});
