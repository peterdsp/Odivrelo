/**
 * Test environment setup.
 *
 * jsdom is missing several things this app genuinely uses, and each shim below
 * exists because the code under test would otherwise be untestable rather than to
 * paper over a bug:
 *
 *  - IndexedDB, supplied by fake-indexeddb, because the offline packs, saved trips
 *    and travel wallet all live there and are worth testing for real.
 *  - Web Crypto's `subtle.digest`, because pack integrity verification is a
 *    release gate and must be exercised with real SHA-256.
 *  - ResizeObserver and matchMedia, which the adaptive layout subscribes to.
 */
import '@testing-library/jest-dom/vitest';
import 'fake-indexeddb/auto';
import { webcrypto } from 'node:crypto';
import { afterEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';

if (!globalThis.crypto?.subtle) {
  Object.defineProperty(globalThis, 'crypto', { value: webcrypto, configurable: true, writable: true });
}

if (typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof ResizeObserver;
}

if (typeof globalThis.matchMedia === 'undefined') {
  Object.defineProperty(globalThis, 'matchMedia', {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      dispatchEvent: () => false,
    }),
  });
}

// jsdom has no layout, so every measurement is zero. The adaptive layout hook
// reads clientWidth/clientHeight, and tests that care set these explicitly.
if (!Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'clientWidth')?.get) {
  Object.defineProperty(HTMLElement.prototype, 'clientWidth', { configurable: true, get: () => 0 });
  Object.defineProperty(HTMLElement.prototype, 'clientHeight', { configurable: true, get: () => 0 });
}

if (typeof URL.createObjectURL === 'undefined') {
  let counter = 0;
  URL.createObjectURL = vi.fn(() => `blob:odivrelo/${(counter += 1)}`);
  URL.revokeObjectURL = vi.fn();
}

afterEach(() => {
  cleanup();
  try {
    globalThis.localStorage?.clear();
    globalThis.sessionStorage?.clear();
  } catch {
    // A test that deliberately breaks storage must not break teardown.
  }
});
