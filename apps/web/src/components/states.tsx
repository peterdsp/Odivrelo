import type { ReactNode } from 'react';
import { useI18n } from '../i18n/I18nProvider';
import { ContractError, type ErrorCode } from '../data/contract';
import { Button, ButtonLink } from './primitives';
import { useAnnounceOnChange } from './Announcer';

/**
 * Every non-happy state the app can be in, in one place.
 *
 * The list is exhaustive on purpose: loading, empty, stale, restricted,
 * unavailable, retry, permission denied, storage full, server error, offline,
 * update available and expired service all have a real rendering, so no screen
 * has to invent one, and none of them is a blank page.
 */

export type StateKind =
  | 'loading'
  | 'empty'
  | 'stale'
  | 'restricted'
  | 'unavailable'
  | 'offline'
  | 'server_error'
  | 'integrity'
  | 'release_mismatch'
  | 'not_found'
  | 'invalid'
  | 'permission_denied'
  | 'storage_full'
  | 'expired';

const TONE_FOR: Record<StateKind, 'info' | 'warning' | 'error' | 'neutral'> = {
  loading: 'neutral',
  empty: 'neutral',
  stale: 'warning',
  restricted: 'warning',
  unavailable: 'warning',
  offline: 'info',
  server_error: 'error',
  integrity: 'error',
  release_mismatch: 'error',
  not_found: 'neutral',
  invalid: 'error',
  permission_denied: 'warning',
  storage_full: 'error',
  expired: 'warning',
};

export interface StateBlockProps {
  readonly kind: StateKind;
  readonly title: string;
  readonly body?: ReactNode;
  readonly onRetry?: () => void;
  readonly retryLabel?: string;
  /** A second way forward when retrying is not the useful thing to do. */
  readonly action?: ReactNode;
  /** Announce the title when it appears. Errors are announced assertively. */
  readonly announce?: boolean;
  readonly headingLevel?: 2 | 3;
}

export function StateBlock({
  kind,
  title,
  body,
  onRetry,
  retryLabel,
  action,
  announce = true,
  headingLevel = 2,
}: StateBlockProps) {
  const { t } = useI18n();
  const tone = TONE_FOR[kind];
  const assertive = tone === 'error';
  useAnnounceOnChange(announce ? title : null, assertive);
  const Heading = headingLevel === 2 ? 'h2' : 'h3';
  return (
    <div className={`od-state od-state--${tone}`} data-state={kind}>
      {kind === 'loading' ? <span className="od-spinner" aria-hidden="true" /> : null}
      <Heading className="od-state__title">{title}</Heading>
      {body ? <div className="od-state__body">{body}</div> : null}
      {(onRetry || action) && (
        <div className="od-state__actions">
          {onRetry ? (
            <Button tone="primary" onClick={onRetry}>
              {retryLabel ?? t('app.retry')}
            </Button>
          ) : null}
          {action}
        </div>
      )}
    </div>
  );
}

/** The loading state, with the wording the app uses everywhere. */
export function Loading({ label }: { label?: string }) {
  const { t } = useI18n();
  return (
    <div className="od-state od-state--neutral" data-state="loading" aria-busy="true">
      <span className="od-spinner" aria-hidden="true" />
      <p className="od-state__title">{label ?? t('app.loadingData')}</p>
    </div>
  );
}

const CODE_TO_KIND: Record<ErrorCode, StateKind> = {
  not_found: 'not_found',
  invalid_request: 'invalid',
  unavailable: 'unavailable',
  release_mismatch: 'release_mismatch',
  unauthorized: 'restricted',
  offline: 'offline',
  integrity: 'integrity',
};

export interface DataErrorProps {
  readonly error: unknown;
  readonly onRetry?: () => void;
  /** Shown instead of the generic wording when the caller knows better. */
  readonly notFoundTitle?: string;
  readonly notFoundBody?: ReactNode;
  readonly headingLevel?: 2 | 3;
}

/**
 * Renders whatever a data source threw.
 *
 * A `ContractError` carries a code, so the reader gets the real reason. Anything
 * else is reported as a server error rather than dressed up as something the
 * app understands.
 */
export function DataError({ error, onRetry, notFoundTitle, notFoundBody, headingLevel = 2 }: DataErrorProps) {
  const { t } = useI18n();
  const kind: StateKind = error instanceof ContractError ? CODE_TO_KIND[error.code] : 'server_error';

  const copy: Record<StateKind, { title: string; body: ReactNode }> = {
    loading: { title: t('state.loading'), body: null },
    empty: { title: t('state.empty'), body: null },
    stale: { title: t('freshness.stale'), body: null },
    restricted: { title: t('state.restrictedTitle'), body: t('state.restrictedBody') },
    unavailable: { title: t('state.unavailableTitle'), body: t('state.unavailableBody') },
    offline: { title: t('state.offlineTitle'), body: t('state.offlineBody') },
    server_error: { title: t('state.serverErrorTitle'), body: t('state.serverErrorBody') },
    integrity: { title: t('state.integrityTitle'), body: t('state.integrityBody') },
    release_mismatch: { title: t('state.releaseMismatchTitle'), body: t('state.releaseMismatchBody') },
    not_found: { title: notFoundTitle ?? t('state.notFoundTitle'), body: notFoundBody ?? t('state.notFoundBody') },
    invalid: { title: t('state.invalidLinkTitle'), body: t('state.invalidLinkBody') },
    permission_denied: { title: t('state.permissionDeniedTitle'), body: null },
    storage_full: { title: t('state.storageFullTitle'), body: null },
    expired: { title: t('state.expiredTitle'), body: null },
  };

  const { title, body } = copy[kind];
  const detail = error instanceof Error ? error.message : String(error);
  const canRetry = kind !== 'not_found' && kind !== 'invalid' && kind !== 'restricted';

  return (
    <StateBlock
      kind={kind}
      title={title}
      headingLevel={headingLevel}
      body={
        <>
          {body ? <p>{body}</p> : null}
          {kind === 'release_mismatch' ? null : (
            <details className="od-state__detail">
              <summary>{t('app.errorDetail')}</summary>
              <p className="od-mono">{detail}</p>
            </details>
          )}
        </>
      }
      {...(canRetry && onRetry ? { onRetry } : {})}
      action={
        kind === 'release_mismatch' ? (
          <Button tone="primary" onClick={() => globalThis.location.reload()}>
            {t('state.reload')}
          </Button>
        ) : kind === 'not_found' || kind === 'invalid' ? (
          <ButtonLink tone="primary" to="/search">
            {t('app.goToSearch')}
          </ButtonLink>
        ) : null
      }
    />
  );
}
