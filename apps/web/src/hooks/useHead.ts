import { useEffect } from 'react';
import { BRAND, canonicalUrl } from '../brand/brand';
import type { DataMode, Language } from '../data/contract';

/**
 * Document head management, done directly rather than through a helper library.
 *
 * Each managed element is tagged `data-od-head`, so a route can replace the set
 * it owns without disturbing anything the HTML shell declared. Structured data
 * is emitted as a single JSON-LD block, built from data the page already has.
 *
 * Indexing: while `dataMode` is `demo` every page emits
 * `<meta name="robots" content="noindex, nofollow">`. Flipping the release to
 * `real` removes it, with no other change anywhere in the app.
 */

export interface HeadDescriptor {
  readonly title: string;
  readonly description?: string;
  readonly path: string;
  readonly language: Language;
  readonly dataMode: DataMode;
  /** Only semantic, indexable pages set this. Everything else is noindex regardless. */
  readonly indexable?: boolean;
  readonly structuredData?: unknown;
  readonly imagePath?: string;
}

const MANAGED = 'data-od-head';

function upsertMeta(attribute: 'name' | 'property', key: string, content: string): void {
  const doc = globalThis.document;
  if (!doc) return;
  let element = doc.head.querySelector<HTMLMetaElement>(`meta[${attribute}="${key}"]`);
  if (!element) {
    element = doc.createElement('meta');
    element.setAttribute(attribute, key);
    element.setAttribute(MANAGED, '');
    doc.head.append(element);
  }
  element.setAttribute('content', content);
}

function removeMeta(attribute: 'name' | 'property', key: string): void {
  globalThis.document?.head.querySelector(`meta[${attribute}="${key}"]`)?.remove();
}

function upsertLink(rel: string, href: string): void {
  const doc = globalThis.document;
  if (!doc) return;
  let element = doc.head.querySelector<HTMLLinkElement>(`link[rel="${rel}"][${MANAGED}]`);
  if (!element) {
    element = doc.createElement('link');
    element.rel = rel;
    element.setAttribute(MANAGED, '');
    doc.head.append(element);
  }
  element.href = href;
}

function setStructuredData(data: unknown): void {
  const doc = globalThis.document;
  if (!doc) return;
  const existing = doc.head.querySelector<HTMLScriptElement>(`script[type="application/ld+json"][${MANAGED}]`);
  if (data === undefined || data === null) {
    existing?.remove();
    return;
  }
  const element = existing ?? doc.createElement('script');
  if (!existing) {
    element.type = 'application/ld+json';
    element.setAttribute(MANAGED, '');
    doc.head.append(element);
  }
  // textContent, never innerHTML: the payload includes place and operator names
  // that come from data, and a script element must never parse markup.
  element.textContent = JSON.stringify(data);
}

export function useHead(descriptor: HeadDescriptor): void {
  const { title, description, path, language, dataMode, indexable, structuredData, imagePath } = descriptor;

  useEffect(() => {
    const doc = globalThis.document;
    if (!doc) return;

    const fullTitle = title === BRAND.name ? BRAND.name : `${title} | ${BRAND.name}`;
    doc.title = fullTitle;

    const canonical = canonicalUrl(path);
    upsertLink('canonical', canonical);

    if (description) upsertMeta('name', 'description', description);
    else removeMeta('name', 'description');

    /*
     * Upgrade only.
     *
     * The static HTML already carries `noindex, nofollow`, so the safe state is
     * in the bytes and survives a client that never runs JavaScript. This may
     * raise a page to `index, follow`, and only when the route declares itself
     * indexable AND the release is real data. Demonstration data is never
     * indexable whatever a route claims.
     */
    const allowIndex = indexable === true && dataMode === 'real';
    upsertMeta('name', 'robots', allowIndex ? 'index, follow' : 'noindex, nofollow');

    upsertMeta('property', 'og:type', 'website');
    upsertMeta('property', 'og:site_name', BRAND.name);
    upsertMeta('property', 'og:title', fullTitle);
    upsertMeta('property', 'og:url', canonical);
    upsertMeta('property', 'og:locale', language === 'el' ? 'el_GR' : language === 'sq' ? 'sq_AL' : 'en_GB');
    if (description) upsertMeta('property', 'og:description', description);
    upsertMeta('property', 'og:image', canonicalUrl(imagePath ?? '/icons/og-image.png'));
    upsertMeta('name', 'twitter:card', 'summary');

    setStructuredData(structuredData);
  }, [title, description, path, language, dataMode, indexable, structuredData, imagePath]);
}

/** Organization block reused by the indexable pages. */
export function organizationNode() {
  return {
    '@type': 'Organization',
    name: BRAND.name,
    url: BRAND.url,
    email: BRAND.supportEmail,
  };
}
