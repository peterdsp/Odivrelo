import { useMemo } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
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

/** Directory of every terminal and boarding point in the release. */
export function Stations() {
  const { t, language, name } = useI18n();
  const { source } = useData();
  const dataMode = useDataMode();
  const places = useAsync((signal) => source.places('', 500, signal), [source]);

  const grouped = useMemo(() => {
    if (places.state.status !== 'ready') return [];
    const all = places.state.value.places;
    return all
      .filter((place) => place.kind === 'stop_place')
      .map((terminal) => ({ terminal, children: all.filter((child) => child.parentId === terminal.id) }));
  }, [places.state]);

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
    <div className="pv-page pv-page--narrow">
      <h1 className="pv-page__title">{t('stations.title')}</h1>
      <p className="pv-page__lede">{t('stations.intro')}</p>

      {places.state.status === 'loading' ? <Loading /> : null}
      {places.state.status === 'error' ? <DataError error={places.state.error} onRetry={places.reload} /> : null}

      {grouped.map(({ terminal, children }) => (
        <Section key={terminal.id} title={name(terminal.name)} level={2} id={`terminal-${terminal.id}`}>
          <p>
            <Link to={`/stations/${encodeURIComponent(terminal.id)}`} className="pv-link">
              {t('station.title', { name: name(terminal.name) })}
            </Link>{' '}
            <CoverageBadge state={terminal.coverage} />
          </p>
          <p className="pv-muted">{terminal.municipality}</p>
          {children.length === 0 ? (
            <p className="pv-muted">{t('search.boardingPointCount_zero')}</p>
          ) : (
            <ul className="pv-list pv-list--plain">
              {children.map((child) => (
                <li key={child.id}>
                  <Link to={`/stations/${encodeURIComponent(child.id)}`} className="pv-link">
                    {name(child.name)}
                  </Link>
                  {child.bay ? <span className="pv-muted"> {t('journey.boardingBay', { bay: child.bay })}</span> : null}{' '}
                  <StepFreeBadge stepFree={child.stepFree ?? null} />
                </li>
              ))}
            </ul>
          )}
        </Section>
      ))}
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
  const placeName = place ? name(place.name) : '';
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
          address: { '@type': 'PostalAddress', addressLocality: place.municipality },
          geo: { '@type': 'GeoCoordinates', latitude: place.latitude, longitude: place.longitude },
          subjectOf: organizationNode(),
        }
      : undefined,
  });

  if (loaded.state.status === 'loading') return <Loading />;
  if (loaded.state.status === 'error') {
    return (
      <div className="pv-page pv-page--narrow">
        <h1 className="pv-page__title">{t('station.notFoundTitle')}</h1>
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
    <div className="pv-page pv-page--narrow">
      <nav aria-label={t('a11y.breadcrumb')} className="pv-breadcrumb">
        <Link to="/stations" className="pv-link">
          {t('stations.title')}
        </Link>
        {result.terminal ? (
          <>
            <span aria-hidden="true"> / </span>
            <Link to={`/stations/${encodeURIComponent(result.terminal.id)}`} className="pv-link">
              {name(result.terminal.name)}
            </Link>
          </>
        ) : null}
      </nav>

      <h1 className="pv-page__title">{placeName}</h1>
      <p className="pv-page__lede">
        <CoverageBadge state={place.coverage} />{' '}
        {place.kind === 'stop' ? <StepFreeBadge stepFree={place.stepFree} /> : null}{' '}
        {place.reviewState ? <ReviewStateBadge state={place.reviewState} /> : null}
      </p>
      {result.terminal ? <p className="pv-muted">{t('station.partOf', { name: name(result.terminal.name) })}</p> : null}

      <FavoriteToggle kind="place" targetId={place.id} label={place.name} />

      <FactList>
        <Fact label={t('station.municipality')}>{place.municipality}</Fact>
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
          <ul className="pv-list pv-list--plain">
            {result.boardingPoints.map((point, index) => {
              const pointId = point.stopId ?? point.id ?? `${result.id}-bp-${index}`;
              return (
                <li key={pointId}>
                  <Link to={`/stations/${encodeURIComponent(pointId)}`} className="pv-link">
                    {name(point.name)}
                  </Link>
                  {point.bay ? <span className="pv-muted"> {t('journey.boardingBay', { bay: point.bay })}</span> : null}{' '}
                  <StepFreeBadge stepFree={point.stepFree ?? null} />
                </li>
              );
            })}
          </ul>
        </Section>
      ) : null}

      {result.operators.length > 0 ? (
        <Section title={t('station.operators')} level={2}>
          <ul className="pv-list pv-list--plain">
            {result.operators.map((operator) => (
              <li key={operator.id}>
                <Link to={`/operators/${encodeURIComponent(operator.id)}`} className="pv-link">
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
          <ul className="pv-list pv-list--plain pv-departures">
            {result.departures.map((departure) => {
              const at = departure.departureAt ?? departure.arrivalAt;
              return (
                <li key={departure.journeyId}>
                  <Link
                    to={`/journey/${encodeURIComponent(departure.journeyId)}?date=${departure.serviceDate}`}
                    className="pv-link"
                  >
                    {at ? <time dateTime={at}>{formatClock(at)}</time> : t('journey.noTime')}{' '}
                    {t('station.towards', { destination: name(departure.headsign) })}
                  </Link>{' '}
                  <span className="pv-muted">{name(departure.operator.name)}</span>{' '}
                  <TimeQualityBadge quality={departure.timeQuality} />
                </li>
              );
            })}
          </ul>
        )}
      </Section>

      <Section title={t('journey.provenance')} level={2}>
        <ul className="pv-list pv-list--plain pv-provenance">
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
