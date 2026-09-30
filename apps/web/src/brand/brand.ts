/**
 * The single place the web app learns its own identity.
 *
 * Nothing else in apps/web hardcodes the product name, slug, domain, bundle id
 * or URL scheme. Everything reads it from here, and here reads
 * `src/brand/generated.ts`, which is produced from the repository-root
 * brand.json by scripts/generate-brand-module.mjs. A rename is one edit in
 * brand.json.
 */
import { BRAND_DATA } from './generated';

export interface BrandTagline {
  readonly el: string;
  readonly en: string;
  readonly sq: string;
}

export interface Brand {
  readonly name: string;
  readonly slug: string;
  readonly tagline: BrandTagline;
  readonly domain: string;
  readonly url: string;
  readonly supportEmail: string;
  readonly bundleId: string;
  readonly urlScheme: string;
  readonly contractVersion: string;
  readonly version: string;
  readonly languages: readonly string[];
  readonly defaultLanguage: string;
}

export const BRAND: Brand = BRAND_DATA;

/** Absolute canonical URL for a router path. */
export function canonicalUrl(path: string): string {
  const base = BRAND.url.replace(/\/+$/, '');
  return `${base}${path.startsWith('/') ? path : `/${path}`}`;
}

/** Deep link into the native app, for the "also available in the app" affordances. */
export function appDeepLink(path: string): string {
  return `${BRAND.urlScheme}://${path.replace(/^\/+/, '')}`;
}
