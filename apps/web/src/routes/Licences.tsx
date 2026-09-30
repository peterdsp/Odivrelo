import { useI18n } from '../i18n/I18nProvider';
import { useHead } from '../hooks/useHead';
import { useData, useDataMode } from '../app/DataProvider';
import { useAsync } from '../hooks/useAsync';
import { Card, ExternalLink, Fact, FactList, Section } from '../components/primitives';
import { BRAND } from '../brand/brand';
import { provenanceView } from '../data/contract';

/**
 * Open-source notices and data licences.
 *
 * The list below is the actual runtime dependency set of this bundle. It is short
 * on purpose: no analytics, no crash reporter, no advertising SDK, no tag manager,
 * and no web font, so there is nothing here that phones anywhere.
 */
interface Notice {
  readonly name: string;
  readonly licence: string;
  readonly url: string;
  readonly role: string;
}

const RUNTIME: readonly Notice[] = [
  { name: 'React', licence: 'MIT', url: 'https://react.dev', role: 'User interface' },
  { name: 'React DOM', licence: 'MIT', url: 'https://react.dev', role: 'Rendering' },
  { name: 'React Router', licence: 'MIT', url: 'https://reactrouter.com', role: 'Routing and deep links' },
  { name: 'idb', licence: 'ISC', url: 'https://github.com/jakearchibald/idb', role: 'IndexedDB access' },
  { name: 'MapLibre GL JS', licence: 'BSD-3-Clause', url: 'https://maplibre.org', role: 'Route map, loaded only when a map is opened' },
  { name: 'Workbox', licence: 'MIT', url: 'https://developer.chrome.com/docs/workbox', role: 'Service worker and offline shell' },
];

const BUILD: readonly Notice[] = [
  { name: 'Vite', licence: 'MIT', url: 'https://vite.dev', role: 'Build' },
  { name: 'TypeScript', licence: 'Apache-2.0', url: 'https://www.typescriptlang.org', role: 'Types' },
  { name: 'Vitest', licence: 'MIT', url: 'https://vitest.dev', role: 'Unit and integration tests' },
  { name: 'Playwright', licence: 'Apache-2.0', url: 'https://playwright.dev', role: 'End-to-end tests' },
  { name: 'axe-core', licence: 'MPL-2.0', url: 'https://github.com/dequelabs/axe-core', role: 'Automated accessibility checks' },
  { name: 'ESLint', licence: 'MIT', url: 'https://eslint.org', role: 'Static analysis' },
];

export function Licences() {
  const { t, language } = useI18n();
  const { source } = useData();
  const dataMode = useDataMode();
  const sources = useAsync((signal) => source.sources(signal), [source]);

  useHead({ title: t('meta.licences.title'), description: t('licences.intro'), path: '/licences', language, dataMode });

  return (
    <div className="pv-page pv-page--narrow">
      <h1 className="pv-page__title">{t('licences.title')}</h1>
      <p className="pv-page__lede">{t('licences.intro')}</p>

      <Section title={t('licences.software')} level={2}>
        <ul className="pv-list pv-list--plain">
          {[...RUNTIME, ...BUILD].map((notice) => (
            <li key={notice.name}>
              <ExternalLink href={notice.url} accessibleLabel={notice.name}>
                {notice.name}
              </ExternalLink>
              {' · '}
              <span className="pv-mono">{notice.licence}</span>
              {' · '}
              <span className="pv-muted">{notice.role}</span>
            </li>
          ))}
        </ul>
      </Section>

      <Section title={t('licences.data')} level={2}>
        {sources.state.status === 'ready' ? (
          <ul className="pv-list pv-list--plain">
            {sources.state.value.sources.map((entry, index) => {
              const view = provenanceView(entry, index);
              return (
                <li key={view.key}>
                  {view.url ? (
                    <ExternalLink href={view.url} accessibleLabel={view.name}>
                      {view.name}
                    </ExternalLink>
                  ) : (
                    view.name
                  )}
                  {' · '}
                  <span className="pv-mono">{view.licence}</span>
                </li>
              );
            })}
          </ul>
        ) : (
          <p className="pv-muted">{t('app.loading')}</p>
        )}
      </Section>

      <Section title={t('licences.fonts')} level={2}>
        <p>{t('licences.fontsBody')}</p>
      </Section>

      <Card tone="muted">
        <FactList>
          <Fact label={t('settings.support')}>
            <a className="pv-link" href={`mailto:${BRAND.supportEmail}`}>
              {BRAND.supportEmail}
            </a>
          </Fact>
        </FactList>
        <p>{t('settings.privacy1')}</p>
      </Card>
    </div>
  );
}
