import { useEffect, useMemo, useRef, useState, type CSSProperties, type KeyboardEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { LANGUAGES, type Language } from '../data/contract';
import { useI18n } from '../i18n/I18nProvider';
import { useHead } from '../hooks/useHead';
import { useAsync } from '../hooks/useAsync';
import { useData, useDataMode } from '../app/DataProvider';
import { Button } from '../components/primitives';
import { markOnboarded } from '../features/onboarding';
import { useAnnouncer } from '../components/Announcer';
import type { MessageKey } from '../i18n/catalogues';
import { BOARD_ROUTES, type BoardRoute } from './welcomeBoard';

const LANGUAGE_KEYS: Readonly<Record<Language, MessageKey>> = {
  el: 'language.el',
  en: 'language.en',
  sq: 'language.sq',
};

const PLACE_KEYS: Readonly<Record<BoardRoute['id'], MessageKey>> = {
  patras: 'welcome.place.patras',
  tiryns: 'welcome.place.tiryns',
  amfissa: 'welcome.place.amfissa',
};

/** The sheet lists this many departures; the rest are counted, not dropped silently. */
const BOARD_ROWS = 8;

/** Screens from the App Store set. There is no Albanian set, so Albanian shows the English one. */
const shotLocale = (language: Language) => (language === 'el' ? 'el' : 'en');

interface Fact {
  readonly name: MessageKey;
  readonly value: MessageKey;
  readonly body?: MessageKey;
}

const FACT_GROUPS: readonly { readonly title: MessageKey; readonly facts: readonly Fact[] }[] = [
  {
    title: 'welcome.factsBefore',
    facts: [
      { name: 'welcome.fact.operators', value: 'welcome.fact.operatorsValue', body: 'welcome.fact.operatorsBody' },
      { name: 'welcome.fact.stop', value: 'welcome.fact.stopValue', body: 'welcome.fact.stopBody' },
      { name: 'welcome.fact.fresh', value: 'welcome.fact.freshValue', body: 'welcome.fact.freshBody' },
    ],
  },
  {
    title: 'welcome.factsRoad',
    facts: [
      { name: 'welcome.fact.offline', value: 'welcome.fact.offlineValue', body: 'welcome.fact.offlineBody' },
      { name: 'welcome.fact.language', value: 'welcome.fact.languageValue', body: 'welcome.fact.languageBody' },
    ],
  },
  {
    title: 'welcome.factsNever',
    facts: [
      { name: 'welcome.fact.tickets', value: 'welcome.fact.ticketsValue' },
      { name: 'welcome.fact.account', value: 'welcome.fact.accountValue' },
      { name: 'welcome.fact.trips', value: 'welcome.fact.tripsValue' },
    ],
  },
];

/**
 * Parts of each section rise into place once, as the section enters. The
 * content is in the document and visible from the first paint; only a browser
 * that runs this and allows motion ever sees it hidden, and only until it is
 * in view.
 */
function useRise() {
  const ref = useRef<HTMLDivElement | null>(null);
  useEffect(() => {
    const root = ref.current;
    if (!root || typeof IntersectionObserver === 'undefined') return;
    if (globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return;
    const parts = Array.from(root.querySelectorAll<HTMLElement>('[data-rise]'));
    const viewport = globalThis.innerHeight || 800;
    const later = parts.filter((part) => part.getBoundingClientRect().top > viewport * 0.9);
    for (const part of later) part.classList.add('is-waiting');
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          entry.target.classList.remove('is-waiting');
          entry.target.classList.add('is-risen');
          observer.unobserve(entry.target);
        }
      },
      { rootMargin: '0px 0px -8% 0px' },
    );
    for (const part of later) observer.observe(part);
    return () => observer.disconnect();
  }, []);
  return ref;
}

/**
 * First launch, and the page a visitor lands on.
 *
 * No account, no permission prompt, no network-only trap: this page is part of
 * the app shell, so it renders from cache with no connection at all. It asks for
 * a language, shows what the product does with one real day of its own data,
 * says plainly that it never sells tickets, and then gets out of the way.
 *
 * A reader who arrived from a deep link has already been sent to their
 * destination and is never made to pass through here.
 */
export function Welcome() {
  const { t, language, setLanguage } = useI18n();
  const { announce } = useAnnouncer();
  const navigate = useNavigate();
  const dataMode = useDataMode();
  const { source } = useData();
  const pageRef = useRise();
  const [routeId, setRouteId] = useState<BoardRoute['id']>('patras');
  const tabRefs = useRef<Record<string, HTMLButtonElement | null>>({});

  useHead({
    title: t('welcome.title'),
    description: t('meta.home.description'),
    path: '/welcome',
    language,
    dataMode,
  });

  const operators = useAsync((signal) => source.operators(signal), [source]);
  const operatorCount = operators.state.status === 'ready' ? operators.state.value.operators.length : null;

  const route = BOARD_ROUTES.find((candidate) => candidate.id === routeId) ?? BOARD_ROUTES[0]!;
  const rows = route.rows.slice(0, BOARD_ROWS);
  const more = route.rows.length - rows.length;
  const fare = useMemo(
    () => new Intl.NumberFormat(language === 'sq' ? 'sq-AL' : language === 'el' ? 'el-GR' : 'en-IE', { style: 'currency', currency: 'EUR' }),
    [language],
  );
  const shots = shotLocale(language);

  const go = () => {
    markOnboarded();
    navigate('/search', { replace: true });
  };

  const onTabKey = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    const step = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0;
    if (!step) return;
    event.preventDefault();
    const next = BOARD_ROUTES[(index + step + BOARD_ROUTES.length) % BOARD_ROUTES.length]!;
    setRouteId(next.id);
    tabRefs.current[next.id]?.focus();
  };

  return (
    <div className="od-landing" ref={pageRef}>
      <section className="od-landing__hero" aria-labelledby="od-landing-title">
        <div className="od-sheet">

          <h1 className="od-landing__title" id="od-landing-title">
            {t('welcome.headline')}
          </h1>
          <p className="od-landing__lede">{t('welcome.intro')}</p>
          <div className="od-landing__start">
            <Button tone="primary" onClick={go}>
              {t('welcome.start')}
            </Button>
          </div>
          <fieldset className="od-landing__languages">
            <legend className="od-visually-hidden">{t('welcome.chooseLanguage')}</legend>
            {LANGUAGES.map((code) => (
              <label key={code} className="od-landing__language">
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

          <div className="od-board" data-ui>
            <h2 className="od-board__title">{t('welcome.boardTitle')}</h2>
            <div className="od-board__tabs" role="tablist" aria-label={t('welcome.routesLabel')}>
              {BOARD_ROUTES.map((candidate, index) => (
                <button
                  key={candidate.id}
                  ref={(node) => {
                    tabRefs.current[candidate.id] = node;
                  }}
                  type="button"
                  role="tab"
                  id={`od-board-tab-${candidate.id}`}
                  aria-selected={candidate.id === route.id}
                  aria-controls="od-board-panel"
                  tabIndex={candidate.id === route.id ? 0 : -1}
                  className="od-board__tab"
                  onClick={() => setRouteId(candidate.id)}
                  onKeyDown={(event) => onTabKey(event, index)}
                >
                  {t(PLACE_KEYS[candidate.id])}
                </button>
              ))}
            </div>
            <div className="od-board__panel" role="tabpanel" id="od-board-panel" aria-labelledby={`od-board-tab-${route.id}`}>
              <p className="od-board__summary">
                {t('welcome.boardSummary', { operator: route.operator[language], count: route.rows.length })}
              </p>
              <table className="od-board__table">
                <thead>
                  <tr>
                    <th scope="col">{t('welcome.col.depart')}</th>
                    <th scope="col">{t('welcome.col.arrive')}</th>
                    <th scope="col">{t('welcome.col.from')}</th>
                    <th scope="col" className="od-board__fare">
                      {t('welcome.col.fare')}
                    </th>
                  </tr>
                </thead>
                <tbody key={route.id}>
                  {rows.map((row, index) => (
                    <tr key={`${row.depart}-${row.arrive}`} style={{ '--od-row': index } as CSSProperties}>
                      <td className="od-board__time">{row.depart}</td>
                      <td>{row.arrive}</td>
                      <td>{t(row.terminal === 'liosion' ? 'welcome.terminal.liosion' : 'welcome.terminal.kifisos')}</td>
                      <td className="od-board__fare">{fare.format(row.fare)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <p className="od-board__note">
                {route.id === 'amfissa' ? t('welcome.liosionNote') : more > 0 ? t('welcome.boardMore', { count: more }) : null}{' '}
                {t('welcome.boardNote')}
              </p>
            </div>
          </div>
        </div>
      </section>

      <section className="od-landing__stop" aria-labelledby="od-landing-stop">
        <figure className="od-landing__plate" data-rise>
          <img
            src={`/welcome/board-${shots}-1200.webp`}
            srcSet={`/welcome/board-${shots}-640.webp 640w, /welcome/board-${shots}-1200.webp 1200w`}
            sizes="(min-width: 900px) 46vw, 92vw"
            width={1200}
            height={618}
            loading="lazy"
            decoding="async"
            alt={t('welcome.stopAlt')}
          />
        </figure>
        <div className="od-landing__stop-copy">
          <h2 className="od-landing__heading" id="od-landing-stop" data-rise>
            {t('welcome.stopTitle')}
          </h2>
          <p className="od-landing__body" data-rise>
            {t('welcome.stopBody')}
          </p>
          <div className="od-landing__cells" data-rise>
            <button type="button" className="od-landing__cell od-landing__cell--ink" onClick={go}>
              {t('welcome.start')}
            </button>
            <Link to="/stations" className="od-landing__cell" onClick={() => markOnboarded()}>
              {t('welcome.browseStations')}
            </Link>
          </div>
        </div>
      </section>

      <section className="od-landing__facts" aria-labelledby="od-landing-facts">
        <div className="od-sheet od-sheet--list" data-rise>
          <h2 className="od-landing__heading od-landing__heading--centre" id="od-landing-facts">
            {t('welcome.factsTitle')}
          </h2>
          {FACT_GROUPS.map((group) => (
            <div className="od-facts__group" key={group.title}>
              <h3 className="od-facts__group-title">{t(group.title)}</h3>
              <dl className="od-facts__list">
                {group.facts.map((fact) => {
                  const value =
                    fact.value === 'welcome.fact.operatorsValue'
                      ? operatorCount !== null
                        ? t(fact.value, { count: operatorCount })
                        : ''
                      : t(fact.value);
                  return (
                    <div className="od-facts__item" key={fact.name}>
                      <dt>{t(fact.name)}</dt>
                      <dd className="od-facts__value">{value}</dd>
                      {fact.body ? <dd className="od-facts__body">{t(fact.body)}</dd> : null}
                    </div>
                  );
                })}
              </dl>
            </div>
          ))}
        </div>
      </section>

      <section className="od-landing__steps" aria-labelledby="od-landing-steps">
        <h2 className="od-landing__heading" id="od-landing-steps" data-rise>
          {t('welcome.stepsTitle')}
        </h2>
        <ol className="od-steps">
          <li className="od-steps__item od-steps__item--tall" data-rise>
            <img
              src={`/welcome/search-${shots}-900.webp`}
              srcSet={`/welcome/search-${shots}-480.webp 480w, /welcome/search-${shots}-900.webp 900w`}
              sizes="(min-width: 900px) 30vw, 80vw"
              width={900}
              height={1650}
              loading="lazy"
              decoding="async"
              alt={t('welcome.step1Alt')}
            />
            <p>{t('welcome.step1')}</p>
          </li>
          <li className="od-steps__item" data-rise>
            <img
              src={`/welcome/results-${shots}-1200.webp`}
              srcSet={`/welcome/results-${shots}-640.webp 640w, /welcome/results-${shots}-1200.webp 1200w`}
              sizes="(min-width: 900px) 40vw, 92vw"
              width={1200}
              height={618}
              loading="lazy"
              decoding="async"
              alt={t('welcome.step2Alt')}
            />
            <p>{t('welcome.step2')}</p>
          </li>
          <li className="od-steps__item" data-rise>
            <img
              src={`/welcome/buy-${shots}-1200.webp`}
              srcSet={`/welcome/buy-${shots}-640.webp 640w, /welcome/buy-${shots}-1200.webp 1200w`}
              sizes="(min-width: 900px) 40vw, 92vw"
              width={1200}
              height={618}
              loading="lazy"
              decoding="async"
              alt={t('welcome.step3Alt')}
            />
            <p>{t('welcome.step3')}</p>
          </li>
        </ol>
      </section>

      <section className="od-landing__cta" aria-labelledby="od-landing-cta">
        <Link to="/search" replace className="od-landing__cta-link" onClick={() => markOnboarded()}>
          <p className="od-landing__cta-line">{t('welcome.ctaLine')}</p>
          <svg className="od-landing__cta-arrow" viewBox="0 0 48 48" width="48" height="48" aria-hidden="true" focusable="false">
            <path d="M12 36 36 12M16 12h20v20" fill="none" stroke="currentColor" strokeWidth="4" strokeLinecap="square" />
          </svg>
          <h2 className="od-landing__cta-word" id="od-landing-cta">
            {t('welcome.ctaWord')}
          </h2>
        </Link>
      </section>

      <section className="od-landing__close" aria-labelledby="od-landing-more">
        <figure className="od-landing__photo">
          <img
            src="/welcome/kifisos-1600.webp"
            srcSet="/welcome/kifisos-800.webp 800w, /welcome/kifisos-1600.webp 1600w"
            sizes="(min-width: 900px) 50vw, 100vw"
            width={1600}
            height={1073}
            loading="lazy"
            decoding="async"
            alt={t('welcome.photoAlt')}
          />
          <figcaption>
            {t('welcome.photoCaption')}{' '}
            <a className="od-landing__credit" href="https://commons.wikimedia.org/w/index.php?curid=77589055" target="_blank" rel="noreferrer">
              {t('welcome.photoCredit')}
            </a>
          </figcaption>
        </figure>
        <div className="od-landing__menu">
          <h2 className="od-landing__heading" id="od-landing-more" data-rise>
            {t('welcome.moreTitle')}
          </h2>
          <ul className="od-landing__links" data-rise>
            {(
              [
                ['/search', 'nav.search'],
                ['/stations', 'nav.stations'],
                ['/operators', 'nav.operators'],
                ['/saved', 'nav.saved'],
                ['/coverage', 'nav.coverage'],
              ] as const
            ).map(([to, key]) => (
              <li key={to}>
                <Link to={to} onClick={() => markOnboarded()}>
                  {t(key)}
                </Link>
              </li>
            ))}
          </ul>
        </div>
      </section>
    </div>
  );
}
