/**
 * The Odivrelo marks, inlined as SVG so the header needs no network request and
 * renders before anything else.
 *
 * The artwork is the one in design/logo/: a ring crossed by a road with lane
 * dashes, arriving at an orange destination node on the ring. The path data is
 * generated from the same geometry every platform ships
 * (markGeometry.generated.ts), so Web, iOS and Android draw one mark.
 *
 * The ring and road take their colours from the theme's custom properties, so
 * the mark follows `prefers-color-scheme` and the explicit `data-theme` override
 * without any JavaScript: deep teal blue and navy on light surfaces, white on
 * dark ones. The node is always the brand orange.
 */
import { useI18n } from '../i18n/I18nProvider';
import { BRAND } from './brand';
import { MARK_NODE, MARK_RING, MARK_ROAD, MARK_SILHOUETTE, MARK_VIEWBOX } from './markGeometry.generated';

export interface MarkProps {
  readonly size?: number;
  readonly className?: string;
  /** Omit the accessible name when the mark sits next to a text label. */
  readonly decorative?: boolean;
}

function a11yProps(decorative: boolean, label: string) {
  return decorative ? { 'aria-hidden': true, role: 'presentation' } : { role: 'img', 'aria-label': label };
}

/** The full-colour mark: ring, road and destination node. */
export function Mark({ size = 32, className, decorative = false }: MarkProps) {
  const { t } = useI18n();
  return (
    <svg
      viewBox={MARK_VIEWBOX}
      width={size}
      height={size}
      className={className}
      {...a11yProps(decorative, t('a11y.logo'))}
      focusable="false"
    >
      <path d={MARK_RING} fill="var(--od-mark-ring)" fillRule="evenodd" />
      <path d={MARK_ROAD} fill="var(--od-mark-road)" fillRule="evenodd" />
      <path d={MARK_NODE} fill="var(--od-brand-warm-orange)" />
    </svg>
  );
}

/** Single-colour mark. Takes its colour from `currentColor`. */
export function MarkMono({ size = 24, className, decorative = true }: MarkProps) {
  const { t } = useI18n();
  return (
    <svg
      viewBox={MARK_VIEWBOX}
      width={size}
      height={size}
      className={className}
      {...a11yProps(decorative, t('a11y.logo'))}
      focusable="false"
    >
      <path d={MARK_SILHOUETTE} fill="currentColor" fillRule="evenodd" />
    </svg>
  );
}

export interface WordmarkProps {
  readonly height?: number;
  readonly className?: string;
}

/**
 * Horizontal wordmark: the mark beside the product name. The name is real text
 * in the page rather than an outlined path, so it stays selectable, searchable
 * and correctly weighted at any zoom.
 */
export function Wordmark({ height = 32, className }: WordmarkProps) {
  return (
    <span className={className ? `od-wordmark ${className}` : 'od-wordmark'}>
      <Mark size={height} decorative className="od-wordmark__mark" />
      <span className="od-wordmark__name">{BRAND.name}</span>
    </span>
  );
}
