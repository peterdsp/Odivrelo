#!/usr/bin/env node
/**
 * Performance budgets, enforced.
 *
 * A warning nobody reads is not a budget, so this exits non-zero when a limit is
 * exceeded. The numbers below are justified against what the app has to do:
 *
 *  - initialJsGzip 180 KiB: React plus the router plus the whole application is
 *    what a first visit downloads. MapLibre is excluded because it is lazily
 *    imported and no route pulls it in unless the reader opens a map. A budget
 *    that included it would make the lazy split invisible.
 *  - initialCssGzip 12 KiB: one stylesheet for the whole app; the map's own CSS
 *    ships with the map chunk.
 *  - precacheBytes 800 KiB: what the service worker installs on first visit. This
 *    is the number that decides whether a passenger on a slow connection gets an
 *    offline-capable shell before they give up.
 *  - mapChunkGzip 300 KiB: MapLibre itself. It is not in the initial budget but it
 *    is still bounded, so an accidental import of something large alongside it is
 *    caught.
 *  - totalAppGzip 500 KiB: everything the app can ever ask for, maps included.
 */
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, extname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { gzipSync } from 'node:zlib';

const here = dirname(fileURLToPath(import.meta.url));
const dist = join(here, '..', 'dist');

const BUDGETS = {
  initialJsGzip: 180 * 1024,
  initialCssGzip: 12 * 1024,
  mapChunkGzip: 300 * 1024,
  totalAppGzip: 500 * 1024,
  precacheBytes: 800 * 1024,
};

if (!existsSync(dist)) {
  process.stderr.write('check-budgets: dist/ is missing. Run npm run build first.\n');
  process.exit(2);
}

function walk(dir) {
  const out = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) out.push(...walk(full));
    else out.push(full);
  }
  return out;
}

const files = walk(dist)
  .map((full) => ({ path: relative(dist, full), full }))
  .filter((f) => !f.path.startsWith('data/'));

const gzipOf = (f) => gzipSync(readFileSync(f.full), { level: 9 }).length;
const isMap = (f) => /assets\/maplibre-.*\.(js|css)$/.test(f.path);

const js = files.filter((f) => extname(f.path) === '.js');
const css = files.filter((f) => extname(f.path) === '.css');

const initialJsGzip = js.filter((f) => !isMap(f) && !f.path.startsWith('workbox') && f.path !== 'sw.js').reduce((n, f) => n + gzipOf(f), 0);
const initialCssGzip = css.filter((f) => !isMap(f)).reduce((n, f) => n + gzipOf(f), 0);
const mapChunkGzip = files.filter(isMap).reduce((n, f) => n + gzipOf(f), 0);
const totalAppGzip = [...js, ...css].reduce((n, f) => n + gzipOf(f), 0);

// What the service worker will install on first visit.
let precacheBytes = 0;
const swPath = join(dist, 'sw.js');
if (existsSync(swPath)) {
  const sw = readFileSync(swPath, 'utf8');
  // Workbox minifies the injected manifest, so the key may be quoted or not.
  const urls = [...sw.matchAll(/(?:"url"|url)\s*:\s*"([^"]+)"/g)].map((m) => m[1]);
  for (const url of urls) {
    const candidate = join(dist, url.replace(/^\//, ''));
    if (existsSync(candidate)) precacheBytes += statSync(candidate).size;
  }
}

const measured = { initialJsGzip, initialCssGzip, mapChunkGzip, totalAppGzip, precacheBytes };
const kib = (n) => `${(n / 1024).toFixed(1)} KiB`;

let failed = false;
process.stdout.write('Performance budgets\n');
for (const [key, limit] of Object.entries(BUDGETS)) {
  const value = measured[key];
  const ok = value <= limit;
  if (!ok) failed = true;
  const pct = ((value / limit) * 100).toFixed(0);
  process.stdout.write(`  ${ok ? 'PASS' : 'FAIL'}  ${key.padEnd(16)} ${kib(value).padStart(10)} of ${kib(limit).padStart(10)}  (${pct}%)\n`);
}

if (failed) {
  process.stderr.write('\nA performance budget was exceeded. Either reduce the bundle or justify and raise the budget deliberately.\n');
  process.exit(1);
}
process.stdout.write('All budgets met.\n');
