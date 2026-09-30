/**
 * Ticket-file import rules.
 *
 * A wallet file is a document the passenger already has, imported explicitly by
 * them. It is treated as sensitive: it is never uploaded, never handed to the
 * service worker cache, never written to a log or to diagnostics, and never put
 * into a URL. The only copy is the one in IndexedDB, so deleting really deletes.
 *
 * Validation does not trust the file name or the type the browser reports. Both
 * are attacker-controlled in the sense that a file picked from a share sheet can
 * claim anything, so the first bytes are sniffed and a file whose content does
 * not match its claimed type is refused rather than stored.
 */

export const MAX_WALLET_BYTES = 8 * 1024 * 1024;

export const ACCEPTED_TYPES = ['application/pdf', 'image/png', 'image/jpeg'] as const;
export type AcceptedType = (typeof ACCEPTED_TYPES)[number];

export const ACCEPT_ATTRIBUTE = '.pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg';

export type RejectionReason =
  | { kind: 'type' }
  | { kind: 'size'; bytes: number }
  | { kind: 'empty' }
  | { kind: 'content'; declared: AcceptedType };

export type ValidationResult = { ok: true; type: AcceptedType } | { ok: false; reason: RejectionReason };

const MAGIC: Record<AcceptedType, readonly number[][]> = {
  'application/pdf': [[0x25, 0x50, 0x44, 0x46]], // %PDF
  'image/png': [[0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]],
  'image/jpeg': [[0xff, 0xd8, 0xff]],
};

function startsWith(bytes: Uint8Array, prefix: readonly number[]): boolean {
  if (bytes.length < prefix.length) return false;
  return prefix.every((value, index) => bytes[index] === value);
}

function declaredType(file: File): AcceptedType | null {
  const byMime = (ACCEPTED_TYPES as readonly string[]).includes(file.type) ? (file.type as AcceptedType) : null;
  if (byMime) return byMime;
  // Some pickers report an empty type. Fall back to the extension, and the byte
  // sniff below still has to agree before anything is stored.
  const lower = file.name.toLowerCase();
  if (lower.endsWith('.pdf')) return 'application/pdf';
  if (lower.endsWith('.png')) return 'image/png';
  if (lower.endsWith('.jpg') || lower.endsWith('.jpeg')) return 'image/jpeg';
  return null;
}

export async function validateWalletFile(file: File): Promise<ValidationResult> {
  const declared = declaredType(file);
  if (!declared) return { ok: false, reason: { kind: 'type' } };
  if (file.size === 0) return { ok: false, reason: { kind: 'empty' } };
  if (file.size > MAX_WALLET_BYTES) return { ok: false, reason: { kind: 'size', bytes: file.size } };

  const head = new Uint8Array(await file.slice(0, 16).arrayBuffer());
  const matches = MAGIC[declared].some((prefix) => startsWith(head, prefix));
  if (!matches) return { ok: false, reason: { kind: 'content', declared } };

  return { ok: true, type: declared };
}

/** A short, stable id. Random rather than derived from the file, so it leaks nothing. */
export function newWalletItemId(): string {
  const bytes = new Uint8Array(16);
  globalThis.crypto.getRandomValues(bytes);
  return [...bytes].map((b) => b.toString(16).padStart(2, '0')).join('');
}

/**
 * Strips a file name down to something safe to display.
 *
 * The name is the passenger's own and is only ever rendered as text, never as
 * markup and never as a path, but control characters and directory separators are
 * removed so it cannot misrepresent itself in the list.
 */
export function safeFileName(raw: string): string {
  const cleaned = raw
    // Control characters are exactly what this expression removes, so the
    // rule that warns about them inside a pattern is inverted for this line.
    // eslint-disable-next-line no-control-regex
    .replace(/[\u0000-\u001f\u007f]/g, '')
    .replace(/[\\/]/g, ' ')
    .trim();
  return cleaned.length > 0 ? cleaned.slice(0, 120) : 'ticket';
}

export const TYPE_LABEL: Record<AcceptedType, string> = {
  'application/pdf': 'PDF',
  'image/png': 'PNG',
  'image/jpeg': 'JPEG',
};
