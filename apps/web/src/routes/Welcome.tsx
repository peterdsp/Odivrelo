import { useNavigate } from 'react-router-dom';
import { LANGUAGES, type Language } from '../data/contract';
import { DepartureBoard } from '../components/DepartureBoard';
import { GreekTravelImage } from '../components/GreekTravelImage';
import { COACH_PICTURE, ROAD_PICTURE, TERMINAL_PICTURE } from '../data/pictures';
import { useI18n } from '../i18n/I18nProvider';
import { Mark } from '../brand/Marks';
import { useHead } from '../hooks/useHead';
import { useDataMode } from '../app/DataProvider';
import { Button, Card, Section } from '../components/primitives';
import { markOnboarded } from '../features/onboarding';
import { useAnnouncer } from '../components/Announcer';
import type { MessageKey } from '../i18n/catalogues';

const DOES: readonly MessageKey[] = ['welcome.does1', 'welcome.does2', 'welcome.does3', 'welcome.does4', 'welcome.does5'];
const DOES_NOT: readonly MessageKey[] = ['welcome.doesNot1', 'welcome.doesNot2', 'welcome.doesNot3', 'welcome.doesNot4'];
const LANGUAGE_KEYS: Readonly<Record<Language, MessageKey>> = {
  el: 'language.el',
  en: 'language.en',
  sq: 'language.sq',
};

/**
 * First launch.
 *
 * No account, no permission prompt, no network-only trap: this page is part of
 * the app shell, so it renders from cache with no connection at all. It asks for
 * a language, states plainly what the product does and does not do (including
 * that it never sells tickets), and then gets out of the way.
 *
 * It can also be skipped. A reader who arrived from a deep link has already been
 * sent to their destination and is never made to pass through here.
 */
export function Welcome() {
  const { t, language, setLanguage } = useI18n();
  const { announce } = useAnnouncer();
  const navigate = useNavigate();
  const dataMode = useDataMode();

  useHead({
    title: t('welcome.title'),
    description: t('meta.home.description'),
    path: '/welcome',
    language,
    dataMode,
  });

  const go = () => {
    markOnboarded();
    navigate('/search', { replace: true });
  };

  // The full measure rather than the narrow one, so the page shares the
  // header's margins instead of sitting inside them, and the board has the
  // width an object needs.
  return (
    <div className="od-page">
      <div className="od-welcome__hero">
        <div className="od-welcome__hero-copy">
          <Mark size={56} decorative />
          {/*
            The title is the tagline, so printing BRAND.tagline under it said the
            same words twice. The sentence below now carries the plain statement
            of what this is, which is what the first screen owes a stranger.
          */}
          <h1 className="od-welcome__title">{t('welcome.title')}</h1>
          <p className="od-welcome__intro">{t('welcome.intro')}</p>
        </div>
        {/*
          The object the page is built as, in place of a stock street
          photograph that was loading broken at tablet and phone widths and
          showed nothing of what Odivrelo does. Every row is a real departure
          from the published release, and the last column is the claim no coach
          timetable makes: when that row was last checked.
        */}
        <DepartureBoard />
      </div>

      {/*
        The world the board describes: the terminal where a reader stands, and
        the road the coach takes. Real photographs from Wikimedia Commons, under
        their own credits, never presented as Odivrelo's own.
      */}
      <div className="od-welcome__world">
        <GreekTravelImage alt={t('welcome.terminalAlt')} {...TERMINAL_PICTURE} />
        <GreekTravelImage alt={t('welcome.roadAlt')} {...ROAD_PICTURE} />
        <GreekTravelImage alt={t('welcome.coachAlt')} {...COACH_PICTURE} />
      </div>

      <Section title={t('welcome.chooseLanguage')} description={t('welcome.languageHelp')} className="od-welcome__languages">
        <fieldset className="od-language-choice">
          <legend className="od-visually-hidden">{t('welcome.chooseLanguage')}</legend>
          {LANGUAGES.map((code) => (
            <label key={code} className="od-language-choice__option">
              <input
                type="radio"
                name="language"
                value={code}
                checked={language === code}
                onChange={() => {
                  setLanguage(code);
                  announce(t('a11y.languageChanged', { language: t(LANGUAGE_KEYS[code]) }));
                }}
              />
              <span lang={code}>{t(LANGUAGE_KEYS[code])}</span>
            </label>
          ))}
        </fieldset>
      </Section>

      <div className="od-welcome__promises">
        <Card as="section" tone="muted">
          <h2 className="od-welcome__promiseTitle">{t('welcome.doesTitle')}</h2>
          <ul className="od-list od-list--check">
            {DOES.map((key) => (
              <li key={key}>{t(key)}</li>
            ))}
          </ul>
        </Card>
        <Card as="section" tone="muted">
          <h2 className="od-welcome__promiseTitle">{t('welcome.doesNotTitle')}</h2>
          <ul className="od-list od-list--cross">
            {DOES_NOT.map((key) => (
              <li key={key}>{t(key)}</li>
            ))}
          </ul>
        </Card>
      </div>

      <div className="od-welcome__actions">
        <Button tone="primary" onClick={go} full>
          {t('welcome.start')}
        </Button>
      </div>
    </div>
  );
}
