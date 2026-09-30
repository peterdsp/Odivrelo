import type {
  Confidence,
  CoverageState,
  Freshness,
  GeometryConfidence,
  PositionQuality,
  ReviewState,
  RightsStatus,
  TimeQuality,
} from '../data/contract';
import { useI18n } from '../i18n/I18nProvider';
import { Badge, type BadgeTone } from './primitives';

/**
 * The confidence vocabulary, rendered consistently everywhere.
 *
 * Scheduled, approximate, predicted, estimated and live are kept visibly
 * distinct. This release has scheduled data only, so `live` never appears from
 * data; the component still handles it so that when a real-time feed does exist
 * nothing has to be rewritten, and until then the app says plainly that live
 * tracking is not available rather than animating a coach that is not there.
 */

export function TimeQualityBadge({ quality }: { quality: TimeQuality }) {
  const { t } = useI18n();
  const tone: BadgeTone = quality === 'scheduled' ? 'info' : quality === 'approximate' ? 'warning' : 'neutral';
  const icon = quality === 'unknown' ? 'question' : 'clock';
  return (
    <Badge tone={tone} icon={icon} title={t(`quality.${quality}Help`)}>
      {t(`quality.${quality}`)}
    </Badge>
  );
}

export function PositionQualityBadge({ quality }: { quality: PositionQuality }) {
  const { t } = useI18n();
  // Only `scheduled` can occur in release 1.0.0. The others are rendered
  // honestly if a future release ever carries them.
  if (quality === 'scheduled') {
    return (
      <Badge tone="neutral" icon="clock" title={t('position.scheduledHelp')}>
        {t('position.scheduled')}
      </Badge>
    );
  }
  return (
    <Badge tone="info" icon="route">
      {quality}
    </Badge>
  );
}

export function ConfidenceBadge({ confidence }: { confidence: Confidence }) {
  const { t } = useI18n();
  return confidence === 'reviewed' ? (
    <Badge tone="success" icon="shield" title={t('confidence.reviewedHelp')}>
      {t('confidence.reviewed')}
    </Badge>
  ) : (
    <Badge tone="warning" icon="question" title={t('confidence.candidateHelp')}>
      {t('confidence.candidate')}
    </Badge>
  );
}

export function FreshnessBadge({ freshness, now }: { freshness: Freshness; now?: Date }) {
  const { t, formatAge, formatDateTime } = useI18n();
  const tone: BadgeTone = freshness.state === 'fresh' ? 'success' : freshness.state === 'aging' ? 'warning' : 'error';
  const icon = freshness.state === 'stale' ? 'alert' : 'clock';
  return (
    <Badge tone={tone} icon={icon} title={formatDateTime(freshness.checkedAt)}>
      {t(`freshness.${freshness.state}`)}
      {': '}
      {t('freshness.checkedAt', { when: formatAge(freshness.checkedAt, now) })}
    </Badge>
  );
}

export function GeometryBadge({ confidence }: { confidence: GeometryConfidence }) {
  const { t } = useI18n();
  const tone: BadgeTone =
    confidence === 'reviewed' ? 'success' : confidence === 'rejected' ? 'error' : confidence === 'unverified' ? 'warning' : 'info';
  // Only three of the five confidences have their own explanation; the rest
  // carry the label alone rather than a made-up sentence.
  const help =
    confidence === 'unverified'
      ? t('geometry.unverifiedHelp')
      : confidence === 'ordered_stops_only'
        ? t('geometry.ordered_stops_onlyHelp')
        : confidence === 'reviewed'
          ? t('geometry.reviewedHelp')
          : undefined;
  return (
    <Badge tone={tone} icon="route" {...(help ? { title: help } : {})}>
      {t(`geometry.${confidence}`)}
    </Badge>
  );
}

export function ReviewStateBadge({ state }: { state: ReviewState }) {
  const { t } = useI18n();
  const tone: BadgeTone =
    state === 'published' || state === 'verified'
      ? 'success'
      : state === 'candidate'
        ? 'warning'
        : state === 'stale'
          ? 'warning'
          : 'error';
  return (
    <Badge tone={tone} icon={state === 'published' || state === 'verified' ? 'check' : 'question'}>
      {t(`review.${state}`)}
    </Badge>
  );
}

export function RightsBadge({ status }: { status: RightsStatus }) {
  const { t } = useI18n();
  const map: Record<RightsStatus, { tone: BadgeTone; label: string }> = {
    allowed: { tone: 'success', label: t('coverage.rightsAllowed') },
    permission_pending: { tone: 'warning', label: t('coverage.rightsPending') },
    prohibited: { tone: 'error', label: t('coverage.rightsProhibited') },
    unknown: { tone: 'neutral', label: t('coverage.rightsUnknown') },
  };
  const { tone, label } = map[status];
  return (
    <Badge tone={tone} icon={status === 'allowed' ? 'check' : 'question'}>
      {label}
    </Badge>
  );
}

export function CoverageBadge({ state }: { state: CoverageState }) {
  const { t } = useI18n();
  const map: Record<CoverageState, { tone: BadgeTone; label: string }> = {
    covered: { tone: 'success', label: t('coverage.stateCovered') },
    partial: { tone: 'warning', label: t('coverage.statePartial') },
    not_covered: { tone: 'error', label: t('coverage.stateNotCovered') },
    demo: { tone: 'accent', label: t('coverage.stateDemo') },
  };
  const { tone, label } = map[state];
  return (
    <Badge tone={tone} icon={state === 'covered' ? 'check' : 'alert'}>
      {label}
    </Badge>
  );
}

export function StepFreeBadge({ stepFree }: { stepFree: boolean | null }) {
  const { t } = useI18n();
  if (stepFree === true) {
    return (
      <Badge tone="success" icon="check">
        {t('journey.stepFreeYes')}
      </Badge>
    );
  }
  if (stepFree === false) {
    return (
      <Badge tone="warning" icon="alert">
        {t('journey.stepFreeNo')}
      </Badge>
    );
  }
  return (
    <Badge tone="neutral" icon="question" title={t('journey.stepFreeUnknownHelp')}>
      {t('journey.stepFreeUnknown')}
    </Badge>
  );
}

export function OvernightBadge() {
  const { t } = useI18n();
  return (
    <Badge tone="info" icon="moon">
      {t('results.overnight')}
    </Badge>
  );
}

/** Where live tracking would be, if there were any. */
export function LiveTrackingNotice() {
  const { t } = useI18n();
  return (
    <div className="od-notice od-notice--info" data-testid="live-unavailable">
      <p className="od-notice__title">{t('position.liveUnavailable')}</p>
      <p>{t('position.liveUnavailableHelp')}</p>
    </div>
  );
}
