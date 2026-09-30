import { useEffect, useRef, type ReactNode } from 'react';
import { useI18n } from '../i18n/I18nProvider';
import type { AdaptiveLayout } from '../hooks/useAdaptiveLayout';
import { Button } from './primitives';

/**
 * A genuine two-pane layout, not a stretched phone screen.
 *
 * When the measured container is wide enough (and tall enough, and no virtual
 * keyboard is covering it) the list and the detail are shown side by side, each
 * at a sensible reading measure, each its own scroll container and its own
 * labelled region.
 *
 * When only one pane fits, both are still reachable: the detail takes over and a
 * back control returns to the list. Nothing is ever unreachable because the
 * window is narrow, and switching between the two layouts never loses which item
 * was selected.
 *
 * On a device that reports two viewport segments, the split is placed at the
 * hinge instead of the middle. That is a refinement on top of a complete
 * single-viewport layout, never a requirement.
 */

export interface TwoPaneProps {
  readonly layout: AdaptiveLayout;
  readonly listLabel: string;
  readonly detailLabel: string;
  readonly list: ReactNode;
  /** Null when nothing is selected: the list then fills the width. */
  readonly detail: ReactNode | null;
  readonly onCloseDetail: () => void;
  readonly closeLabel?: string;
}

export function TwoPane({
  layout,
  listLabel,
  detailLabel,
  list,
  detail,
  onCloseDetail,
  closeLabel,
}: TwoPaneProps) {
  const { t } = useI18n();
  const detailRef = useRef<HTMLDivElement | null>(null);
  const showDetailOnly = !layout.twoPane && detail !== null;

  // In the single-pane case the detail has genuinely replaced the list, so focus
  // has to follow it or a keyboard reader is left pointing at content that is no
  // longer on screen.
  useEffect(() => {
    if (showDetailOnly) detailRef.current?.focus();
  }, [showDetailOnly]);

  // With two segments, split at the hinge. `segments` is only ever non-null when
  // the platform reports it, so this cannot affect an ordinary screen.
  const hingeSplit =
    layout.twoPane && layout.segments && layout.segments.length === 2 && layout.segments[0]
      ? `${Math.round((layout.segments[0].width / layout.width) * 100)}%`
      : null;

  const style = hingeSplit ? ({ '--pv-pane-split': hingeSplit } as React.CSSProperties) : undefined;

  return (
    <div
      className="pv-two-pane"
      data-mode={layout.twoPane ? 'two' : 'one'}
      data-showing={detail ? 'detail' : 'list'}
      {...(style ? { style } : {})}
    >
      <div className="pv-two-pane__list" role="region" aria-label={listLabel} hidden={showDetailOnly}>
        {list}
      </div>
      {detail !== null ? (
        <div
          className="pv-two-pane__detail"
          role="region"
          aria-label={detailLabel}
          tabIndex={-1}
          ref={detailRef}
        >
          <div className="pv-two-pane__detail-bar">
            <Button tone="quiet" onClick={onCloseDetail}>
              <span aria-hidden="true">{'←'} </span>
              {closeLabel ?? t('app.back')}
            </Button>
          </div>
          {detail}
        </div>
      ) : null}
    </div>
  );
}
