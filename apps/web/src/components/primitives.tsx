import {
  forwardRef,
  useId,
  useState,
  type AnchorHTMLAttributes,
  type ButtonHTMLAttributes,
  type ElementType,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
} from 'react';
import { Link, type LinkProps } from 'react-router-dom';
import { useI18n } from '../i18n/I18nProvider';
import { useAnnouncer } from './Announcer';

// ---------------------------------------------------------------------------
// Button
// ---------------------------------------------------------------------------

export type ButtonTone = 'primary' | 'secondary' | 'quiet' | 'danger';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  readonly tone?: ButtonTone;
  readonly full?: boolean;
  /**
   * Why this button cannot be used. A button with this set is still focusable
   * and still announces, because a silently greyed-out control tells the reader
   * nothing. It is never used to hide an action that simply has not been built.
   */
  readonly unavailableReason?: string;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { tone = 'secondary', full = false, unavailableReason, className, children, onClick, ...rest },
  ref,
) {
  const { alert } = useAnnouncer();
  const classes = ['od-button', `od-button--${tone}`, full ? 'od-button--full' : '', className ?? '']
    .filter(Boolean)
    .join(' ');
  return (
    <button
      ref={ref}
      type={rest.type ?? 'button'}
      className={classes}
      aria-disabled={unavailableReason ? true : rest['aria-disabled']}
      {...(unavailableReason ? { 'data-unavailable': '' } : {})}
      onClick={(event) => {
        if (unavailableReason) {
          event.preventDefault();
          alert(unavailableReason);
          return;
        }
        onClick?.(event);
      }}
      {...rest}
    >
      {children}
      {unavailableReason ? <span className="od-visually-hidden">. {unavailableReason}</span> : null}
    </button>
  );
});

// ---------------------------------------------------------------------------
// Links
// ---------------------------------------------------------------------------

export interface ButtonLinkProps extends LinkProps {
  readonly tone?: ButtonTone;
  readonly full?: boolean;
}

export function ButtonLink({ tone = 'secondary', full = false, className, ...rest }: ButtonLinkProps) {
  const classes = ['od-button', `od-button--${tone}`, full ? 'od-button--full' : '', className ?? '']
    .filter(Boolean)
    .join(' ');
  return <Link className={classes} {...rest} />;
}

export interface ExternalLinkProps extends AnchorHTMLAttributes<HTMLAnchorElement> {
  readonly href: string;
  /** Rendered as a button rather than an inline link. */
  readonly tone?: ButtonTone;
  readonly children: ReactNode;
  readonly accessibleLabel?: string;
}

/**
 * A link that leaves the app.
 *
 * `noopener noreferrer` on every one: the operator's own site has no business
 * holding a handle on this window, and the referrer is nobody's business either.
 * The accessible name always says that a new tab will open, because a link that
 * moves the reader somewhere else without warning is a WCAG 3.2.5 failure.
 */
export function ExternalLink({ href, tone, children, accessibleLabel, className, ...rest }: ExternalLinkProps) {
  const { t } = useI18n();
  const classes = tone
    ? ['od-button', `od-button--${tone}`, 'od-external', className ?? ''].filter(Boolean).join(' ')
    : ['od-link', 'od-external', className ?? ''].filter(Boolean).join(' ');
  return (
    <a
      href={href}
      className={classes}
      target="_blank"
      rel="noopener noreferrer external"
      {...(accessibleLabel ? { 'aria-label': t('a11y.externalLink', { label: accessibleLabel }) } : {})}
      {...rest}
    >
      {children}
      <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true" focusable="false" className="od-external__icon">
        <path
          d="M6 3h7v7M13 3 6.5 9.5"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.6"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <path d="M11 11.5V13H3V5h1.5" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
      </svg>
      {accessibleLabel ? null : <span className="od-visually-hidden">, {t('app.opensExternally')}</span>}
    </a>
  );
}

// ---------------------------------------------------------------------------
// Card and sections
// ---------------------------------------------------------------------------

export interface CardProps {
  readonly as?: ElementType;
  readonly children: ReactNode;
  readonly className?: string;
  readonly tone?: 'plain' | 'muted' | 'accent';
}

export function Card({ as: Tag = 'div', children, className, tone = 'plain' }: CardProps) {
  return <Tag className={['od-card', `od-card--${tone}`, className ?? ''].filter(Boolean).join(' ')}>{children}</Tag>;
}

export interface SectionProps {
  readonly title: string;
  readonly children: ReactNode;
  readonly description?: ReactNode;
  readonly level?: 2 | 3 | 4;
  readonly id?: string;
  readonly className?: string;
  readonly actions?: ReactNode;
}

/** A titled region with a real heading, so the heading order of every page is walkable. */
export function Section({ title, children, description, level = 2, id, className, actions }: SectionProps) {
  const generated = useId();
  const headingId = id ? `${id}-heading` : generated;
  const Heading = `h${level}` as ElementType;
  return (
    <section aria-labelledby={headingId} id={id} className={['od-section', className ?? ''].filter(Boolean).join(' ')}>
      <div className="od-section__head">
        <Heading id={headingId} className="od-section__title">
          {title}
        </Heading>
        {actions ? <div className="od-section__actions">{actions}</div> : null}
      </div>
      {description ? <div className="od-section__description">{description}</div> : null}
      {children}
    </section>
  );
}

// ---------------------------------------------------------------------------
// Description lists
// ---------------------------------------------------------------------------

export interface FactProps {
  readonly label: string;
  readonly children: ReactNode;
}

export function FactList({ children, className }: { children: ReactNode; className?: string }) {
  return <dl className={['od-facts', className ?? ''].filter(Boolean).join(' ')}>{children}</dl>;
}

export function Fact({ label, children }: FactProps) {
  return (
    <div className="od-facts__row">
      <dt className="od-facts__label">{label}</dt>
      <dd className="od-facts__value">{children}</dd>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Badge
// ---------------------------------------------------------------------------

export type BadgeTone = 'neutral' | 'success' | 'warning' | 'error' | 'info' | 'accent';

export interface BadgeProps {
  readonly tone?: BadgeTone;
  readonly children: ReactNode;
  readonly icon?: 'check' | 'clock' | 'alert' | 'question' | 'moon' | 'route' | 'shield' | 'download';
  readonly title?: string;
}

const ICON_PATHS: Record<NonNullable<BadgeProps['icon']>, string> = {
  check: 'M3.5 8.5 6.5 11.5 12.5 4.5',
  clock: 'M8 4v4.2l2.8 1.8M8 1.5a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13Z',
  alert: 'M8 5v4.5M8 11.6v.2M8 1.8 1.4 13.2h13.2L8 1.8Z',
  question: 'M6 5.6a2 2 0 1 1 2.6 1.9c-.5.2-.6.6-.6 1V9M8 11.4v.2M8 1.5a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13Z',
  moon: 'M12.4 9.8A5 5 0 0 1 6.2 3.6 5.2 5.2 0 1 0 12.4 9.8Z',
  route: 'M4 12.5V6a2.5 2.5 0 0 1 5 0v4a2.5 2.5 0 0 0 5 0V3.5',
  shield: 'M8 1.8 3 3.6v4.1c0 3 2.1 5.4 5 6.5 2.9-1.1 5-3.5 5-6.5V3.6L8 1.8Zm-2 6.2 1.7 1.7L10.4 7',
  download: 'M8 2.5v7M5 7l3 3 3-3M3 13h10',
};

/**
 * A status chip. Colour is never the only signal: every badge carries an icon
 * and a text label, so it survives both colour blindness and a monochrome print.
 */
export function Badge({ tone = 'neutral', children, icon, title }: BadgeProps) {
  return (
    <span className={`od-badge od-badge--${tone}`} {...(title ? { title } : {})}>
      {icon ? (
        <svg viewBox="0 0 16 16" width="13" height="13" aria-hidden="true" focusable="false">
          <path d={ICON_PATHS[icon]} fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      ) : null}
      <span>{children}</span>
    </span>
  );
}

// ---------------------------------------------------------------------------
// Fields
// ---------------------------------------------------------------------------

export interface FieldShellProps {
  readonly label: string;
  readonly id: string;
  readonly children: ReactNode;
  readonly hint?: ReactNode;
  readonly error?: string | null;
  readonly className?: string;
}

export function FieldShell({ label, id, children, hint, error, className }: FieldShellProps) {
  return (
    <div className={['od-field', error ? 'od-field--invalid' : '', className ?? ''].filter(Boolean).join(' ')}>
      <label className="od-field__label" htmlFor={id}>
        {label}
      </label>
      {children}
      {hint ? (
        <p className="od-field__hint" id={`${id}-hint`}>
          {hint}
        </p>
      ) : null}
      {error ? (
        <p className="od-field__error" id={`${id}-error`}>
          {error}
        </p>
      ) : null}
    </div>
  );
}

export interface TextFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> {
  readonly label: string;
  readonly id: string;
  readonly hint?: ReactNode;
  readonly error?: string | null;
}

export function TextField({ label, id, hint, error, className, ...rest }: TextFieldProps) {
  const described = [hint ? `${id}-hint` : '', error ? `${id}-error` : ''].filter(Boolean).join(' ');
  return (
    <FieldShell label={label} id={id} hint={hint} error={error} className={className}>
      <input
        id={id}
        className="od-input"
        aria-invalid={error ? true : undefined}
        aria-describedby={described || undefined}
        {...rest}
      />
    </FieldShell>
  );
}

export interface SelectFieldProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'id'> {
  readonly label: string;
  readonly id: string;
  readonly hint?: ReactNode;
  readonly error?: string | null;
  readonly children: ReactNode;
}

export function SelectField({ label, id, hint, error, className, children, ...rest }: SelectFieldProps) {
  const described = [hint ? `${id}-hint` : '', error ? `${id}-error` : ''].filter(Boolean).join(' ');
  return (
    <FieldShell label={label} id={id} hint={hint} error={error} className={className}>
      <select id={id} className="od-select" aria-describedby={described || undefined} {...rest}>
        {children}
      </select>
    </FieldShell>
  );
}

export interface CheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id' | 'type'> {
  readonly label: ReactNode;
  readonly id: string;
  readonly hint?: ReactNode;
}

export function Checkbox({ label, id, hint, ...rest }: CheckboxProps) {
  return (
    <div className="od-checkbox">
      <input id={id} type="checkbox" className="od-checkbox__input" aria-describedby={hint ? `${id}-hint` : undefined} {...rest} />
      <label htmlFor={id} className="od-checkbox__label">
        {label}
      </label>
      {hint ? (
        <p className="od-checkbox__hint" id={`${id}-hint`}>
          {hint}
        </p>
      ) : null}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Disclosure
// ---------------------------------------------------------------------------

export interface DisclosureProps {
  readonly summary: string;
  readonly children: ReactNode;
  readonly defaultOpen?: boolean;
  readonly className?: string;
}

/** A native `<details>`: keyboard-operable and screen-reader-correct for free. */
export function Disclosure({ summary, children, defaultOpen = false, className }: DisclosureProps) {
  return (
    <details className={['od-disclosure', className ?? ''].filter(Boolean).join(' ')} {...(defaultOpen ? { open: true } : {})}>
      <summary className="od-disclosure__summary">{summary}</summary>
      <div className="od-disclosure__body">{children}</div>
    </details>
  );
}

// ---------------------------------------------------------------------------
// Progress
// ---------------------------------------------------------------------------

export interface ProgressBarProps {
  readonly value: number;
  readonly max: number;
  readonly label: string;
  readonly text: string;
}

export function ProgressBar({ value, max, label, text }: ProgressBarProps) {
  const percent = max > 0 ? Math.min(100, Math.round((value / max) * 100)) : 0;
  return (
    <div className="od-progress">
      <div
        className="od-progress__track"
        role="progressbar"
        aria-label={label}
        aria-valuenow={percent}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuetext={text}
      >
        <div className="od-progress__fill" style={{ inlineSize: `${percent}%` }} />
      </div>
      <p className="od-progress__text">{text}</p>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Copy to clipboard
// ---------------------------------------------------------------------------

export function CopyButton({ value, label }: { value: string; label?: string }) {
  const { t } = useI18n();
  const { announce, alert } = useAnnouncer();
  const [copied, setCopied] = useState(false);
  return (
    <Button
      tone="quiet"
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(value);
          setCopied(true);
          announce(t('app.copied'));
          globalThis.setTimeout(() => setCopied(false), 2000);
        } catch {
          alert(t('app.copyFailed'));
        }
      }}
    >
      {copied ? t('app.copied') : (label ?? t('app.copy'))}
    </Button>
  );
}
