import { execSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const brand = JSON.parse(readFileSync(join(repoRoot, 'brand.json'), 'utf8')) as {
  name: string;
  slug: string;
  version: string;
  tagline: Record<string, string>;
  description: Record<string, string>;
  defaultLanguage: string;
};

/*
 * The manifest's colours come from the design tokens, the same file the
 * stylesheets, iOS and Android are generated from.
 */
const tokens = JSON.parse(readFileSync(join(repoRoot, 'design', 'tokens', 'odivrelo.tokens.json'), 'utf8')) as {
  color: { brand: Record<string, { $value: string }>; primitive: Record<string, { $value: string }> };
};

function gitCommit(): string {
  if (process.env.GITHUB_SHA) return process.env.GITHUB_SHA.slice(0, 12);
  try {
    return execSync('git rev-parse --short=12 HEAD', { cwd: repoRoot, stdio: ['ignore', 'pipe', 'ignore'] })
      .toString()
      .trim();
  } catch {
    // A source archive with no git metadata still has to build.
    return 'unknown';
  }
}

const buildTime = new Date().toISOString();

export default defineConfig({
  // Served from the site root of https://odivrelo.peterdsp.dev, so absolute paths
  // are correct and a deep link resolves its assets from anywhere in the app.
  base: '/',
  define: {
    __APP_VERSION__: JSON.stringify(brand.version),
    __GIT_COMMIT__: JSON.stringify(gitCommit()),
    __BUILD_TIME__: JSON.stringify(buildTime),
  },
  resolve: {
    alias: { '@': resolve(here, 'src') },
  },
  build: {
    target: 'es2022',
    /*
     * Source maps are off for the published artifact.
     *
     * A .map file embeds the absolute build path, and this working tree still
     * lives in a directory named after the rejected identity, so shipping maps
     * puts that string inside dist and fails scripts/check-brand.sh. Set
     * ODIVRELO_SOURCEMAPS=1 locally when a production stack trace needs reading.
     */
    sourcemap: process.env.ODIVRELO_SOURCEMAPS === '1',
    cssCodeSplit: true,
    // Budgets. These are warnings at the Rollup level; scripts/check-budgets.mjs
    // turns an overrun into a build failure, because a warning nobody reads is
    // not a budget.
    chunkSizeWarningLimit: 250,
    rollupOptions: {
      output: {
        manualChunks(id) {
          // MapLibre is isolated so that a route which never opens a map never
          // downloads it. It is larger than the whole rest of the app.
          if (id.includes('maplibre-gl')) return 'maplibre';
          if (id.includes('node_modules/react-router') || id.includes('node_modules/react-dom') || id.includes('node_modules/react/')) {
            return 'react';
          }
          return undefined;
        },
      },
    },
  },
  plugins: [
    react(),
    VitePWA({
      registerType: 'prompt',
      injectRegister: null,
      // The web manifest is generated here so the name, tagline and icons all
      // come from brand.json and the one mark in design/logo/.
      manifest: {
        id: '/',
        name: brand.name,
        short_name: brand.name,
        description: brand.description[brand.defaultLanguage] ?? brand.description.en ?? '',
        lang: brand.defaultLanguage,
        dir: 'ltr',
        start_url: '/search',
        scope: '/',
        display: 'standalone',
        orientation: 'any',
        background_color: tokens.color.primitive.canvas.$value.toLowerCase(),
        theme_color: tokens.color.brand.deepTealBlue.$value.toLowerCase(),
        categories: ['travel', 'navigation', 'utilities'],
        icons: [
          { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
          { src: '/icons/icon-256.png', sizes: '256x256', type: 'image/png', purpose: 'any' },
          { src: '/icons/icon-384.png', sizes: '384x384', type: 'image/png', purpose: 'any' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
          { src: '/icons/maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
          { src: '/favicon.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any' },
        ],
        shortcuts: [
          { name: 'Search', url: '/search' },
          { name: 'Saved', url: '/saved' },
          { name: 'Offline data', url: '/offline' },
        ],
      },
      workbox: {
        // The app shell. Packs are handled by a runtime rule instead, because they
        // are large and the reader chooses which ones to hold.
        globPatterns: ['**/*.{js,css,html,svg,png,ico,webmanifest}'],
        // MapLibre is deliberately NOT precached. Precaching it would download a
        // megabyte on first visit for a reader who may never open a map, which is
        // exactly what the lazy import exists to avoid. It is cached at runtime
        // the first time a map is actually opened, and works offline after that.
        globIgnores: [
          'data/**',
          // The link-preview image is for crawlers, never needed offline.
          'icons/og-image.png',
          '**/node_modules/**',
          'assets/maplibre-*.js',
          'assets/maplibre-*.css',
          '**/*.map',
        ],
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/data\//],
        cleanupOutdatedCaches: true,
        clientsClaim: false,
        skipWaiting: false,
        maximumFileSizeToCacheInBytes: 4 * 1024 * 1024,
        runtimeCaching: [
          {
            // The map bundle, cached the first time a map is opened.
            urlPattern: /\/assets\/maplibre-.*\.(js|css)$/,
            handler: 'CacheFirst',
            options: {
              cacheName: 'odivrelo-map-v1',
              expiration: { maxEntries: 8, maxAgeSeconds: 60 * 60 * 24 * 365 },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
          {
            // Content-addressed packs are immutable, so once cached they never
            // need revalidating.
            urlPattern: /\/data\/packs\/.*$/,
            handler: 'CacheFirst',
            options: {
              cacheName: 'odivrelo-packs-v1',
              expiration: { maxEntries: 400, maxAgeSeconds: 60 * 60 * 24 * 365 },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
          {
            // The manifest is the one mutable file: serve it fast but always
            // refresh it, so a new release is noticed.
            urlPattern: /\/data\/manifest\.json$/,
            handler: 'StaleWhileRevalidate',
            options: {
              cacheName: 'odivrelo-manifest-v1',
              expiration: { maxEntries: 4 },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
          {
            /*
             * Never cached, ever:
             *  - blob: URLs, which is how a wallet ticket is rendered;
             *  - anything under /admin, which is the private surface;
             *  - any cross-origin request, which includes every operator booking
             *    page the reader is handed off to.
             * A NetworkOnly rule with no cacheName cannot store a response.
             */
            urlPattern: ({ url, sameOrigin }) =>
              !sameOrigin || url.pathname.startsWith('/admin') || url.protocol === 'blob:',
            handler: 'NetworkOnly',
          },
        ],
      },
      devOptions: { enabled: false },
    }),
  ],
  server: { port: 5173, strictPort: true },
  preview: { port: 4173, strictPort: true },
});
