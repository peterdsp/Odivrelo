import { Link } from 'react-router-dom';
import type { JourneySummary } from '../data/contract';
import { useI18n } from '../i18n/I18nProvider';
import { Badge, Card } from '../components/primitives';
import { ConfidenceBadge, OvernightBadge, PositionQualityBadge, TimeQualityBadge } from '../components/quality';
import { isPastServiceDate } from '../lib/time';

/**
 * One journey, as it appears in a result list.
 *
 * Everything the brief requires is on the face of the card and none of it is
 * implied: operator, both times with their own quality, duration, how many
 * intermediate stops, an explicit marker when it crosses midnight, the fare
 * marked indicative when there is one and marked absent when there is not,
 * freshness with both the check time and its age, and the confidence.
 *
 * The whole card is not a link. The heading is, so a screen reader can list the
 * journeys by name, and the reader can still select the times or the operator
 * name as text.
 */

export interface JourneyCardProps {
  readonly journey: JourneySummary;
  readonly to: string;
  readonly selected?: boolean;
  readonly now?: Date;
  readonly headingLevel?: 2 | 3;
}

export function JourneyCard({ journey, to, selected = false, now, headingLevel = 3 }: JourneyCardProps) {
  const { t, name, formatClock, formatDuration, formatMoney, formatAge } = useI18n();
  const Heading = headingLevel === 2 ? 'h2' : 'h3';
  const expired = isPastServiceDate(journey.serviceDate, now);

  const summary = t('results.summaryFor', {
    origin: name(journey.departure.stopName),
    departure: formatClock(journey.departure.at),
    destination: name(journey.arrival.stopName),
    arrival: formatClock(journey.arrival.at),
    operator: name(journey.operator.name),
  });

  return (
    <Card
      as="article"
      className={[
        'od-journey',
        // The provenance rail. Freshness on the leading edge of the whole
        // asserted block, paired with the stamp below so colour is never the
        // only carrier.
        `od-journey--rail-${journey.freshness.state}`,
        selected ? 'od-journey--selected' : '',
      ]
        .filter(Boolean)
        .join(' ')}
      tone="plain"
    >
      <Heading className="od-journey__heading">
        {/*
          The accessible name is the full sentence; the visible text is the two
          times. An aria-label rather than a visually-hidden span inside the link,
          because an absolutely positioned child collapses the anchor's hit area
          and makes the link awkward to tap.
        */}
        <Link to={to} className="od-journey__link" aria-label={summary} aria-current={selected ? 'true' : undefined}>
          <span className="od-data">{formatClock(journey.departure.at)}</span> {'→'}{' '}
          <span className="od-data">{formatClock(journey.arrival.at)}</span>
        </Link>
      </Heading>

      <p className="od-journey__places">
        <span>{name(journey.departure.stopName)}</span>
        <span className="od-journey__arrow" aria-hidden="true">
          {'→'}
        </span>
        <span>{name(journey.arrival.stopName)}</span>
      </p>

      <dl className="od-journey__grid">
        <div>
          <dt>{t('results.departs')}</dt>
          <dd>
            <time className="od-data" dateTime={journey.departure.at}>{formatClock(journey.departure.at)}</time>{' '}
            <TimeQualityBadge quality={journey.departure.quality} />
          </dd>
        </div>
        <div>
          <dt>{t('results.arrives')}</dt>
          <dd>
            <time className="od-data" dateTime={journey.arrival.at}>{formatClock(journey.arrival.at)}</time>{' '}
            <TimeQualityBadge quality={journey.arrival.quality} />
          </dd>
        </div>
        <div>
          <dt>{t('results.duration')}</dt>
          <dd className="od-data">{formatDuration(journey.durationMinutes)}</dd>
        </div>
        <div>
          <dt>{t('journey.operator')}</dt>
          <dd>{name(journey.operator.name)}</dd>
        </div>
        <div>
          <dt>{t('journey.stops')}</dt>
          <dd>
            {t(
              journey.intermediateStopCount === 0
                ? 'results.stops_zero'
                : journey.intermediateStopCount === 1
                  ? 'results.stops_one'
                  : 'results.stops_other',
              { count: journey.intermediateStopCount },
            )}
          </dd>
        </div>
        <div>
          <dt>{t('results.fare')}</dt>
          <dd>
            {journey.fare ? (
              <>
                <span className="od-data">{formatMoney(journey.fare.amount, journey.fare.currency)}</span>
                {journey.fare.isIndicative ? (
                  <>
                    {' '}
                    <Badge tone="warning" icon="question" title={t('results.fareIndicative')}>
                      {t('results.fareIndicativeShort')}
                    </Badge>
                  </>
                ) : null}
              </>
            ) : (
              <Badge tone="neutral" icon="question" title={t('results.fareUnknownHelp')}>
                {t('results.fareUnknown')}
              </Badge>
            )}
          </dd>
        </div>
      </dl>

      {/*
        What the rail means, in words and figures. This is the accessible
        carrier of the freshness fact; the rail itself is purely visual.
      */}
      <p className="od-journey__stamp">
        <span>{t(`freshness.${journey.freshness.state}`)}</span>
        <span className="od-data">{t('freshness.checkedAt', { when: formatAge(journey.freshness.checkedAt, now) })}</span>
      </p>

      <p className="od-journey__badges">
        {journey.crossesMidnight ? <OvernightBadge /> : null}
        <ConfidenceBadge confidence={journey.confidence} />
        <PositionQualityBadge quality={journey.positionQuality} />
        {expired ? (
          <Badge tone="warning" icon="alert">
            {t('results.expiredBadge')}
          </Badge>
        ) : null}
      </p>

      <p className="od-journey__note">{t('results.seatAvailabilityUnknown')}</p>
      {journey.purchase.kind === 'online' && journey.purchase.url ? (
        <p><a className="od-link" href={journey.purchase.url} target="_blank" rel="noreferrer">{t('booking.onlineAction', { operator: name(journey.operator.name) })}</a></p>
      ) : null}

      {journey.crossesMidnight ? (
        <p className="od-journey__note">{t('results.overnightExplain', { date: journey.serviceDate })}</p>
      ) : null}
      {journey.freshness.state === 'stale' ? (
        <p className="od-journey__note od-journey__note--warning">
          {t('freshness.staleWarning', { age: t('freshness.age_other', { count: journey.freshness.ageHours }) })}
        </p>
      ) : null}
    </Card>
  );
}
