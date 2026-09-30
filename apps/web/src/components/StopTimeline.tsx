import { Link } from 'react-router-dom';
import type { BoardingRule, JourneyStop } from '../data/contract';
import { useI18n } from '../i18n/I18nProvider';
import type { MessageKey } from '../i18n/catalogues';
import { Badge, type BadgeTone } from './primitives';
import { TimeQualityBadge } from './quality';

/**
 * The full ordered stop list.
 *
 * This is the non-map equivalent, and it is the authoritative one: it carries
 * every fact the map could show plus the pickup and drop-off rules, which the map
 * cannot show at all. It is an ordered list so the sequence is conveyed
 * structurally rather than only visually, and the arrival and departure times are
 * separate `<time>` elements rather than a single run-together string.
 */

export interface StopTimelineProps {
  readonly stops: readonly JourneyStop[];
  readonly boardingStopId: string | null;
  readonly linkStops?: boolean;
  /**
   * The level of the section this list sits in. Each stop name is one level
   * below it, so the heading order never skips a level whether the list is on
   * its own page or inside the results two-pane layout.
   */
  readonly sectionLevel?: 2 | 3;
}

const PICKUP_KEYS: Readonly<Record<BoardingRule, MessageKey>> = {
  allowed: 'journey.pickupAllowed',
  not_allowed: 'journey.pickupNotAllowed',
  on_request: 'journey.pickupOnRequest',
};

const DROPOFF_KEYS: Readonly<Record<BoardingRule, MessageKey>> = {
  allowed: 'journey.dropoffAllowed',
  not_allowed: 'journey.dropoffNotAllowed',
  on_request: 'journey.dropoffOnRequest',
};

function RuleBadge({ rule, kind }: { rule: BoardingRule; kind: 'pickup' | 'dropoff' }) {
  const { t } = useI18n();
  const tone: BadgeTone = rule === 'allowed' ? 'success' : rule === 'not_allowed' ? 'neutral' : 'warning';
  return (
    <Badge tone={tone} icon={rule === 'allowed' ? 'check' : rule === 'on_request' ? 'question' : 'alert'}>
      {t(kind === 'pickup' ? PICKUP_KEYS[rule] : DROPOFF_KEYS[rule])}
    </Badge>
  );
}

export function StopTimeline({ stops, boardingStopId, linkStops = true, sectionLevel = 2 }: StopTimelineProps) {
  const { t, name, formatClock } = useI18n();
  const StopHeading = sectionLevel === 2 ? 'h3' : 'h4';
  return (
    <ol className="pv-timeline" aria-label={t('journey.stops')}>
      {stops.map((stop, index) => {
        const isBoarding = stop.stopId === boardingStopId;
        const isLast = index === stops.length - 1;
        return (
          <li
            key={`${stop.stopId}-${stop.sequence}`}
            className={[
              'pv-timeline__item',
              isBoarding ? 'pv-timeline__item--boarding' : '',
              index === 0 ? 'pv-timeline__item--first' : '',
              isLast ? 'pv-timeline__item--last' : '',
            ]
              .filter(Boolean)
              .join(' ')}
          >
            <div className="pv-timeline__rail" aria-hidden="true">
              <span className="pv-timeline__dot" />
            </div>
            <div className="pv-timeline__body">
              <StopHeading className="pv-timeline__name">
                {linkStops ? (
                  <Link to={`/stations/${encodeURIComponent(stop.stopId)}`} className="pv-link">
                    {name(stop.name)}
                  </Link>
                ) : (
                  name(stop.name)
                )}
              </StopHeading>
              <p className="pv-timeline__times">
                {stop.arrivalAt ? (
                  <span className="pv-timeline__time">
                    {t('journey.arrivalAt', { time: '' }).trim()}{' '}
                    <time dateTime={stop.arrivalAt}>{formatClock(stop.arrivalAt)}</time>
                  </span>
                ) : null}
                {stop.departureAt ? (
                  <span className="pv-timeline__time">
                    {t('journey.departureAt', { time: '' }).trim()}{' '}
                    <time dateTime={stop.departureAt}>{formatClock(stop.departureAt)}</time>
                  </span>
                ) : null}
                {!stop.arrivalAt && !stop.departureAt ? <span className="pv-timeline__time">{t('journey.noTime')}</span> : null}
              </p>
              <p className="pv-timeline__badges">
                <TimeQualityBadge quality={stop.timeQuality} />
                <RuleBadge rule={stop.pickup} kind="pickup" />
                <RuleBadge rule={stop.dropoff} kind="dropoff" />
              </p>
            </div>
          </li>
        );
      })}
    </ol>
  );
}
