/**
 * The Poravia marks, inlined as SVG so the header needs no network request and
 * renders before anything else.
 *
 * The artwork is the one in design/logo/: an arch, which is a passage you travel
 * through, with an amber point inside the opening standing for the exact
 * boarding point. Web, iOS and Android all ship this same mark.
 *
 * The wordmark has a light and a dark variant. Rather than swap files at run
 * time, both are expressed with the theme's own custom properties, so the
 * correct colours follow `prefers-color-scheme` and the explicit `data-theme`
 * override without any JavaScript.
 */
import { useI18n } from '../i18n/I18nProvider';
import { BRAND } from './brand';

export interface MarkProps {
  readonly size?: number;
  readonly className?: string;
  /** Omit the accessible name when the mark sits next to a text label. */
  readonly decorative?: boolean;
}

/** The full-colour app mark: teal ground, limestone arch, amber point. */
export function Mark({ size = 32, className, decorative = false }: MarkProps) {
  const { t } = useI18n();
  const label = t('a11y.logo');
  return (
    <svg
      viewBox="0 0 64 64"
      width={size}
      height={size}
      className={className}
      {...(decorative ? { 'aria-hidden': true, role: 'presentation' } : { role: 'img', 'aria-label': label })}
      focusable="false"
    >
      <rect width="64" height="64" rx="15" fill="#0B6B63" />
      <path d="M16 53V30a16 16 0 0 1 32 0v23" fill="none" stroke="#EFF7F5" strokeWidth="7.5" strokeLinecap="round" />
      <circle cx="32" cy="34" r="5.5" fill="#F2B84B" />
    </svg>
  );
}

/** Single-colour mark. Takes its colour from `currentColor`. */
export function MarkMono({ size = 24, className, decorative = true }: MarkProps) {
  const { t } = useI18n();
  return (
    <svg
      viewBox="0 0 64 64"
      width={size}
      height={size}
      className={className}
      {...(decorative ? { 'aria-hidden': true, role: 'presentation' } : { role: 'img', 'aria-label': t('a11y.logo') })}
      focusable="false"
    >
      <path d="M16 53V30a16 16 0 0 1 32 0v23" fill="none" stroke="currentColor" strokeWidth="7.5" strokeLinecap="round" />
      <circle cx="32" cy="34" r="5.5" fill="currentColor" />
    </svg>
  );
}

export interface WordmarkProps {
  readonly height?: number;
  readonly className?: string;
}

/**
 * Horizontal wordmark. The arch and the product name are drawn with theme
 * tokens, and the name is real text in the page rather than an outlined path, so
 * it stays selectable, searchable and correctly weighted at any zoom.
 */
export function Wordmark({ height = 32, className }: WordmarkProps) {
  return (
    <span className={className ? `pv-wordmark ${className}` : 'pv-wordmark'}>
      <svg
        viewBox="0 0 46 64"
        height={height}
        width={(height * 46) / 64}
        aria-hidden="true"
        role="presentation"
        focusable="false"
        className="pv-wordmark__arch"
      >
        <path
          d="M10 50V30a13 13 0 0 1 26 0v20"
          fill="none"
          stroke="var(--pv-primary)"
          strokeWidth="6"
          strokeLinecap="round"
        />
        <circle cx="23" cy="33" r="4.5" fill="var(--pv-accent)" />
      </svg>
      <span className="pv-wordmark__name">{BRAND.name}</span>
    </span>
  );
}
