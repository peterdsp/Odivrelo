import { useCallback, useEffect, useMemo, useRef } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import type { JourneysResult, Place } from '../data/contract';
import { useI18n } from '../i18n/I18nProvider';
import { useHead } from '../hooks/useHead';
import { useData, useDataMode } from '../app/DataProvider';
import { useLayout } from '../app/layoutContext';
import { useAsync } from '../hooks/useAsync';
import { Button, ButtonLink, Section } from '../components/primitives';
import { CoverageBadge } from '../components/quality';
import { DataError, Loading, StateBlock } from '../components/states';
import { TwoPane } from '../components/TwoPane';
import { useAnnouncer } from '../components/Announcer';
import { JourneyCard } from '../features/JourneyCard';
import { JourneyDetailView } from '../features/JourneyDetailView';
import { readSearchState, toQuery, writeSearchState } from '../features/searchParams';
import { isPastServiceDate } from '../lib/time';
import { serviceDatesOf } from '../features/offlineGroups';

/**
 * Journey results.
 *
 * The query lives entirely in the URL, so this page is reconstructed identically
 * after a resize, a rotation, a reload, or a return from the back stack, and the
 * identical search is never re-issued because the window changed shape: the
 * request is memoised on its own contents.
 *
 * When there is room, the selected journey is shown in a second pane beside the
 * list. When there is not, the same journey takes the whole width with a back
 * control, and both are always reachable.
 */
export function Results() {
  const { t, language, name, plural } = useI18n();
  const { source, manifest, reload } = useData();
  const dataMode = useDataMode();
  const layout = useLayout();
  const { announce } = useAnnouncer();
  const [params, setParams] = useSearchParams();

  const state = useMemo(() => readSearchState(params), [params]);
  const query = useMemo(() => toQuery(state), [state]);

  /*
   * Hold the query stable while it is semantically unchanged, so a geometry
   * change, a re-render or a restored back-stack entry cannot re-issue an
   * identical search.
   *
   * The key is derived from the query's own contents, so two structurally equal
   * queries produce the same key and therefore the same memoised object. The
   * presentation-only parts of the URL, the selected journey and the map intent,
   * are not in it, which is exactly why selecting a journey does not refetch.
   */
  const queryKey = useMemo(
    () =>
      query
        ? [
            query.originId,
            query.destinationId,
            query.date,
            query.accessible ? '1' : '0',
            query.departFrom ?? '',
            query.departTo ?? '',
            (query.operatorIds ?? []).join('+'),
          ].join('|')
        : '',
    [query],
  );
  // eslint-disable-next-line react-hooks/exhaustive-deps -- keyed deliberately on the derived key
  const effectiveQuery = useMemo(() => query, [queryKey]);

  const results = useAsync(
    (signal) => (effectiveQuery ? source.journeys(effectiveQuery, signal) : Promise.reject(new Error('incomplete query'))),
    [source, effectiveQuery],
    { enabled: effectiveQuery !== null },
  );

  const places = useAsync((signal) => source.places('', 500, signal), [source]);
  const placeById = useMemo(() => {
    const map = new Map<string, Place>();
    if (places.state.status === 'ready') for (const place of places.state.value.places) map.set(place.id, place);
    return map;
  }, [places.state]);

  const originName = state.originId ? name(placeById.get(state.originId)?.name) || state.originId : '';
  const destinationName = state.destinationId ? name(placeById.get(state.destinationId)?.name) || state.destinationId : '';

  const value: JourneysResult | null = results.state.status === 'ready' ? results.state.value : null;

  useHead({
    title:
      state.date && originName && destinationName
        ? t('meta.results.title', { origin: originName, destination: destinationName, date: state.date })
        : t('meta.search.title'),
    description:
      state.date && originName && destinationName
        ? t('meta.results.description', { origin: originName, destination: destinationName, date: state.date })
        : undefined,
    path: `/results?${params.toString()}`,
    language,
    dataMode,
  });

  // Announce the outcome once per completed search, politely.
  const announcedFor = useRef<string | null>(null);
  useEffect(() => {
    if (!value) return;
    const token = `${value.query.originId}|${value.query.destinationId}|${value.query.date}|${value.results.length}`;
    if (announcedFor.current === token) return;
    announcedFor.current = token;
    announce(t('a11y.resultsUpdated', { count: plural('results.count', value.results.length) }));
  }, [value, announce, t, plural]);

  const select = useCallback(
    (journeyId: string | null) => {
      const next = writeSearchState({ ...state, journeyId });
      setParams(next, { replace: false });
    },
    [state, setParams],
  );

  const searchHref = `/search?${writeSearchState({ ...state, journeyId: null }).toString()}`;

  if (!effectiveQuery) {
    return (
      <div className="pv-page pv-page--narrow">
        <h1 className="pv-page__title">{t('meta.search.title')}</h1>
        <StateBlock
          kind="invalid"
          title={t('state.invalidLinkTitle')}
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

  const manifestValue = manifest.status === 'ready' ? manifest.value : null;
  const releaseDates = manifestValue ? serviceDatesOf(manifestValue) : [];
  const expired = state.date ? isPastServiceDate(state.date) : false;

  const list = (
    <div className="pv-results">
      <div className="pv-results__head">
        <h1 className="pv-page__title">{t('results.title', { origin: originName, destination: destinationName })}</h1>
        <p className="pv-results__meta">
          <span>{state.date ? t('search.date') : ''}</span>{' '}
          {state.date ? <time dateTime={state.date}>{state.date}</time> : null}
          {value ? (
            <>
              {' · '}
              <CoverageBadge state={value.coverage} />
            </>
          ) : null}
        </p>
        <div className="pv-results__actions">
          <ButtonLink tone="secondary" to={searchHref}>
            {t('results.newSearch')}
          </ButtonLink>
        </div>
      </div>

      {expired ? (
        <StateBlock
          kind="expired"
          headingLevel={2}
          title={t('results.expiredTitle')}
          body={<p>{t('results.expiredBody', { date: state.date ?? '' })}</p>}
          announce={false}
        />
      ) : null}

      {results.state.status === 'loading' ? <Loading /> : null}
      {results.state.status === 'error' ? <DataError error={results.state.error} onRetry={results.reload} /> : null}

      {value ? (
        value.results.length === 0 ? (
          <StateBlock
            kind={
              value.unavailableReason === 'outside_coverage'
                ? 'restricted'
                : value.unavailableReason === 'no_offline_data_for_date'
                  ? 'unavailable'
                  : 'empty'
            }
            headingLevel={2}
            title={
              value.unavailableReason === 'outside_coverage'
                ? t('results.notCoveredTitle')
                : value.unavailableReason === 'origin_equals_destination'
                  ? t('results.sameTitle')
                  : value.unavailableReason === 'no_offline_data_for_date'
                    ? t('results.noOfflineDataTitle')
                    : t('results.emptyTitle')
            }
            body={
              <>
                <p>
                  {value.unavailableReason === 'outside_coverage'
                    ? t('results.notCoveredBody', { origin: originName, destination: destinationName })
                    : value.unavailableReason === 'origin_equals_destination'
                      ? t('results.sameBody')
                      : value.unavailableReason === 'no_offline_data_for_date'
                        ? t('results.noOfflineDataBody', { date: state.date ?? '' })
                        : t('results.emptyBody', { date: state.date ?? '' })}
                </p>
                {/*
                  When the answer is "this date is not downloaded", naming the
                  dates that are turns a dead end into a next step.
                */}
                {value.unavailableReason === 'no_offline_data_for_date' && releaseDates.length > 0 ? (
                  <p>
                    {t('results.datesCovered')}:{' '}
                    {releaseDates.map((date, index) => (
                      <span key={date}>
                        {index > 0 ? ', ' : ''}
                        <Link
                          className="pv-link"
                          to={{ pathname: '/results', search: `?${writeSearchState({ ...state, date }).toString()}` }}
                        >
                          {date}
                        </Link>
                      </span>
                    ))}
                  </p>
                ) : null}
              </>
            }
            action={
              <ButtonLink tone="primary" to={searchHref}>
                {t('results.newSearch')}
              </ButtonLink>
            }
          />
        ) : (
          <>
            {value.coverage === 'partial' ? (
              <StateBlock
                kind="stale"
                headingLevel={2}
                title={t('results.partialTitle')}
                body={<p>{t('results.partialBody')}</p>}
                announce={false}
              />
            ) : null}
            <Section title={plural('results.count', value.results.length)} description={t('results.sortedByDeparture')} level={2}>
              <ul className="pv-list pv-list--cards" aria-label={t('a11y.sortedList')}>
                {value.results.map((journey) => (
                  <li key={journey.id}>
                    {layout.twoPane ? (
                      <div
                        className="pv-results__selectable"
                        onClick={(event) => {
                          // Selecting in the two-pane layout should not leave the
                          // page. The heading link still works for a new tab, a
                          // middle click, or a keyboard reader that wants the
                          // full page, so this only intercepts a plain click on
                          // the surrounding card.
                          const target = event.target as HTMLElement;
                          if (target.closest('a') || target.closest('button')) return;
                          select(journey.id);
                        }}
                      >
                        <JourneyCard
                          journey={journey}
                          to={`/journey/${encodeURIComponent(journey.id)}?date=${journey.serviceDate}`}
                          selected={state.journeyId === journey.id}
                        />
                        <Button
                          tone="quiet"
                          onClick={() => select(journey.id)}
                          aria-pressed={state.journeyId === journey.id}
                        >
                          {t('results.openDetail')}
                        </Button>
                      </div>
                    ) : (
                      <JourneyCard
                        journey={journey}
                        to={`/journey/${encodeURIComponent(journey.id)}?date=${journey.serviceDate}`}
                      />
                    )}
                  </li>
                ))}
              </ul>
            </Section>
            <p className="pv-results__footnote">
              {t('saved.fromRelease', { releaseId: value.releaseId })}
              {manifestValue ? ` · ${t('offline.release', { releaseId: manifestValue.releaseId, when: manifestValue.publishedAt })}` : ''}
            </p>
            <p className="pv-results__footnote">
              <Link to="/coverage" className="pv-link">
                {t('nav.coverage')}
              </Link>
            </p>
          </>
        )
      ) : null}
      {manifest.status === 'error' ? <DataError error={manifest.error} onRetry={reload} /> : null}
    </div>
  );

  const detail =
    state.journeyId && state.date ? (
      <JourneyDetailView journeyId={state.journeyId} serviceDate={state.date} headingLevel={2} embedded />
    ) : null;

  return (
    <div className="pv-page">
      <TwoPane
        layout={layout}
        listLabel={t('results.title', { origin: originName, destination: destinationName })}
        detailLabel={t('results.openDetail')}
        list={list}
        detail={detail}
        onCloseDetail={() => select(null)}
        closeLabel={t('results.listToggle')}
      />
    </div>
  );
}
