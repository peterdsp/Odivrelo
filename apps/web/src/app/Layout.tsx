import { useEffect, useRef } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useI18n } from '../i18n/I18nProvider';
import { BRAND } from '../brand/brand';
import { Wordmark } from '../brand/Marks';
import { useAdaptiveLayout } from '../hooks/useAdaptiveLayout';
import { useScrollRestoration } from '../hooks/useScrollRestoration';
import { useOnline } from '../hooks/useOnline';
import { DemoBanner } from './DemoBanner';
import { ErrorBoundary } from './ErrorBoundary';
import { UpdatePrompt } from './UpdatePrompt';
import { LayoutContext } from './layoutContext';
import type { MessageKey } from '../i18n/catalogues';

interface NavEntry {
  readonly to: string;
  readonly labelKey: MessageKey;
  readonly icon: string;
  readonly primary: boolean;
}

/** Primary entries get the compact tab bar; the rest live in the "more" list. */
const NAV: readonly NavEntry[] = [
  { to: '/search', labelKey: 'nav.search', icon: 'M10.5 10.5 14 14M11.5 7a4.5 4.5 0 1 1-9 0 4.5 4.5 0 0 1 9 0Z', primary: true },
  { to: '/saved', labelKey: 'nav.saved', icon: 'M4 2h8v12l-4-3-4 3V2Z', primary: true },
  { to: '/wallet', labelKey: 'nav.wallet', icon: 'M2.5 5h11v8h-11V5Zm0 3h11M10 10.5h2', primary: true },
  { to: '/operators', labelKey: 'nav.operators', icon: 'M4 12.5V6a2.5 2.5 0 0 1 5 0v4a2.5 2.5 0 0 0 5 0V3.5', primary: false },
  { to: '/stations', labelKey: 'nav.stations', icon: 'M8 1.8C5.8 1.8 4 3.6 4 5.8 4 9 8 14.2 8 14.2S12 9 12 5.8c0-2.2-1.8-4-4-4Zm0 5.4a1.6 1.6 0 1 1 0-3.2 1.6 1.6 0 0 1 0 3.2Z', primary: false },
  { to: '/settings', labelKey: 'nav.settings', icon: 'M8 10.2a2.2 2.2 0 1 0 0-4.4 2.2 2.2 0 0 0 0 4.4Zm5.4-2.2c0 .4 0 .8-.1 1.1l1.3 1-1.4 2.4-1.5-.6a5.5 5.5 0 0 1-1 .6L10.4 14H7.6l-.3-1.5a5.5 5.5 0 0 1-1-.6l-1.5.6L3.4 10.1l1.3-1a6 6 0 0 1 0-2.2l-1.3-1 1.4-2.4 1.5.6a5.5 5.5 0 0 1 1-.6L7.6 2h2.8l.3 1.5c.35.16.68.36 1 .6l1.5-.6 1.4 2.4-1.3 1c.06.36.1.73.1 1.1Z', primary: false },
];

function NavIcon({ path }: { path: string }) {
  return (
    <svg viewBox="0 0 16 16" width="20" height="20" aria-hidden="true" focusable="false" className="od-nav__icon">
      <path d={path} fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

/**
 * The application shell.
 *
 * Landmarks are real: one `banner`, one `navigation` per group with its own
 * accessible name, one `main` with the skip-link target, one `contentinfo`. The
 * heading order starts at the `h1` each route renders, and the shell never emits
 * an `h1` of its own so there is exactly one per page.
 *
 * Layout comes from `useAdaptiveLayout`, which measures the container rather than
 * guessing from a device width, and the result is published on `data-mode` for
 * CSS and through context for the routes that need a real two-pane decision.
 */
export function Layout() {
  const { t } = useI18n();
  const layout = useAdaptiveLayout();
  const online = useOnline();
  const location = useLocation();
  const mainRef = useRef<HTMLElement | null>(null);
  const firstRender = useRef(true);
  useScrollRestoration();

  // On a route change, move focus to `main` so a keyboard or screen-reader user
  // is not left at the top of the navigation on every navigation. Not on the
  // very first render, where the reader has not navigated anywhere yet.
  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false;
      return;
    }
    mainRef.current?.focus({ preventScroll: true });
  }, [location.pathname]);

  const primary = NAV.filter((entry) => entry.primary);
  const secondary = NAV.filter((entry) => !entry.primary);

  return (
    <LayoutContext.Provider value={layout}>
      {/* Reads the four safe-area insets so the hook can report real numbers. */}
      <div id="od-safe-area-probe" aria-hidden="true" />

      <div className="od-shell" ref={layout.containerRef} data-mode={layout.mode} data-two-pane={layout.twoPane ? '' : undefined}>
        <a className="od-skip-link" href="#main">
          {t('app.skipToContent')}
        </a>

        <header className="od-header" role="banner">
          <div className="od-header__inner">
            <NavLink to="/search" className="od-header__brand" aria-label={BRAND.name}>
              <Wordmark height={30} />
            </NavLink>
            {layout.mode !== 'compact' ? (
              <nav className="od-nav od-nav--inline" aria-label={t('app.primaryNav')}>
                <ul className="od-nav__list">
                  {NAV.map((entry) => (
                    <li key={entry.to}>
                      <NavLink to={entry.to} className="od-nav__link">
                        <NavIcon path={entry.icon} />
                        <span>{t(entry.labelKey)}</span>
                      </NavLink>
                    </li>
                  ))}
                </ul>
              </nav>
            ) : null}
            {!online ? (
              <p className="od-header__offline" role="status">
                {t('state.offlineTitle')}
              </p>
            ) : null}
          </div>
        </header>

        <DemoBanner />

        <main className="od-main" id="main" ref={mainRef} tabIndex={-1}>
          {/*
            Scoped to the route, so a failure in one page keeps the header, the
            demonstration notice, the navigation and the footer. The boundary in
            main.tsx remains for a failure in the shell itself.

            Keyed on the path so that navigating away from a broken route clears
            the error instead of leaving the reader stuck on it.
          */}
          <ErrorBoundary key={location.pathname} insideMain>
            <Outlet />
          </ErrorBoundary>
        </main>

        {layout.mode === 'compact' ? (
          <>
            <nav className="od-nav od-nav--bar" aria-label={t('app.primaryNav')}>
              <ul className="od-nav__list">
                {primary.map((entry) => (
                  <li key={entry.to}>
                    <NavLink to={entry.to} className="od-nav__link">
                      <NavIcon path={entry.icon} />
                      <span>{t(entry.labelKey)}</span>
                    </NavLink>
                  </li>
                ))}
              </ul>
            </nav>
            <nav className="od-nav od-nav--utility" aria-label={t('app.utilityNav')}>
              <ul className="od-nav__list">
                {secondary.map((entry) => (
                  <li key={entry.to}>
                    <NavLink to={entry.to} className="od-nav__link od-nav__link--text">
                      {t(entry.labelKey)}
                    </NavLink>
                  </li>
                ))}
              </ul>
            </nav>
          </>
        ) : null}

        <footer className="od-footer" role="contentinfo" aria-label={t('app.footer')}>
          <div className="od-footer__inner">
            <p className="od-footer__statement">{t('app.neverSellsTickets')}</p>
            <p className="od-footer__statement">{t('app.independence')}</p>
            <ul className="od-footer__links">
              <li>
                <NavLink to="/licences" className="od-link">
                  {t('nav.licences')}
                </NavLink>
              </li>
              <li>
                <NavLink to="/settings" className="od-link">
                  {t('nav.settings')}
                </NavLink>
              </li>
              <li>
                <a className="od-link" href={`mailto:${BRAND.supportEmail}`}>
                  {BRAND.supportEmail}
                </a>
              </li>
            </ul>
          </div>
        </footer>

        <UpdatePrompt />
      </div>
    </LayoutContext.Provider>
  );
}
