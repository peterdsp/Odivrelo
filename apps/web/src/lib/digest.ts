/**
 * SHA-256 over bytes, used to verify every pack before it is installed or read.
 *
 * A pack that does not match the digest the manifest names is never parsed and
 * never stored: a truncated download is indistinguishable from a tampered one,
 * so both are rejected the same way.
 */
export async function sha256Hex(bytes: ArrayBuffer | Uint8Array): Promise<string> {
  const view = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  const buffer = view.buffer.slice(view.byteOffset, view.byteOffset + view.byteLength) as ArrayBuffer;
  const subtle = globalThis.crypto?.subtle;
  if (!subtle) {
    throw new Error('Web Crypto is unavailable, so pack integrity cannot be verified.');
  }
  const digest = await subtle.digest('SHA-256', buffer);
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

/** Constant-time-ish comparison. Digests are public, but habit is cheap. */
export function digestsMatch(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i += 1) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

export function formatBytes(bytes: number, locale: string): string {
  const units = ['B', 'kB', 'MB', 'GB'];
  let value = bytes;
  let unit = 0;
  while (value >= 1000 && unit < units.length - 1) {
    value /= 1000;
    unit += 1;
  }
  const formatter = new Intl.NumberFormat(locale, { maximumFractionDigits: value < 10 && unit > 0 ? 1 : 0 });
  return `${formatter.format(value)} ${units[unit]}`;
}
