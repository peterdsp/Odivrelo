import { useLocation } from 'react-router-dom';
import { useI18n } from '../i18n/I18nProvider';
import { useHead } from '../hooks/useHead';
import { useDataMode } from '../app/DataProvider';
import { ButtonLink, Card, Section } from '../components/primitives';
import { StateBlock } from '../components/states';

/**
 * Unknown or expired deep links.
 *
 * On GitHub Pages every unmatched path is served `404.html`, which boots the same
 * app, so this component is what a stale bookmark or a mistyped URL actually gets.
 * It says what probably happened, shows the path so the reader can see the typo,
 * and offers the routes that do exist. It never renders a blank page and never
 * pretends the link was fine.
 */
export function NotFound() {
  const { t, language } = useI18n();
  const dataMode = useDataMode();
  const location = useLocation();

  useHead({
    title: t('meta.notFound.title'),
    description: t('state.notFoundBody'),
    path: location.pathname,
    language,
    dataMode,
  });

  return (
    <div className="pv-page pv-page--narrow">
      <h1 className="pv-page__title">{t('state.notFoundTitle')}</h1>
      <StateBlock
        kind="not_found"
        headingLevel={2}
        title={t('state.notFoundTitle')}
        body={
          <>
            <p>{t('state.notFoundBody404')}</p>
            <p className="pv-mono pv-muted">{location.pathname}</p>
          </>
        }
        action={
          <ButtonLink tone="primary" to="/search">
            {t('app.goToSearch')}
          </ButtonLink>
        }
      />

      <Section title={t('app.primaryNav')} level={2}>
        <Card>
          <ul className="pv-list pv-list--plain">
            <li>
              <ButtonLink tone="quiet" to="/search">
                {t('nav.search')}
              </ButtonLink>
            </li>
            <li>
              <ButtonLink tone="quiet" to="/operators">
                {t('nav.operators')}
              </ButtonLink>
            </li>
            <li>
              <ButtonLink tone="quiet" to="/stations">
                {t('nav.stations')}
              </ButtonLink>
            </li>
            <li>
              <ButtonLink tone="quiet" to="/saved">
                {t('nav.saved')}
              </ButtonLink>
            </li>
            <li>
              <ButtonLink tone="quiet" to="/coverage">
                {t('nav.coverage')}
              </ButtonLink>
            </li>
            <li>
              <ButtonLink tone="quiet" to="/settings">
                {t('nav.settings')}
              </ButtonLink>
            </li>
          </ul>
        </Card>
      </Section>
    </div>
  );
}
