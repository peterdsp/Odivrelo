import { useMemo } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { isServiceDate } from '../data/contract';
import { useI18n } from '../i18n/I18nProvider';
import { useHead, organizationNode } from '../hooks/useHead';
import { useData, useDataMode } from '../app/DataProvider';
import { useAsync } from '../hooks/useAsync';
import { ButtonLink } from '../components/primitives';
import { StateBlock } from '../components/states';
import { JourneyDetailView } from '../features/JourneyDetailView';
import { decomposeJourneyId } from '../data/packQuery';

/**
 * A journey as its own page, reachable by a stable link.
 *
 * The identifier and the service date are both validated before anything is
 * requested, so a mistyped, truncated or expired link produces a page that
 * explains itself rather than a crash or a blank screen. Nothing here parses the
 * identifier for meaning: `decomposeJourneyId` only checks that it has the shape
 * this release's data source produces.
 */
export function Journey() {
  const { t, language, name } = useI18n();
  const { source } = useData();
  const dataMode = useDataMode();
  const params = useParams<{ id: string }>();
  const [query] = useSearchParams();

  const journeyId = params.id ?? '';
  const dateParam = query.get('date');
  const serviceDate = dateParam && isServiceDate(dateParam) ? dateParam : null;
  const shapeValid = useMemo(() => decomposeJourneyId(journeyId) !== null, [journeyId]);

  const loaded = useAsync(
    (signal) => source.journey(journeyId, serviceDate as string, signal),
    [source, journeyId, serviceDate],
    { enabled: shapeValid && serviceDate !== null },
  );
  const journey = loaded.state.status === 'ready' ? loaded.state.value.journey : null;

  useHead({
    title: journey
      ? t('meta.journey.title', {
          origin: name(journey.departure.stopName),
          destination: name(journey.arrival.stopName),
          date: journey.serviceDate,
        })
      : t('journey.notFoundTitle'),
    description: journey
      ? t('meta.journey.description', {
          operator: name(journey.operator.name),
          origin: name(journey.departure.stopName),
          departure: journey.departure.at,
          destination: name(journey.arrival.stopName),
          arrival: journey.arrival.at,
          date: journey.serviceDate,
        })
      : undefined,
    path: `/journey/${journeyId}${serviceDate ? `?date=${serviceDate}` : ''}`,
    language,
    dataMode,
    structuredData: journey
      ? {
          '@context': 'https://schema.org',
          '@type': 'BusTrip',
          provider: { '@type': 'Organization', name: name(journey.operator.name) },
          departureBusStop: { '@type': 'BusStop', name: name(journey.departure.stopName) },
          arrivalBusStop: { '@type': 'BusStop', name: name(journey.arrival.stopName) },
          departureTime: journey.departure.at,
          arrivalTime: journey.arrival.at,
          isAccessibleForFree: false,
          publisher: organizationNode(),
        }
      : undefined,
  });

  if (!serviceDate) {
    return (
      <div className="pv-page pv-page--narrow">
        <h1 className="pv-page__title">{t('state.invalidLinkTitle')}</h1>
        <StateBlock
          kind="invalid"
          title={t('search.invalidDate')}
          body={<p>{t('state.invalidLinkBody')}</p>}
          action={
            <ButtonLink tone="primary" to="/search">
              {t('app.goToSearch')}
            </ButtonLink>
          }
        />
      </div>
    );
  }

  return <JourneyDetailView journeyId={journeyId} serviceDate={serviceDate} headingLevel={1} />;
}
