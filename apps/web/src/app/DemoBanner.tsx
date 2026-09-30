import { Link } from 'react-router-dom';
import { useI18n } from '../i18n/I18nProvider';
import { useDataMode } from './DataProvider';
import { Disclosure } from '../components/primitives';

/**
 * The demonstration-data notice.
 *
 * It is deliberately not dismissible. Every departure on the site is invented,
 * and a reader who scrolled past a toast an hour ago would have no way of knowing
 * that.
 *
 * It is a bare `<aside>`, which is a complementary landmark, rather than a
 * `role="note"`: `note` is not a landmark, so the banner would have sat outside
 * every landmark on the page and a screen-reader user navigating by landmark
 * would have skipped the one notice they most need. It carries its own accessible
 * name, appears on every page in whichever of the three languages is active, and
 * disappears on its own the moment the release declares `dataMode: "real"`, with
 * no other change anywhere.
 */
export function DemoBanner() {
  const { t } = useI18n();
  const mode = useDataMode();
  if (mode !== 'demo') return null;
  return (
    <aside className="pv-demo-banner" aria-label={t('demo.title')} data-testid="demo-banner">
      <div className="pv-demo-banner__inner">
        <p className="pv-demo-banner__title">
          <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true" focusable="false">
            <path
              d="M8 5v4.5M8 11.6v.2M8 1.8 1.4 13.2h13.2L8 1.8Z"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
          {t('demo.title')}
        </p>
        <p className="pv-demo-banner__body">{t('demo.body')}</p>
        <Disclosure summary={t('demo.why')}>
          <p>{t('demo.whyBody')}</p>
          <p>
            <Link to="/coverage" className="pv-link">
              {t('nav.coverage')}
            </Link>
          </p>
        </Disclosure>
      </div>
    </aside>
  );
}
