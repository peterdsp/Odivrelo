#!/usr/bin/env node
/**
 * Emits a static entry point with its own head for every fixed route, so a
 * static host answers 200.
 *
 * Without this, GitHub Pages serves `404.html` for anything but `/`, and the app
 * renders correctly while the response says the page does not exist. That is
 * wrong three times over: a shared journey link reports 404 to whoever receives
 * it, link previews and crawlers treat the page as broken, and the operator and
 * station pages the product exists to publish would be un-indexable on the day
 * the data becomes real.
 *
 * What is emitted is the application shell with a per-page `<head>`: the title,
 * description, canonical link and Open Graph tags for that specific entity, and
 * a robots directive. The body is still hydrated by the client. That closes the
 * two actual defects, the status code and the metadata, without taking on
 * server-side rendering of the body.
 *
 * Only the routes whose number is fixed get a file: the static pages and the
 * operators, a few dozen at most. Stations and journeys do not. There are tens
 * of thousands of them and the count grows with every collection, and one file
 * each took the deployment past the host's 20,000 file limit, where it failed
 * every day from 10 November 2026. The app already renders those pages, and
 * their heads, from the data packs it loads anyway, so a file per entity only
 * repeated the shell.
 *
 * `public/_redirects` answers `/stations/*` and `/journey/*` with the shell and a
 * 200, keeping the address. The rewrite target is `/`, not `/index.html`:
 * Cloudflare Pages redirects `/index.html` to `/`, so a rewrite to it either
 * 404s or lands the reader on the site root, which is what a rewrite of the
 * booking page did in October. A rewrite to `/` serves the shell directly.
 * Rules win over files on Pages, so no rule may cover a path that has a file.
 *
 * `404.html` is left alone. A genuinely unknown path is a 404 and should say so.
 */
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const webRoot = join(here, '..');
const repoRoot = join(webRoot, '..', '..');
const dist = join(webRoot, 'dist');
const brand = JSON.parse(readFileSync(join(repoRoot, 'brand.json'), 'utf8'));

const SITE = brand.url.replace(/\/+$/, '');

const shellPath = join(dist, 'index.html');
if (!existsSync(shellPath)) {
  process.stderr.write('prerender: dist/index.html is missing. Run the Vite build first.\n');
  process.exit(1);
}
const shell = readFileSync(shellPath, 'utf8');

// --------------------------------------------------------------------------
// The release, read from what is actually published
// --------------------------------------------------------------------------
const dataDir = join(dist, 'data');
const manifestPath = join(dataDir, 'manifest.json');

let release = null;
if (existsSync(manifestPath)) {
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
  const read = (name) => JSON.parse(readFileSync(join(dataDir, manifest.files[name].path), 'utf8'));
  release = {
    manifest,
    dataMode: read('meta').dataMode ?? 'demo',
    places: read('places').places ?? [],
    operators: read('operators').operators ?? {},
  };
} else {
  process.stderr.write('prerender: no dist/data/manifest.json, so only the static routes are emitted.\n');
}

const indexable = release?.dataMode === 'real';

// --------------------------------------------------------------------------
// Head rewriting
// --------------------------------------------------------------------------
const escapeHtml = (value) =>
  String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');

const localised = (value, language = 'el') => {
  if (!value) return '';
  if (typeof value === 'string') return value;
  return value[language] || value.en || value.el || '';
};

/**
 * Builds the page's head from the shell's, replacing what is page-specific.
 *
 * The robots directive is default-deny in the shell, and only a page that is both
 * marked indexable and backed by real data ever upgrades it. The safe state is
 * therefore the one that ships in the bytes, rather than something JavaScript has
 * to arrive and apply.
 */
function pageHtml({ path, title, description, ogType = 'website', canIndex = false, structuredData = null }) {
  const canonical = `${SITE}${path === '/' ? '/' : path}`;
  const fullTitle = title === brand.name ? brand.name : `${title} | ${brand.name}`;
  const robots = canIndex && indexable ? 'index, follow' : 'noindex, nofollow';

  let html = shell;

  html = html.replace(/<title>[^<]*<\/title>/, `<title>${escapeHtml(fullTitle)}</title>`);
  html = html.replace(
    /<meta name="description" content="[^"]*"\s*\/?>/,
    `<meta name="description" content="${escapeHtml(description)}" />`,
  );
  html = html.replace(
    /<meta name="robots" content="[^"]*"\s*\/?>/,
    `<meta name="robots" content="${robots}" />`,
  );

  const social = [
    `<link rel="canonical" href="${escapeHtml(canonical)}" />`,
    `<meta property="og:type" content="${escapeHtml(ogType)}" />`,
    `<meta property="og:site_name" content="${escapeHtml(brand.name)}" />`,
    `<meta property="og:title" content="${escapeHtml(fullTitle)}" />`,
    `<meta property="og:description" content="${escapeHtml(description)}" />`,
    `<meta property="og:url" content="${escapeHtml(canonical)}" />`,
    `<meta property="og:locale" content="el_GR" />`,
    `<meta property="og:image" content="${escapeHtml(`${SITE}/icons/og-image.png`)}" />`,
    `<meta name="twitter:card" content="summary" />`,
  ];
  if (structuredData) {
    social.push(`<script type="application/ld+json">${JSON.stringify(structuredData).replace(/</g, '\\u003c')}</script>`);
  }

  return html.replace('</head>', `    ${social.join('\n    ')}\n  </head>`);
}

function emit(routePath, html, { extensionless = true } = {}) {
  const directory = routePath === '/' ? dist : join(dist, routePath.replace(/^\//, ''));
  mkdirSync(directory, { recursive: true });
  writeFileSync(join(directory, 'index.html'), html);
  // A static host answers /search with a redirect to /search/ when only the
  // directory exists. A sibling search.html lets GitHub Pages and Cloudflare
  // Pages serve the extensionless deep link directly with a 200.
  if (routePath !== '/' && extensionless) writeFileSync(`${directory}.html`, html);
}

// --------------------------------------------------------------------------
// Static routes
// --------------------------------------------------------------------------
const STATIC_ROUTES = [
  { path: '/welcome', title: 'Ταξίδεψε με βεβαιότητα', description: brand.tagline.el },
  {
    path: '/search',
    title: 'Βρες δρομολόγιο',
    description:
      'Προγραμματισμένα υπεραστικά δρομολόγια με την ακριβή θέση επιβίβασης, την πηγή κάθε στοιχείου και το πόσο παλιό είναι.',
  },
  { path: '/results', title: 'Αποτελέσματα', description: 'Αποτελέσματα αναζήτησης δρομολογίων.' },
  {
    path: '/operators',
    title: 'Υπεραστικοί μεταφορείς',
    description: 'Κάθε μεταφορέας αυτής της έκδοσης δεδομένων, με ό,τι έχει επιβεβαιωθεί για τον καθένα.',
    canIndex: true,
  },
  {
    path: '/stations',
    title: 'Σταθμοί και στάσεις',
    description: 'Κάθε σταθμός και θέση επιβίβασης σε αυτή την έκδοση δεδομένων.',
    canIndex: true,
  },
  { path: '/saved', title: 'Αποθηκευμένα ταξίδια', description: 'Τα ταξίδια που έχεις αποθηκεύσει σε αυτή τη συσκευή.' },
  { path: '/offline', title: 'Δεδομένα εκτός σύνδεσης', description: 'Κατέβασε την έκδοση δεδομένων σε αυτή τη συσκευή.' },
  { path: '/wallet', title: 'Πορτοφόλι ταξιδιού', description: 'Κράτα αντίγραφο ενός εισιτηρίου που ήδη έχεις.' },
  { path: '/settings', title: 'Ρυθμίσεις', description: 'Γλώσσα, εμφάνιση, προσβασιμότητα, αποθήκευση και ιδιωτικότητα.' },
  {
    path: '/coverage',
    title: 'Κάλυψη και πηγές',
    description: 'Τι καλύπτει αυτή η έκδοση δεδομένων, τι ρητά δεν καλύπτει, και από πού προέρχεται.',
    canIndex: true,
  },
  { path: '/licences', title: 'Άδειες και σημειώσεις', description: 'Λογισμικό ανοιχτού κώδικα και άδειες δεδομένων.' },
];

const emitted = [];

for (const route of STATIC_ROUTES) {
  emit(route.path, pageHtml({ ...route, canIndex: route.canIndex ?? false }));
  emitted.push(route.path);
}

// The root keeps its own head, with the default-deny robots already in the shell.
emit(
  '/',
  pageHtml({
    path: '/',
    title: brand.name,
    description: brand.description?.el ?? brand.tagline.el,
    canIndex: false,
  }),
);

// --------------------------------------------------------------------------
// Entity routes, enumerated from the published release
// --------------------------------------------------------------------------
if (release) {
  for (const [id, operator] of Object.entries(release.operators)) {
    const name = localised(operator.name);
    emit(
      `/operators/${id}`,
      pageHtml({
        path: `/operators/${id}`,
        title: name,
        description: `Επιβεβαιωμένα στοιχεία, κάλυψη και πηγές για τον μεταφορέα ${name}.`,
        ogType: 'profile',
        canIndex: true,
        structuredData: {
          '@context': 'https://schema.org',
          '@type': 'Organization',
          name,
          url: operator.officialSiteUrl ?? `${SITE}/operators/${id}`,
          ...(operator.contact?.phone ? { telephone: operator.contact.phone } : {}),
        },
      }),
      { extensionless: false },
    );
    emitted.push(`/operators/${id}`);
  }
}

// --------------------------------------------------------------------------
// The host refuses a deployment of more than 20,000 files, and it refuses it
// at the very last step, after a collection of several hours. This counts
// everything in dist, not only what was prerendered, so data packs, assets and
// icons are held to the budget too. The site needs a few hundred files; the
// budget sits far below the host's limit so that anything which starts
// emitting a file per stop, journey or date fails here, in seconds, long
// before it can reach that limit again. Fix what emits the files rather than
// raising this number.
// --------------------------------------------------------------------------
const FILE_BUDGET = 2_000;
const countFiles = (directory) =>
  readdirSync(directory, { withFileTypes: true }).reduce(
    (total, entry) => total + (entry.isDirectory() ? countFiles(join(directory, entry.name)) : 1),
    0,
  );
const fileCount = countFiles(dist);
if (fileCount > FILE_BUDGET) {
  process.stderr.write(
    `dist holds ${fileCount} files, over the ${FILE_BUDGET} budget. Cloudflare Pages rejects a ` +
      'deployment above 20,000 files. Render the new pages from the data packs instead of ' +
      'emitting a file each.\n',
  );
  process.exit(1);
}

// --------------------------------------------------------------------------
// A machine-readable list, so a test can assert no route regressed to a 404.
// --------------------------------------------------------------------------
writeFileSync(
  join(webRoot, 'artifacts', 'prerendered-routes.json'),
  `${JSON.stringify({ generatedAt: new Date().toISOString(), indexable, routes: emitted.sort() }, null, 2)}\n`,
);

process.stdout.write(
  `Prerendered ${emitted.length + 1} entry points ` +
    `(${STATIC_ROUTES.length} static, ${emitted.length - STATIC_ROUTES.length} from the release, plus the root).\n` +
    `  robots: ${indexable ? 'index, follow on indexable pages' : 'noindex, nofollow everywhere (dataMode is demo)'}\n` +
    `  dist holds ${fileCount} files (budget ${FILE_BUDGET})\n`,
);
