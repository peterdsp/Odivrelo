import { lazy, Suspense, useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { provenanceView, safeHref, type JourneyDetail } from '../data/contract';
import { useI18n } from '../i18n/I18nProvider';
import { useData } from '../app/DataProvider';
import { useLayout } from '../app/layoutContext';
import { useAsync } from '../hooks/useAsync';
import { Badge, Button, ButtonLink, Card, Fact, FactList, Section } from '../components/primitives';
import { DataError, Loading, StateBlock } from '../components/states';
import {
  ConfidenceBadge,
  FreshnessBadge,
  GeometryBadge,
  LiveTrackingNotice,
  OvernightBadge,
  ReviewStateBadge,
  RightsBadge,
  StepFreeBadge,
  TimeQualityBadge,
} from '../components/quality';
import { StopTimeline } from '../components/StopTimeline';
import { useAnnouncer } from '../components/Announcer';
import { isPastServiceDate } from '../lib/time';
import { loadSavedTrips, removeTrip, saveTrip, savedTripId, StorageFullError } from './savedTrips';
import { decomposeJourneyId } from '../data/packQuery';

// MapLibre is the largest dependency in the app. Loading it here, and only when
// the reader asks for the map, keeps it off every other route.
const MapPanel = lazy(async () => ({ default: (await import('../components/MapPanel')).MapPanel }));

export interface JourneyDetailViewProps {
  readonly journeyId: string;
  readonly serviceDate: string;
  readonly headingLevel?: 1 | 2;
  /** True when rendered inside the results two-pane layout rather than as its own page. */
  readonly embedded?: boolean;
  /** A saved snapshot to render instead of reading the release, for offline use. */
  readonly snapshot?: JourneyDetail | null;
  readonly snapshotReleaseId?: string;
}

/**
 * Journey detail.
 *
 * Every required fact is here in text: the operating date, the operator, the full
 * ordered stop list with its pickup and drop-off rules, the exact boarding point
 * with the bay and step-free state when they are known and an explicit "not
 * checked" when they are not, the conditions of travel, the complete provenance
 * with rights and licence per source, the freshness, and the geometry confidence.
 *
 * The map is an optional addition on top of that, never a substitute for it.
 */
export function JourneyDetailView({
  journeyId,
  serviceDate,
  headingLevel = 1,
  embedded = false,
  snapshot = null,
  snapshotReleaseId,
}: JourneyDetailViewProps) {
  const { t, name, formatClock, formatDateTime, formatDuration, formatMoney, formatServiceDate } = useI18n();
  const { source, storageAvailable } = useData();
  const layout = useLayout();
  const { announce, alert } = useAnnouncer();
  const [showMap, setShowMap] = useState(false);
  const [savedId, setSavedId] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);

  const valid = decomposeJourneyId(journeyId) !== null;

  const loaded = useAsync(
    (signal) => source.journey(journeyId, serviceDate, signal),
    [source, journeyId, serviceDate],
    { enabled: snapshot === null && valid },
  );

  const journey: JourneyDetail | null = snapshot ?? (loaded.state.status === 'ready' ? loaded.state.value.journey : null);
  const releaseId = snapshot ? (snapshotReleaseId ?? '') : loaded.state.status === 'ready' ? loaded.state.value.releaseId : '';

  const refreshSaved = useCallback(async () => {
    if (!storageAvailable) return;
    const trips = await loadSavedTrips();
    const id = savedTripId(journeyId, serviceDate);
    setSavedId(trips.some((trip) => trip.id === id) ? id : null);
  }, [journeyId, serviceDate, storageAvailable]);

  useEffect(() => {
    void refreshSaved();
  }, [refreshSaved]);

  const Heading = headingLevel === 1 ? 'h1' : 'h2';
  const sectionLevel = headingLevel === 1 ? 2 : 3;

  if (!valid) {
    return (
      <div className={embedded ? 'od-detail od-detail--embedded' : 'od-page od-page--narrow'}>
        <StateBlock
          kind="invalid"
          title={t('journey.invalidTitle')}
          body={<p>{t('journey.invalidBody')}</p>}
          action={
            <ButtonLink tone="primary" to="/search">
              {t('app.goToSearch')}
            </ButtonLink>
          }
        />
      </div>
    );
  }

  if (!journey) {
    if (loaded.state.status === 'loading') {
      return embedded ? <Loading /> : (
        <div className="od-page od-page--narrow">
          <Heading className="od-page__title">{t('app.loadingData')}</Heading>
          <Loading />
        </div>
      );
    }
    if (loaded.state.status === 'error') {
      const error = (
        <DataError
          error={loaded.state.error}
          onRetry={loaded.reload}
          notFoundTitle={t('journey.notFoundTitle')}
          notFoundBody={<p>{t('journey.notFoundBody')}</p>}
          headingLevel={headingLevel === 1 ? 2 : 3}
        />
      );
      // A page that fails is still a page: it needs its own level-one heading, or
      // a screen-reader user landing on a broken deep link has nothing to orient
      // by.
      return embedded ? (
        <div className="od-detail od-detail--embedded">{error}</div>
      ) : (
        <div className="od-page od-page--narrow">
          <Heading className="od-page__title">{t('journey.notFoundTitle')}</Heading>
          {error}
        </div>
      );
    }
    return null;
  }

  const expired = isPastServiceDate(journey.serviceDate);
  const bookingHref = `/journey/${encodeURIComponent(journey.id)}/booking?date=${journey.serviceDate}`;

  const toggleSave = async () => {
    setSaveError(null);
    try {
      if (savedId) {
        await removeTrip(savedId);
        setSavedId(null);
        announce(t('saved.removed'));
      } else {
        await saveTrip({ detail: journey, releaseId });
        setSavedId(savedTripId(journey.id, journey.serviceDate));
        announce(t('journey.savedTrip'));
      }
    } catch (error) {
      const message = error instanceof StorageFullError ? t('offline.storageFullBody') : t('saved.storageUnavailable');
      setSaveError(message);
      alert(message);
    }
  };

  return (
    <article className={embedded ? 'od-detail od-detail--embedded' : 'od-page od-page--narrow od-detail'}>
      <Heading className="od-page__title">
        {t('journey.title', { origin: name(journey.departure.stopName), destination: name(journey.arrival.stopName) })}
      </Heading>

      <p className="od-detail__times">
        <time dateTime={journey.departure.at}>{formatClock(journey.departure.at)}</time>
        <span aria-hidden="true"> {'→'} </span>
        <time dateTime={journey.arrival.at}>{formatClock(journey.arrival.at)}</time>
        <span className="od-detail__duration"> {formatDuration(journey.durationMinutes)}</span>
      </p>

      <p className="od-detail__badges">
        {journey.crossesMidnight ? <OvernightBadge /> : null}
        <FreshnessBadge freshness={journey.freshness} />
        <ConfidenceBadge confidence={journey.confidence} />
        {expired ? (
          <Badge tone="warning" icon="alert">
            {t('results.expiredBadge')}
          </Badge>
        ) : null}
      </p>

      {expired ? (
        <StateBlock
          kind="expired"
          headingLevel={sectionLevel === 2 ? 2 : 3}
          title={t('results.expiredTitle')}
          body={<p>{t('results.expiredBody', { date: journey.serviceDate })}</p>}
          announce={false}
        />
      ) : null}

      <FactList className="od-detail__facts">
        <Fact label={t('journey.operatingDate')}>
          <time dateTime={journey.serviceDate}>{formatServiceDate(journey.serviceDate)}</time>
          {journey.crossesMidnight ? (
            <span className="od-detail__note"> {t('results.overnightExplain', { date: journey.serviceDate })}</span>
          ) : null}
        </Fact>
        <Fact label={t('journey.operator')}>
          <Link to={`/operators/${encodeURIComponent(journey.operator.id)}`} className="od-link">
            {name(journey.operator.name)}
          </Link>
        </Fact>
        {journey.routeName ? <Fact label={t('journey.route')}>{name(journey.routeName)}</Fact> : null}
        <Fact label={t('results.departs')}>
          <time dateTime={journey.departure.at}>{formatClock(journey.departure.at)}</time>{' '}
          <TimeQualityBadge quality={journey.departure.quality} />
        </Fact>
        <Fact label={t('results.arrives')}>
          <time dateTime={journey.arrival.at}>{formatClock(journey.arrival.at)}</time>{' '}
          <TimeQualityBadge quality={journey.arrival.quality} />
        </Fact>
        <Fact label={t('results.fare')}>
          {journey.fare ? (
            <>
              {formatMoney(journey.fare.amount, journey.fare.currency)}
              {journey.fare.isIndicative ? <span className="od-detail__note"> {t('results.fareIndicative')}</span> : null}
            </>
          ) : (
            <>
              {t('results.fareUnknown')}
              <span className="od-detail__note"> {t('results.fareUnknownHelp')}</span>
            </>
          )}
        </Fact>
      </FactList>

      <div className="od-detail__actions">
        <ButtonLink tone="primary" to={bookingHref}>
          {t('booking.title')}
        </ButtonLink>
        {storageAvailable ? (
          <Button tone="secondary" onClick={() => void toggleSave()} aria-pressed={savedId !== null}>
            {savedId ? t('journey.unsaveTrip') : t('journey.saveTrip')}
          </Button>
        ) : (
          <Button tone="secondary" unavailableReason={t('saved.storageUnavailable')}>
            {t('journey.saveTrip')}
          </Button>
        )}
      </div>
      <p className="od-detail__note">{t('journey.saveTripHelp')}</p>
      {saveError ? <p className="od-field__error">{saveError}</p> : null}
      <p className="od-detail__note od-detail__note--strong">{t('app.neverSellsTickets')}</p>

      {/* -- Boarding point ------------------------------------------------- */}
      <Section title={t('journey.boarding')} level={sectionLevel} id="boarding">
        <Card tone="accent">
          <p className="od-boarding__name">{name(journey.boardingPoint.name)}</p>
          <p className="od-boarding__terminal">
            {t('journey.boardingAt', { terminal: name(journey.boardingPoint.terminalName) })}
          </p>
          <p className="od-boarding__bay">
            {journey.boardingPoint.bay
              ? t('journey.boardingBay', { bay: journey.boardingPoint.bay })
              : t('journey.boardingNoBay')}
          </p>
          <p className="od-boarding__badges">
            <StepFreeBadge stepFree={journey.boardingPoint.stepFree} />
            <ReviewStateBadge state={journey.boardingPoint.reviewState} />
          </p>
          {journey.boardingPoint.instructions ? (
            <p className="od-boarding__instructions">{name(journey.boardingPoint.instructions)}</p>
          ) : null}
          <p className="od-boarding__review">
            {journey.boardingPoint.reviewedAt
              ? t('journey.boardingReviewed', { when: formatDateTime(journey.boardingPoint.reviewedAt) })
              : t('journey.boardingNotReviewed')}
          </p>
          <p>
            <Link to={`/stations/${encodeURIComponent(journey.boardingPoint.stopId)}`} className="od-link">
              {t('station.boardingPoints')}
            </Link>
          </p>
        </Card>
      </Section>

      {/* -- Live tracking, which does not exist in this release ------------ */}
      <LiveTrackingNotice />

      {/* -- Stops, with the map as an optional addition -------------------- */}
      <Section
        title={t('journey.stops')}
        description={t('journey.stopsHelp')}
        level={sectionLevel}
        id="stops"
        actions={
          <Button tone="quiet" onClick={() => setShowMap((v) => !v)} aria-pressed={showMap} aria-controls="journey-map">
            {showMap ? t('journey.hideMap') : t('journey.showMap')}
          </Button>
        }
      >
        <p className="od-detail__note">
          <GeometryBadge confidence={journey.geometry?.confidence ?? 'unverified'} />
        </p>
        <div id="journey-map">
          {showMap ? (
            <Suspense fallback={<Loading label={t('journey.mapTitle')} />}>
              <MapPanel
                stops={journey.stops}
                geometry={journey.geometry}
                boardingStopId={journey.boardingPoint.stopId}
                reducedMotion={layout.prefersReducedMotion}
              />
            </Suspense>
          ) : null}
        </div>
        <StopTimeline stops={journey.stops} boardingStopId={journey.boardingPoint.stopId} sectionLevel={sectionLevel} />
      </Section>

      {/* -- Restrictions -------------------------------------------------- */}
      {journey.restrictions.length > 0 ? (
        <Section title={t('journey.restrictions')} level={sectionLevel} id="restrictions">
          <ul className="od-list od-list--plain">
            {journey.restrictions.map((restriction) => (
              <li key={restriction.code}>{name(restriction.text)}</li>
            ))}
          </ul>
        </Section>
      ) : null}

      {/* -- Provenance ---------------------------------------------------- */}
      <Section title={t('journey.provenance')} description={t('journey.provenanceHelp')} level={sectionLevel} id="provenance">
        <ul className="od-list od-list--plain od-provenance">
          {journey.provenance.map((entry, index) => {
            const view = provenanceView(entry, index);
            return (
              <li key={view.key}>
                <FactList>
                  <Fact label={t('journey.source')}>
                    {view.url ? (
                      <a className="od-link" href={view.url} target="_blank" rel="noopener noreferrer external">
                        {view.name}
                      </a>
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
        {safeHref(journey.correctionUrl) ? (
          <p>
            <a
              className="od-link"
              href={safeHref(journey.correctionUrl)!}
              target="_blank"
              rel="noopener noreferrer external"
            >
              {t('journey.correction')}
            </a>
            <span className="od-detail__note"> {t('journey.correctionHelp')}</span>
          </p>
        ) : null}
        {releaseId ? <p className="od-detail__note">{t('saved.fromRelease', { releaseId })}</p> : null}
      </Section>
    </article>
  );
}
