import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { LANGUAGES, type Language } from '../data/contract';
import { useI18n } from '../i18n/I18nProvider';
import type { MessageKey } from '../i18n/catalogues';
import { useHead } from '../hooks/useHead';
import { useData, useDataMode } from '../app/DataProvider';
import { useTheme, type ThemeChoice } from '../hooks/useTheme';
import { useLayout } from '../app/layoutContext';
import { Button, Card, Checkbox, CopyButton, Disclosure, Fact, FactList, Section } from '../components/primitives';
import { useAnnouncer } from '../components/Announcer';
import { formatBytes } from '../lib/digest';
import { clearOfflineData, clearEverything, measureStorage, type StorageUsage } from '../lib/db';
import { clearAllPrefs, storageAvailable as prefsAvailable } from '../lib/prefs';
import { notificationSupport, canDeliverInBackground } from '../features/reminders';
import { buildStamp, appConfig } from '../config';
import { BRAND } from '../brand/brand';

const LANGUAGE_LABELS: Readonly<Record<Language, MessageKey>> = {
  el: 'language.el',
  en: 'language.en',
  sq: 'language.sq',
};

const THEMES: readonly { value: ThemeChoice; key: MessageKey }[] = [
  { value: 'system', key: 'settings.themeSystem' },
  { value: 'light', key: 'settings.themeLight' },
  { value: 'dark', key: 'settings.themeDark' },
];

/**
 * Settings.
 *
 * The four build stamps the brief requires are all here and all real: the version,
 * the git commit the bundle was built from, the build time, and the id of the data
 * release actually being read. Diagnostics is the same information as copyable
 * text; it contains no token, no identifier of the reader, and nothing from the
 * travel wallet.
 *
 * The privacy wording is the canonical text from docs/beta/release/PRIVACY.md,
 * translated rather than paraphrased, including the line that browser storage can
 * be evicted so the wallet is not a backup.
 */
export function Settings() {
  const { t, language, setLanguage, formatDateTime } = useI18n();
  const { source, meta, manifest } = useData();
  const dataMode = useDataMode();
  const layout = useLayout();
  const { announce } = useAnnouncer();
  const { theme, setTheme, forceReducedMotion, setForceReducedMotion, underlineLinks, setUnderlineLinks } = useTheme();
  const [usage, setUsage] = useState<StorageUsage | null>(null);

  useHead({ title: t('meta.settings.title'), description: t('settings.privacy1'), path: '/settings', language, dataMode });

  const refresh = useCallback(async () => setUsage(await measureStorage()), []);
  useEffect(() => {
    void refresh();
  }, [refresh]);

  const releaseId = manifest.status === 'ready' ? manifest.value.releaseId : meta.status === 'ready' ? meta.value.releaseId : null;
  const publishedAt = manifest.status === 'ready' ? manifest.value.publishedAt : null;
  const permission = notificationSupport();

  const diagnostics = useMemo(() => {
    // Everything here is either a build constant or a capability flag. Nothing
    // identifies the reader, and nothing comes from the wallet or a saved trip.
    const lines = [
      `product: ${BRAND.name} web`,
      `version: ${buildStamp.version}`,
      `commit: ${buildStamp.commit}`,
      `builtAt: ${buildStamp.builtAt}`,
      `contract: ${BRAND.contractVersion}`,
      `dataMode: ${dataMode}`,
      `dataSource: ${source.kind}`,
      `dataOrigin: ${source.origin}`,
      `releaseId: ${releaseId ?? 'unknown'}`,
      `releasePublishedAt: ${publishedAt ?? 'unknown'}`,
      `language: ${language}`,
      `theme: ${theme}`,
      `layoutMode: ${layout.mode}`,
      `viewport: ${Math.round(layout.width)}x${Math.round(layout.height)} @ ${layout.rootFontSize}px root`,
      `twoPane: ${layout.twoPane}`,
      `viewportSegments: ${layout.segmentsSupported ? (layout.segments?.length ?? 1) : 'unsupported'}`,
      `reducedMotion: ${layout.prefersReducedMotion || forceReducedMotion}`,
      `localStorage: ${prefsAvailable() ? 'available' : 'unavailable'}`,
      `indexedDb: ${usage ? 'available' : 'unknown'}`,
      `packBytes: ${usage?.packBytes ?? 'unknown'}`,
      `walletItems: ${usage?.walletCount ?? 'unknown'}`,
      `storagePersisted: ${usage?.persisted ?? 'unknown'}`,
      `notifications: ${permission}`,
      `backgroundReminders: ${canDeliverInBackground() ? 'possible' : 'not available'}`,
      `serviceWorker: ${'serviceWorker' in globalThis.navigator ? 'supported' : 'unsupported'}`,
    ];
    return lines.join('\n');
  }, [
    dataMode,
    source.kind,
    source.origin,
    releaseId,
    publishedAt,
    language,
    theme,
    layout,
    forceReducedMotion,
    usage,
    permission,
  ]);

  return (
    <div className="od-page od-page--narrow">
      <h1 className="od-page__title">{t('settings.title')}</h1>

      {/* -- Language ------------------------------------------------------ */}
      <Section title={t('settings.language')} level={2} description={t('settings.languageHelp')}>
        <fieldset className="od-language-choice">
          <legend className="od-visually-hidden">{t('settings.language')}</legend>
          {LANGUAGES.map((code) => (
            <label key={code} className="od-language-choice__option">
              <input
                type="radio"
                name="settings-language"
                value={code}
                checked={language === code}
                onChange={() => {
                  setLanguage(code);
                  announce(t('a11y.languageChanged', { language: t(LANGUAGE_LABELS[code]) }));
                }}
              />
              <span lang={code}>{t(LANGUAGE_LABELS[code])}</span>
            </label>
          ))}
        </fieldset>
      </Section>

      {/* -- Appearance ---------------------------------------------------- */}
      <Section title={t('settings.appearance')} level={2}>
        <fieldset className="od-language-choice">
          <legend className="od-visually-hidden">{t('settings.appearance')}</legend>
          {THEMES.map((option) => (
            <label key={option.value} className="od-language-choice__option">
              <input
                type="radio"
                name="settings-theme"
                value={option.value}
                checked={theme === option.value}
                onChange={() => {
                  setTheme(option.value);
                  announce(t('a11y.themeChanged', { theme: t(option.key) }));
                }}
              />
              <span>{t(option.key)}</span>
            </label>
          ))}
        </fieldset>
      </Section>

      {/* -- Accessibility -------------------------------------------------- */}
      <Section title={t('settings.accessibility')} level={2}>
        <Checkbox
          id="setting-reduce-motion"
          label={t('settings.reduceMotion')}
          hint={t('settings.reduceMotionHelp')}
          checked={forceReducedMotion}
          onChange={(event) => setForceReducedMotion(event.target.checked)}
        />
        <Checkbox
          id="setting-underline"
          label={t('settings.underline')}
          checked={underlineLinks}
          onChange={(event) => setUnderlineLinks(event.target.checked)}
        />
      </Section>

      {/* -- Storage -------------------------------------------------------- */}
      <Section title={t('settings.storage')} level={2}>
        {usage ? (
          <FactList>
            <Fact label={t('settings.storagePacks')}>{formatBytes(usage.packBytes, language)}</Fact>
            <Fact label={t('settings.storageWallet')}>{formatBytes(usage.walletBytes, language)}</Fact>
            <Fact label={t('saved.tripsHeading')}>
              {t(
                usage.tripCount === 0
                  ? 'settings.storageTrips_zero'
                  : usage.tripCount === 1
                    ? 'settings.storageTrips_one'
                    : 'settings.storageTrips_other',
                { count: usage.tripCount },
              )}
            </Fact>
            <Fact label={t('settings.storageUsed', { used: '' }).trim()}>
              {usage.quotaBytes !== null && usage.usageBytes !== null
                ? t('settings.storageOf', {
                    used: formatBytes(usage.usageBytes, language),
                    quota: formatBytes(usage.quotaBytes, language),
                  })
                : t('settings.storageUnknown')}
            </Fact>
          </FactList>
        ) : null}
        <p className="od-notice od-notice--warning">{t('offline.evictionWarning')}</p>
        <div className="od-pack__actions">
          <Button
            tone="secondary"
            onClick={async () => {
              if (!globalThis.confirm(t('settings.clearOfflineConfirm'))) return;
              await clearOfflineData();
              announce(t('settings.cleared'));
              await refresh();
            }}
          >
            {t('settings.clearOffline')}
          </Button>
          <Button
            tone="danger"
            onClick={async () => {
              if (!globalThis.confirm(t('settings.clearEverythingConfirm'))) return;
              await clearEverything();
              clearAllPrefs();
              announce(t('settings.cleared'));
              await refresh();
            }}
          >
            {t('settings.clearEverything')}
          </Button>
        </div>
      </Section>

      {/* -- Privacy -------------------------------------------------------- */}
      <Section title={t('settings.privacy')} level={2}>
        <ul className="od-list od-list--check">
          <li>{t('settings.privacy1')}</li>
          <li>{t('settings.privacy2')}</li>
          <li>{t('settings.privacy3')}</li>
        </ul>
        <Card tone="muted">
          <p>{t('wallet.privacy2')}</p>
          <p className="od-notice od-notice--warning">{t('wallet.privacy4')}</p>
        </Card>
      </Section>

      {/* -- Notifications -------------------------------------------------- */}
      <Section title={t('settings.notifications')} level={2}>
        <FactList>
          <Fact label={t('settings.notifications')}>
            {permission === 'granted'
              ? t('app.yes')
              : permission === 'denied'
                ? t('reminders.permissionDenied')
                : permission === 'unsupported'
                  ? t('reminders.unsupported')
                  : t('app.unknown')}
          </Fact>
        </FactList>
        <p className="od-muted">{t('reminders.backgroundLimitationBody')}</p>
        <p className="od-muted">{t('reminders.privacy')}</p>
        <p>
          <Link to="/saved" className="od-link">
            {t('reminders.title')}
          </Link>
        </p>
      </Section>

      {/* -- Data sources --------------------------------------------------- */}
      <Section title={t('settings.dataSources')} level={2}>
        <FactList>
          <Fact label={t('settings.dataSourceKind')}>
            {source.kind === 'static' ? t('settings.dataSourceStatic') : t('settings.dataSourceHttp')}
          </Fact>
          <Fact label={t('settings.dataSourceOrigin')}>
            <span className="od-mono">{source.origin}</span>
          </Fact>
          <Fact label={t('coverage.state')}>
            {dataMode === 'demo' ? t('coverage.stateDemo') : t('coverage.stateCovered')}
          </Fact>
        </FactList>
        <p>
          <Link to="/coverage" className="od-link">
            {t('nav.coverage')}
          </Link>
        </p>
        {appConfig.apiBaseUrl ? <p className="od-mono od-muted">{appConfig.apiBaseUrl}</p> : null}
      </Section>

      {/* -- Support -------------------------------------------------------- */}
      <Section title={t('settings.support')} level={2}>
        <FactList>
          <Fact label={t('settings.supportEmail')}>
            <a className="od-link" href={`mailto:${BRAND.supportEmail}`}>
              {BRAND.supportEmail}
            </a>
          </Fact>
        </FactList>
        <p className="od-muted">{t('settings.diagnosticsHelp')}</p>
      </Section>

      {/* -- About and build ------------------------------------------------ */}
      <Section title={t('settings.about')} level={2}>
        <p>{t('settings.aboutBody')}</p>
        <p>{t('settings.markMeaning')}</p>
        <FactList>
          <Fact label={t('settings.version')}>
            <span className="od-mono">{buildStamp.version}</span>
          </Fact>
          <Fact label={t('settings.commit')}>
            <span className="od-mono">{buildStamp.commit}</span>
          </Fact>
          <Fact label={t('settings.builtAt')}>
            <span className="od-mono">{buildStamp.builtAt}</span>
          </Fact>
          <Fact label={t('settings.dataRelease')}>
            <span className="od-mono">{releaseId ?? t('app.unknown')}</span>
            {publishedAt ? <span className="od-muted"> {formatDateTime(publishedAt)}</span> : null}
          </Fact>
        </FactList>
        <p>
          <Link to="/licences" className="od-link">
            {t('settings.licences')}
          </Link>
        </p>
      </Section>

      {/* -- Diagnostics ---------------------------------------------------- */}
      <Section title={t('settings.diagnostics')} level={2} description={t('settings.diagnosticsHelp')}>
        <Disclosure summary={t('settings.diagnostics')}>
          <pre className="od-diagnostics">{diagnostics}</pre>
          <CopyButton value={diagnostics} label={t('settings.copyDiagnostics')} />
        </Disclosure>
      </Section>
    </div>
  );
}
