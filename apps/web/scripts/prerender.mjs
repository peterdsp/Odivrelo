#!/usr/bin/env node
/**
 * Emits a real static entry point for every route, so a static host answers 200.
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
 * Entity routes are enumerated from the release packs that ship in `dist/data/`,
 * so the set of pages always matches the data actually published.
 *
 * `404.html` is left alone. A genuinely unknown path is a 404 and should say so.
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
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
  const serviceDates = Object.keys(manifest.files)
    .filter((name) => name.startsWith('journeys-'))
    .map((name) => name.slice('journeys-'.length))
    .sort();
  release = {
    manifest,
    dataMode: read('meta').dataMode ?? 'demo',
    places: read('places').places ?? [],
    operators: read('operators').operators ?? {},
    stops: read('stops').stops ?? {},
    serviceDates,
    journeysByDate: Object.fromEntries(serviceDates.map((date) => [date, read(`journeys-${date}`)])),
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

function emit(routePath, html) {
  const directory = routePath === '/' ? dist : join(dist, routePath.replace(/^\//, ''));
  mkdirSync(directory, { recursive: true });
  writeFileSync(join(directory, 'index.html'), html);
  // A static host answers /search with a redirect to /search/ when only the
  // directory exists. A sibling search.html lets GitHub Pages and Cloudflare
  // Pages serve the extensionless deep link directly with a 200.
  if (routePath !== '/') writeFileSync(`${directory}.html`, html);
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
    );
    emitted.push(`/operators/${id}`);
  }

  for (const [id, stop] of Object.entries(release.stops)) {
    const name = localised(stop.name);
    emit(
      `/stations/${id}`,
      pageHtml({
        path: `/stations/${id}`,
        title: name,
        description: `Θέσεις επιβίβασης, μεταφορείς και προγραμματισμένες αναχωρήσεις στον σταθμό ${name}.`,
        ogType: 'place',
        canIndex: true,
        structuredData: {
          '@context': 'https://schema.org',
          '@type': stop.kind === 'stop_place' ? 'BusStation' : 'BusStop',
          name,
          ...(stop.municipality ? { address: { '@type': 'PostalAddress', addressLocality: stop.municipality } } : {}),
          geo: { '@type': 'GeoCoordinates', latitude: stop.latitude, longitude: stop.longitude },
        },
      }),
    );
    emitted.push(`/stations/${id}`);
  }

  const seenJourneys = new Set();
  for (const [date, pack] of Object.entries(release.journeysByDate)) {
    for (const [id, journey] of Object.entries(pack.journeys ?? {})) {
      if (seenJourneys.has(id)) continue;
      seenJourneys.add(id);
      const origin = localised(journey.departure?.stopName);
      const destination = localised(journey.arrival?.stopName);
      const operatorName = localised(journey.operator?.name);
      const title = `${origin} προς ${destination}, ${date}`;
      const description = `Ο μεταφορέας ${operatorName} αναχωρεί από ${origin} και φτάνει ${destination} στις ${date}.`;
      const structuredData = {
        '@context': 'https://schema.org',
        '@type': 'BusTrip',
        provider: { '@type': 'Organization', name: operatorName },
        departureBusStop: { '@type': 'BusStop', name: origin },
        arrivalBusStop: { '@type': 'BusStop', name: destination },
        departureTime: journey.departure?.at,
        arrivalTime: journey.arrival?.at,
      };
      // Journeys are date-specific, so the canonical link carries the date.
      emit(`/journey/${id}`, pageHtml({ path: `/journey/${id}?date=${date}`, title, description, structuredData }));
      emit(
        `/journey/${id}/booking`,
        pageHtml({
          path: `/journey/${id}/booking?date=${date}`,
          title: 'Πώς αγοράζεις αυτό το εισιτήριο',
          description:
            'Το Odivrelo δεν πουλά και δεν εκδίδει εισιτήρια. Ο μεταφορέας είναι υπεύθυνος για την τιμή, το εισιτήριο, τις αλλαγές και τις επιστροφές.',
        }),
      );
      emitted.push(`/journey/${id}`, `/journey/${id}/booking`);
    }
  }
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
    `  robots: ${indexable ? 'index, follow on indexable pages' : 'noindex, nofollow everywhere (dataMode is demo)'}\n`,
);
