#!/usr/bin/env node
/**
 * Generates src/brand/generated.ts from the repository-root brand.json.
 *
 * The web app must not bundle brand.json wholesale: that file carries a
 * `legacyNames` list and a repository URL that both still spell the rejected
 * identity, and scripts/check-brand.sh fails the build if either string reaches
 * apps/web/dist. Generating a module lets the whole app keep reading its identity
 * from exactly one source while shipping only the fields it is allowed to ship.
 *
 * The generated file is committed so that dev, tests and typecheck work without
 * a prebuild step, and it is regenerated (and compared) on every build.
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const webRoot = join(here, '..');
const repoRoot = join(webRoot, '..', '..');

const brand = JSON.parse(readFileSync(join(repoRoot, 'brand.json'), 'utf8'));

/** Only these fields are shipped to the browser. */
const SHIPPED = [
  'name',
  'slug',
  'tagline',
  'domain',
  'url',
  'supportEmail',
  'bundleId',
  'urlScheme',
  'contractVersion',
  'version',
  'languages',
  'defaultLanguage',
];

const missing = SHIPPED.filter((key) => brand[key] === undefined);
if (missing.length > 0) {
  process.stderr.write(`brand.json is missing required fields: ${missing.join(', ')}\n`);
  process.exit(1);
}

const shipped = Object.fromEntries(SHIPPED.map((key) => [key, brand[key]]));

const banner = `// GENERATED FILE. Do not edit by hand.
//
// Source: brand.json at the repository root.
// Regenerate: node apps/web/scripts/generate-brand-module.mjs
//
// Only the fields the browser is allowed to ship are copied here. The identity
// lives in brand.json and nowhere else; a rename is one edit in that one file.
`;

const body = `${banner}
export const BRAND_DATA = ${JSON.stringify(shipped, null, 2)} as const;
`;

const target = join(webRoot, 'src', 'brand', 'generated.ts');
let previous;
try {
  previous = readFileSync(target, 'utf8');
} catch {
  // No file yet, which compares unequal to the body and so triggers a write.
  previous = null;
}
if (previous !== body) {
  writeFileSync(target, body);
  process.stdout.write(`brand module regenerated from brand.json -> ${target}\n`);
} else {
  process.stdout.write('brand module is already current.\n');
}
