#!/usr/bin/env node
/**
 * Everything the static host needs that Vite does not emit itself.
 *
 *  - 404.html: GitHub Pages serves it for any unmatched path, and it is a byte
 *    copy of index.html so a deep link boots the same app and the router resolves
 *    the route. Without it every deep link would be a dead end.
 *  - robots.txt and sitemap.xml: derived from the release's own dataMode. While
 *    the release is demonstration data the site disallows everything and no
 *    sitemap is published at all, because an invented region has no business in a
 *    search index. Flip the release to `real` and both change with no code edit.
 *  - A build report, so the sizes in the release notes are measured rather than
 *    remembered.
 */
import { copyFileSync, existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { dirname, extname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { gzipSync } from 'node:zlib';

const here = dirname(fileURLToPath(import.meta.url));
const webRoot = join(here, '..');
const repoRoot = join(webRoot, '..', '..');
const dist = join(webRoot, 'dist');
const brand = JSON.parse(readFileSync(join(repoRoot, 'brand.json'), 'utf8'));

if (!existsSync(join(dist, 'index.html'))) {
  process.stderr.write('postbuild: dist/index.html is missing, so the build did not produce a site.\n');
  process.exit(1);
}

// --- SPA deep-link fallback ------------------------------------------------
copyFileSync(join(dist, 'index.html'), join(dist, '404.html'));

// --- What the release says about itself -----------------------------------
let dataMode = 'demo';
let releaseId = 'unknown';
const manifestPath = join(dist, 'data', 'manifest.json');
if (existsSync(manifestPath)) {
  try {
    const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
    releaseId = manifest.releaseId ?? 'unknown';
    const metaEntry = manifest.files?.meta;
    if (metaEntry?.path) {
      const meta = JSON.parse(readFileSync(join(dist, 'data', metaEntry.path), 'utf8'));
      dataMode = meta.dataMode ?? 'demo';
    }
  } catch {
    // An unreadable manifest is not a reason to publish an indexable site.
    dataMode = 'demo';
  }
} else {
  process.stderr.write('postbuild: no dist/data/manifest.json. Run scripts/web-sync-packs.sh before building.\n');
}

const indexable = dataMode === 'real';

// --- robots.txt ------------------------------------------------------------
const robots = indexable
  ? `# ${brand.name}\nUser-agent: *\nAllow: /\n\n# Private and machine-only surfaces.\nDisallow: /admin\nDisallow: /data/\n\nSitemap: ${brand.url}/sitemap.xml\n`
  : `# ${brand.name}\n# This deployment carries demonstration data: an invented region that does not\n# exist. Nothing here describes a real departure, so nothing here should be\n# indexed. No sitemap is published while this is true.\nUser-agent: *\nDisallow: /\n`;
writeFileSync(join(dist, 'robots.txt'), robots);

// --- sitemap.xml -----------------------------------------------------------
if (indexable) {
  const paths = ['/', '/search', '/operators', '/stations', '/coverage', '/licences'];
  const urls = paths
    .map((path) => `  <url><loc>${brand.url}${path === '/' ? '' : path}</loc><changefreq>daily</changefreq></url>`)
    .join('\n');
  writeFileSync(
    join(dist, 'sitemap.xml'),
    `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls}\n</urlset>\n`,
  );
} else {
  process.stdout.write('postbuild: dataMode is demo, so robots.txt disallows everything and no sitemap is written.\n');
}

// --- Build report ----------------------------------------------------------
function walk(dir) {
  const out = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) out.push(...walk(full));
    else out.push(full);
  }
  return out;
}

const files = walk(dist).map((full) => {
  const bytes = statSync(full).size;
  const relativePath = relative(dist, full);
  const compressible = ['.js', '.css', '.html', '.json', '.svg', '.xml', '.txt', '.webmanifest'].includes(extname(full));
  return {
    path: relativePath,
    bytes,
    gzipBytes: compressible ? gzipSync(readFileSync(full), { level: 9 }).length : null,
  };
});

const appFiles = files.filter((f) => !f.path.startsWith('data/'));
const dataFiles = files.filter((f) => f.path.startsWith('data/'));
const sum = (list, key) => list.reduce((n, f) => n + (f[key] ?? 0), 0);

const report = {
  product: brand.name,
  version: brand.version,
  builtAt: new Date().toISOString(),
  dataMode,
  releaseId,
  indexable,
  totals: {
    appBytes: sum(appFiles, 'bytes'),
    appGzipBytes: sum(appFiles, 'gzipBytes'),
    dataBytes: sum(dataFiles, 'bytes'),
    fileCount: files.length,
  },
  chunks: appFiles
    .filter((f) => f.path.endsWith('.js') || f.path.endsWith('.css'))
    .sort((a, b) => b.bytes - a.bytes)
    .map((f) => ({ path: f.path, bytes: f.bytes, gzipBytes: f.gzipBytes })),
};

mkdirSync(join(webRoot, 'artifacts'), { recursive: true });
writeFileSync(join(webRoot, 'artifacts', 'build-report.json'), `${JSON.stringify(report, null, 2)}\n`);

const kib = (n) => `${(n / 1024).toFixed(1)} KiB`;
process.stdout.write(
  `\n${brand.name} ${brand.version} build report\n` +
    `  dataMode ${dataMode}, release ${releaseId}, indexable ${indexable}\n` +
    `  app ${kib(report.totals.appBytes)} raw, ${kib(report.totals.appGzipBytes)} gzip\n` +
    `  data ${kib(report.totals.dataBytes)}\n` +
    report.chunks
      .slice(0, 12)
      .map((c) => `    ${c.path.padEnd(46)} ${kib(c.bytes).padStart(10)} raw  ${kib(c.gzipBytes ?? 0).padStart(10)} gzip\n`)
      .join('') +
    `  report written to artifacts/build-report.json\n`,
);
