import { useId, useMemo, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import type { Place } from '../data/contract';
import { isServiceDate, provenanceView, safeHref } from '../data/contract';
import { useI18n } from '../i18n/I18nProvider';
import { useHead, organizationNode } from '../hooks/useHead';
import { useData, useDataMode } from '../app/DataProvider';
import { useAsync } from '../hooks/useAsync';
import { Card, ExternalLink, Fact, FactList, Section } from '../components/primitives';
import { DataError, Loading, StateBlock } from '../components/states';
import { CoverageBadge, ReviewStateBadge, RightsBadge, StepFreeBadge, TimeQualityBadge } from '../components/quality';
import { ServiceDateField } from '../components/ServiceDateField';
import { FavoriteToggle } from '../features/FavoriteToggle';
import { todayServiceDate } from '../lib/time';
import { BRAND } from '../brand/brand';
import { serviceDatesOf } from '../features/offlineGroups';
import { StationNetworkMap } from '../components/StationNetworkMap';
import { GreekTravelImage } from '../components/GreekTravelImage';
import { COACH_PICTURE } from '../data/pictures';

/** Directory of every terminal and boarding point in the release. */
export function Stations() {
  const { t, language, name } = useI18n();
  const { source } = useData();
  const dataMode = useDataMode();
  /*
   * The whole place pack, not a page of it. StaticPackSource already holds
   * every place in memory and `limit` only slices the array, so asking for all
   * of them costs no extra network. Asking for 500 cost something worse: the
   * slice is in name order, so the directory could only ever rank the front of
   * the alphabet. Tripoli, the busiest terminal in the release at 75 boarding
   * points, was not in the window and could not be offered.
   */
  const places = useAsync((signal) => source.places('', Number.MAX_SAFE_INTEGER, signal), [source]);

  const [query, setQuery] = useState('');
  const filterId = useId();

  const grouped = useMemo(() => {
    if (places.state.status !== 'ready') return [];
    const all = places.state.value.places;
    // One pass to gather children by parent, rather than a scan of every place
    // for every terminal. Over the whole release that difference is thousands
    // of terminals times nineteen thousand places, which is not something to
    // do while somebody waits for a directory.
    const childrenOf = new Map<string, Place[]>();
    for (const place of all) {
      if (!place.parentId) continue;
      const bucket = childrenOf.get(place.parentId);
      if (bucket) bucket.push(place);
      else childrenOf.set(place.parentId, [place]);
    }
    return all
      .filter((place) => place.kind === 'stop_place')
      .map((terminal) => ({ terminal, children: childrenOf.get(terminal.id) ?? [] }));
  }, [places.state]);

  /*
   * The release publishes a national network, so this page is a directory, not
   * a list to read top to bottom. Rendering every terminal produced a page
   * 155,000 pixels tall, which is not a page anybody scrolls. The whole set
   * stays reachable by typing, and a bounded number shows until then.
   *
   * Nothing is hidden from a crawler by this: the prerendered body is the boot
   * shell, and the indexable content is the per-station pages, one for each of
   * the stations in the release.
   */
  const needle = query.trim().toLocaleLowerCase(language);
  const matches = useMemo(() => {
    if (needle.length === 0) return grouped;
    return grouped.filter(({ terminal, children }) =>
      [name(terminal.name), terminal.municipality ?? '', ...children.map((child) => name(child.name))]
        .join(' ')
        .toLocaleLowerCase(language)
        .includes(needle),
    );
  }, [grouped, needle, language, name]);

  const FIRST_VIEW = 40;

  /*
   * Which forty, when nobody has typed. By name the page opened on 11.500,
   * 16XLM, 21 KM, 43 km, 6 and 6.5 km: the KTEL source data's own noise, which
   * sorts to the front of the alphabet and carries no coach. Of the places in
   * the release, under a twentieth have any service, so forty taken off the
   * top of a name sort is forty dead ends.
   *
   * Ordering the bounded view by how much service a terminal has puts the
   * country's real stations on the first screen. Typing still searches the
   * whole directory, and an uncovered station still appears with the badge
   * that says so, because "this stop has no service" is a real answer.
   */
  const leading = useMemo(() => {
    if (needle.length > 0) return matches;
    return [...matches].sort((a, b) => {
      const served = Number(b.terminal.coverage === 'covered') - Number(a.terminal.coverage === 'covered');
      if (served !== 0) return served;
      return b.terminal.boardingPointCount - a.terminal.boardingPointCount;
    });
  }, [matches, needle]);

  const shown = needle.length > 0 ? matches : leading.slice(0, FIRST_VIEW);
  const hiddenCount = matches.length - shown.length;

  useHead({
    title: t('meta.stations.title'),
    description: t('stations.intro'),
    path: '/stations',
    language,
    dataMode,
    indexable: true,
    structuredData:
      grouped.length > 0
        ? {
            '@context': 'https://schema.org',
            '@type': 'ItemList',
            name: t('stations.title'),
            itemListElement: grouped.map(({ terminal }, index) => ({
              '@type': 'ListItem',
              position: index + 1,
              item: { '@type': 'BusStation', name: name(terminal.name), url: `${BRAND.url}/stations/${terminal.id}` },
            })),
          }
        : undefined,
  });

  return (
    <div className="od-page od-page--narrow">
      <h1 className="od-page__title">{t('stations.title')}</h1>
      <p className="od-page__lede">{t('stations.intro')}</p>

      <GreekTravelImage className="od-stations__image" alt={t('stations.imageAlt')} {...COACH_PICTURE} />

      {places.state.status === 'loading' ? <Loading /> : null}
      {places.state.status === 'error' ? <DataError error={places.state.error} onRetry={places.reload} /> : null}

      {places.state.status === 'ready' ? <StationNetworkMap places={places.state.value.places} /> : null}

      {places.state.status === 'ready' ? (
        <div className="od-field">
          <label className="od-field__label" htmlFor={filterId}>
            {t('stations.filterLabel')}
          </label>
          <input
            id={filterId}
            className="od-input"
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={t('stations.filterPlaceholder')}
            autoComplete="off"
          />
          <p className="od-field__hint" role="status">
            {needle.length > 0
              ? t('stations.filterMatches', { shown: matches.length, total: grouped.length })
              : t('stations.filterShowing', { shown: shown.length, total: grouped.length })}
          </p>
        </div>
      ) : null}

      {places.state.status === 'ready' && needle.length > 0 && matches.length === 0 ? (
        <StateBlock kind="empty" title={t('stations.filterNoneTitle')} body={t('stations.filterNoneBody')} />
      ) : null}

      {/*
        A directory is a list, not forty page regions. Each terminal used to be
        a `Section`, which renders a landmark, so a screen reader met forty
        sibling landmarks on one page and axe reported four of them sharing a
        name, because the source publishes more than one terminal called
        "11500". Headings inside list items carry the same structure without
        claiming each row is a major area of the page.
      */}
      <ul className="od-list od-list--plain od-stations__list">
      {shown.map(({ terminal, children }) => (
        <li key={terminal.id} id={`terminal-${terminal.id}`}>
          <h2 className="od-section__title">{name(terminal.name)}</h2>
          <p>
            <Link to={`/stations/${encodeURIComponent(terminal.id)}`} className="od-link">
              {t('station.title', { name: name(terminal.name) })}
            </Link>{' '}
            <CoverageBadge state={terminal.coverage} />
          </p>
          <p className="od-muted">{terminal.municipality || t('app.unknown')}</p>
          {children.length === 0 ? (
            <p className="od-muted">{t('search.boardingPointCount_zero')}</p>
          ) : (
            <ul className="od-list od-list--plain">
              {children.map((child) => (
                <li key={child.id}>
                  <Link to={`/stations/${encodeURIComponent(child.id)}`} className="od-link">
                    {name(child.name)}
                  </Link>
                  {child.bay ? <span className="od-muted"> {t('journey.boardingBay', { bay: child.bay })}</span> : null}{' '}
                  <StepFreeBadge stepFree={child.stepFree ?? null} />
                </li>
              ))}
            </ul>
          )}
        </li>
      ))}
      </ul>

      {hiddenCount > 0 ? (
        <p className="od-muted">{t('stations.filterHidden', { count: hiddenCount })}</p>
      ) : null}
    </div>
  );
}

/**
 * A single station or boarding point.
 *
 * The date is part of the URL, so a link to "departures from here on this day" is
 * stable and shareable, and changing the date does not lose the page.
 */
export function StationDetail() {
  const { t, language, name, formatClock, formatDateTime, formatNumber } = useI18n();
  const { source, manifest } = useData();
  const dataMode = useDataMode();
  const params = useParams<{ id: string }>();
  const [query, setQuery] = useSearchParams();
  const id = params.id ?? '';

  const dateParam = query.get('date');
  const serviceDate = dateParam && isServiceDate(dateParam) ? dateParam : todayServiceDate();

  const loaded = useAsync((signal) => source.stop(id, serviceDate, signal), [source, id, serviceDate], {
    enabled: id.length > 0,
  });
  const place = loaded.state.status === 'ready' ? loaded.state.value : null;
  const result = place;
  const rawPlaceName = place ? name(place.name) : '';
  /*
   * Some stops in the release carry no real name: the field is present but
   * holds a dash in every language. Rendering that faithfully gave the page a
   * heading of "-", which tells a reader nothing and leaves the page with no
   * usable title at all for a screen reader.
   *
   * Saying the stop is unnamed is both truer and usable. The identifier and
   * the coordinates below still say which stop this is.
   */
  const placeIsNamed = /[\p{L}\p{N}]/u.test(rawPlaceName);
  const placeName = placeIsNamed ? rawPlaceName : t('station.unnamed');
  const manifestValue = manifest.status === 'ready' ? manifest.value : null;
  const releaseDates = useMemo(() => (manifestValue ? serviceDatesOf(manifestValue) : []), [manifestValue]);

  useHead({
    title: place ? t('meta.station.title', { name: placeName }) : t('station.notFoundTitle'),
    description: place ? t('meta.station.description', { name: placeName }) : undefined,
    path: `/stations/${id}?date=${serviceDate}`,
    language,
    dataMode,
    indexable: place !== null,
    structuredData: place
      ? {
          '@context': 'https://schema.org',
          '@type': place.kind === 'stop_place' ? 'BusStation' : 'BusStop',
          name: placeName,
          ...(place.municipality ? { address: { '@type': 'PostalAddress', addressLocality: place.municipality } } : {}),
          geo: { '@type': 'GeoCoordinates', latitude: place.latitude, longitude: place.longitude },
          subjectOf: organizationNode(),
        }
      : undefined,
  });

  /*
   * A page still needs its level-one heading while it loads.
   *
   * These branches returned a bare spinner, so for as long as the data took
   * there was no h1 anywhere on the page: a screen reader landing mid-load
   * had nothing to orient by, and axe flagged it on whichever page the timing
   * happened to catch.
   */
  if (loaded.state.status === 'loading') {
    return (
      <div className="od-page od-page--narrow">
        <h1 className="od-page__title">{t('meta.stations.title')}</h1>
        <Loading />
      </div>
    );
  }
  if (loaded.state.status === 'error') {
    return (
      <div className="od-page od-page--narrow">
        <h1 className="od-page__title">{t('station.notFoundTitle')}</h1>
        <DataError
          error={loaded.state.error}
          onRetry={loaded.reload}
          notFoundTitle={t('station.notFoundTitle')}
          notFoundBody={<p>{t('station.notFoundBody')}</p>}
        />
      </div>
    );
  }
  if (!result || !place) return null;

  return (
    <div className="od-page od-page--narrow">
      <nav aria-label={t('a11y.breadcrumb')} className="od-breadcrumb">
        <Link to="/stations" className="od-link">
          {t('stations.title')}
        </Link>
        {result.terminal ? (
          <>
            <span aria-hidden="true"> / </span>
            <Link to={`/stations/${encodeURIComponent(result.terminal.id)}`} className="od-link">
              {name(result.terminal.name)}
            </Link>
          </>
        ) : null}
      </nav>

      <h1 className="od-page__title">{placeName}</h1>
      <p className="od-page__lede">
        <CoverageBadge state={place.coverage} />{' '}
        {place.kind === 'stop' ? <StepFreeBadge stepFree={place.stepFree} /> : null}{' '}
        {place.reviewState ? <ReviewStateBadge state={place.reviewState} /> : null}
      </p>
      {result.terminal ? <p className="od-muted">{t('station.partOf', { name: name(result.terminal.name) })}</p> : null}

      <FavoriteToggle kind="place" targetId={place.id} label={place.name} />

      <FactList>
        <Fact label={t('station.municipality')}>{place.municipality || t('app.unknown')}</Fact>
        {place.bay ? <Fact label={t('journey.boardingBay', { bay: '' }).trim()}>{place.bay}</Fact> : null}
        <Fact label={t('station.coordinates')}>
          {t('station.coordinatesValue', {
            latitude: formatNumber(place.latitude, { maximumFractionDigits: 5 }),
            longitude: formatNumber(place.longitude, { maximumFractionDigits: 5 }),
          })}
        </Fact>
        {place.reviewedAt ? (
          <Fact label={t('journey.boardingReviewed', { when: '' }).trim()}>
            <time dateTime={place.reviewedAt}>{formatDateTime(place.reviewedAt)}</time>
          </Fact>
        ) : null}
      </FactList>

      {place.instructions ? (
        <Section title={t('station.instructions')} level={2}>
          <Card tone="accent">
            <p>{name(place.instructions)}</p>
          </Card>
        </Section>
      ) : null}

      {result.boardingPoints.length > 0 ? (
        <Section title={t('station.boardingPoints')} level={2}>
          <ul className="od-list od-list--plain">
            {result.boardingPoints.map((point, index) => {
              const pointId = point.stopId ?? point.id ?? `${result.id}-bp-${index}`;
              return (
                <li key={pointId}>
                  <Link to={`/stations/${encodeURIComponent(pointId)}`} className="od-link">
                    {name(point.name)}
                  </Link>
                  {point.bay ? <span className="od-muted"> {t('journey.boardingBay', { bay: point.bay })}</span> : null}{' '}
                  <StepFreeBadge stepFree={point.stepFree ?? null} />
                </li>
              );
            })}
          </ul>
        </Section>
      ) : null}

      {result.operators.length > 0 ? (
        <Section title={t('station.operators')} level={2}>
          <ul className="od-list od-list--plain">
            {result.operators.map((operator) => (
              <li key={operator.id}>
                <Link to={`/operators/${encodeURIComponent(operator.id)}`} className="od-link">
                  {name(operator.name)}
                </Link>
              </li>
            ))}
          </ul>
        </Section>
      ) : null}

      <Section title={t('station.departures', { date: serviceDate })} level={2}>
        <ServiceDateField
          value={serviceDate}
          onChange={(next) => {
            const params2 = new URLSearchParams(query);
            params2.set('date', next);
            setQuery(params2, { replace: true });
          }}
          min={releaseDates[0] ?? serviceDate}
          max={releaseDates.at(-1) ?? serviceDate}
        />
        {result.departures.length === 0 ? (
          <StateBlock
            kind="empty"
            headingLevel={3}
            title={t('state.empty')}
            body={<p>{t('station.noDepartures', { date: serviceDate })}</p>}
            announce={false}
          />
        ) : (
          <ul className="od-list od-list--plain od-departures">
            {result.departures.map((departure) => {
              const at = departure.departureAt ?? departure.arrivalAt;
              return (
                <li key={departure.journeyId}>
                  <Link
                    to={`/journey/${encodeURIComponent(departure.journeyId)}?date=${departure.serviceDate}`}
                    className="od-link"
                  >
                    {at ? <time className="od-data" dateTime={at}>{formatClock(at)}</time> : t('journey.noTime')}{' '}
                    {t('station.towards', { destination: name(departure.headsign) })}
                  </Link>{' '}
                  <span className="od-muted">{name(departure.operator.name)}</span>{' '}
                  <TimeQualityBadge quality={departure.timeQuality} />
                </li>
              );
            })}
          </ul>
        )}
      </Section>

      <Section title={t('journey.provenance')} level={2}>
        <ul className="od-list od-list--plain od-provenance">
          {result.provenance.map((entry, index) => {
            const view = provenanceView(entry, index);
            return (
              <li key={view.key}>
                <FactList>
                  <Fact label={t('journey.source')}>
                    {view.url ? (
                      <ExternalLink href={view.url} accessibleLabel={view.name}>
                        {view.name}
                      </ExternalLink>
                    ) : (
                      view.name
                    )}
                  </Fact>
                  {view.checkedAt ? (
                    <Fact label={t('journey.retrievedAt')}>
                      <time dateTime={view.checkedAt}>{formatDateTime(view.checkedAt)}</time>
                    </Fact>
                  ) : null}
                  <Fact label={t('journey.rights')}>
                    <RightsBadge status={view.rightsStatus} />
                  </Fact>
                  <Fact label={t('journey.licence')}>{view.licence}</Fact>
                </FactList>
              </li>
            );
          })}
        </ul>
        {safeHref(place.correctionUrl) ? (
          <p>
            <ExternalLink href={safeHref(place.correctionUrl)!} accessibleLabel={t('journey.correction')}>
              {t('journey.correction')}
            </ExternalLink>
          </p>
        ) : null}
      </Section>
    </div>
  );
}
