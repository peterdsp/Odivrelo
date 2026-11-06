import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import type { Place, StopResult } from '../data/contract';
import { useI18n } from '../i18n/I18nProvider';
import { useHead, organizationNode } from '../hooks/useHead';
import { useData, useDataMode } from '../app/DataProvider';
import { useAsync } from '../hooks/useAsync';
import { Button, Card, Checkbox, Disclosure, Section, SelectField, TextField } from '../components/primitives';
import { PlaceField } from '../components/PlaceField';
import { ServiceDateField } from '../components/ServiceDateField';
import { DataError, Loading, StateBlock } from '../components/states';
import { CoverageBadge } from '../components/quality';
import { useAnnouncer } from '../components/Announcer';
import { todayServiceDate } from '../lib/time';
import {
  activeFilterCount,
  clearRecentSearches,
  readRecentSearches,
  readSearchState,
  readStoredFilter,
  rememberSearch,
  writeSearchState,
  writeStoredFilter,
  type RecentSearch,
  type SearchState,
} from '../features/searchParams';
import { BRAND } from '../brand/brand';
import { GreekTravelImage } from '../components/GreekTravelImage';
import { ROAD_PICTURE } from '../data/pictures';
import { serviceDatesOf } from '../features/offlineGroups';
import { JourneyCard } from '../features/JourneyCard';
import type { JourneyQuery } from '../data/PublicDataSource';

/**
 * The search screen.
 *
 * Origin and destination are disambiguated places, not free text, so a search can
 * never be run against a name the release does not hold. The service date is
 * bounded by what the release actually carries, and every filter has a stated
 * consequence. Nothing here requires a connection beyond the release packs, and
 * if those cannot be read the screen says so rather than presenting an empty form
 * that cannot work.
 */
export function Search() {
  const { t, language, name } = useI18n();
  const { source, meta, manifest, reload } = useData();
  const dataMode = useDataMode();
  const { announce, alert } = useAnnouncer();
  const [params, setParams] = useSearchParams();

  const incoming = useMemo(() => readSearchState(params), [params]);

  const [origin, setOrigin] = useState<Place | null>(null);
  const [destination, setDestination] = useState<Place | null>(null);
  const [date, setDate] = useState<string>(() => incoming.date ?? todayServiceDate());
  const [accessible, setAccessible] = useState(incoming.accessible);
  const [operatorId, setOperatorId] = useState<string>(incoming.operatorIds[0] ?? '');
  const [departFrom, setDepartFrom] = useState(incoming.departFrom ?? '');
  const [departTo, setDepartTo] = useState(incoming.departTo ?? '');
  const [errors, setErrors] = useState<{ origin?: string; destination?: string; date?: string }>({});
  const [recent, setRecent] = useState<RecentSearch[]>(() => readRecentSearches());

  /*
   * Athens terminals, read from the release rather than written down here.
   *
   * Most journeys in this release start in Athens, and a first-time reader
   * arrived at an empty form with an empty recent list and nothing to act on.
   * These are real places from the directory: if a release stops carrying
   * them, the row disappears rather than offering a terminal that is not in
   * the data.
   */
  const athensPlaces = useAsync((signal) => source.places('ΑΘΗΝΑ', 40, signal), [source]);

  /*
   * The release carries dozens of Athens stops and many share the bare name
   * "ΑΘΗΝΑ", so the first four from the directory were four identical
   * buttons. Only the ones that name their terminal can be acted on, and the
   * list is deduplicated by the name actually rendered.
   */
  const athensStarts = useMemo(() => {
    if (athensPlaces.state.status !== 'ready') return [];
    const seen = new Set<string>();
    const named: Place[] = [];
    for (const place of athensPlaces.state.value.places) {
      const label = name(place.name).trim();
      if (!label || !label.includes('(') || seen.has(label)) continue;
      seen.add(label);
      named.push(place);
      if (named.length === 4) break;
    }
    return named;
  }, [athensPlaces.state, name]);
  const [inlineQuery, setInlineQuery] = useState<JourneyQuery | null>(null);

  /*
   * A search lives in the address bar, not only in this component.
   *
   * Answering in place used to leave the URL on a bare /search, so opening a
   * journey and pressing back came home to an empty form: the results, and
   * everything typed to find them, were gone. The query is written to the URL
   * on submit and read back here, which also makes a search survive a refresh
   * and be worth sending to someone.
   */
  const carried = incoming.originId && incoming.destinationId ? `${incoming.originId}~${incoming.destinationId}` : '';
  /*
   * Resolved one id at a time rather than by scanning the directory: the
   * release carries 19,139 places, terminals sort first, and the ids in a link
   * are usually boarding points, so any capped list of them comes back without
   * the two that matter.
   */
  const carriedPlaces = useAsync(
    async (signal) => {
      const [from, to] = carried.split('~');
      return Promise.all([source.place(from!, signal), source.place(to!, signal)]);
    },
    [source, carried],
    { enabled: carried !== '' },
  );
  useEffect(() => {
    if (!carried || carriedPlaces.state.status !== 'ready') return;
    const [from, to] = carriedPlaces.state.value;
    // A stop record carries everything a place does except the three fields
    // below, each of which it states in another shape.
    const asPlace = (stop: StopResult): Place => ({
      ...stop,
      parentId: stop.terminal?.id ?? null,
      boardingPointCount: stop.boardingPoints.length,
      operatorIds: stop.operators.map((operator) => operator.id),
    });
    setOrigin(asPlace(from!));
    setDestination(asPlace(to!));
    setInlineQuery({
      originId: from!.id,
      destinationId: to!.id,
      date: incoming.date ?? todayServiceDate(),
      operatorIds: incoming.operatorIds,
      accessible: incoming.accessible,
      departFrom: incoming.departFrom ?? undefined,
      departTo: incoming.departTo ?? undefined,
    });
  }, [carried, carriedPlaces.state, incoming]);

  const metaValue = meta.status === 'ready' ? meta.value : null;
  const manifestValue = manifest.status === 'ready' ? manifest.value : null;

  useHead({
    title: t('meta.search.title'),
    description: t('meta.home.description'),
    path: '/search',
    language,
    dataMode,
    structuredData: {
      '@context': 'https://schema.org',
      '@type': 'WebSite',
      name: BRAND.name,
      url: BRAND.url,
      inLanguage: language,
      publisher: organizationNode(),
    },
  });

  // Restore the last filter the reader used, but only when the URL does not
  // already carry one: an explicit link always wins over a remembered default.
  useEffect(() => {
    if (params.toString().length > 0) return;
    const stored = readStoredFilter();
    if (!stored) return;
    setAccessible(stored.accessible);
    setOperatorId(stored.operatorIds[0] ?? '');
    setDepartFrom(stored.departFrom ?? '');
    setDepartTo(stored.departTo ?? '');
  }, [params]);

  const operators = useAsync((signal) => source.operators(signal), [source]);

  // Resolve place ids arriving in the URL back into real places, so a shared or
  // restored link repopulates the form completely.
  const resolvePlaces = useCallback(
    async (signal: AbortSignal) => {
      const ids = [incoming.originId, incoming.destinationId].filter((v): v is string => v !== null);
      if (ids.length === 0) return { origin: null, destination: null };
      const all = await source.places('', 500, signal);
      const find = (id: string | null) => (id ? (all.places.find((p) => p.id === id) ?? null) : null);
      return { origin: find(incoming.originId), destination: find(incoming.destinationId) };
    },
    [source, incoming.originId, incoming.destinationId],
  );
  const resolved = useAsync(resolvePlaces, [resolvePlaces]);

  useEffect(() => {
    if (resolved.state.status !== 'ready') return;
    if (resolved.state.value.origin) setOrigin(resolved.state.value.origin);
    if (resolved.state.value.destination) setDestination(resolved.state.value.destination);
  }, [resolved.state]);

  const searchPlaces = useCallback(
    async (query: string) => {
      const result = await source.places(query, 12);
      return result.places;
    },
    [source],
  );

  const popularPlaces = useCallback(async () => {
    // These are real names observed in the current KTEL release, not demo
    // suggestions. Exact station queries keep the useful terminal records at
    // the top instead of returning an arbitrary page of city stops.
    const queries = [
      'ΑΘΗΝΑ_ΣΤΑΘΜΟΣ',
      'ΣΤΑΘΜΟΣ ΑΓΡΙΝΙΟΥ',
      'ΠΑΤΡΑ ΣΤΑΘΜΟΣ',
      'TRIPOLI',
      'ASTROS',
      'PARALIO ASTROS',
      'MEGALOPOLI',
    ];
    const results = await Promise.all(queries.map((query) => source.places(query, 8)));
    const seen = new Set<string>();
    const candidates = results
      // A curated suggestion must be useful for an actual search. The previous
      // code surfaced the first two Athens records even when both were marked
      // not_covered, which made the picker look duplicated and led directly to
      // a dead timetable search.
      .flatMap((result) => result.places)
      .sort((a, b) => Number(b.coverage === 'covered') - Number(a.coverage === 'covered'));
    return candidates
      .filter((place) => {
        // Different operator imports can carry the same terminal under
        // different opaque ids. Keep one useful visible suggestion per name;
        // typed searches still expose the complete disambiguation set.
        const key = `${name(place.name).trim().toLocaleLowerCase()}|${place.municipality}`;
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
      })
      .slice(0, 12);
  }, [name, source]);

  /*
   * The date field is bounded by the dates this release actually holds a journeys
   * pack for. That is a statement about the download, not about the timetable: a
   * service may well run on a date outside this range, and a live API would
   * resolve it. Bounding the field keeps the reader from asking a question this
   * device cannot answer, and the results page says so explicitly if they do.
   */
  const releaseDates = useMemo(() => (manifestValue ? serviceDatesOf(manifestValue) : []), [manifestValue]);
  const minDate = releaseDates[0] ?? todayServiceDate();
  const maxDate = releaseDates.at(-1) ?? todayServiceDate();

  const submit = () => {
    const next: typeof errors = {};
    if (!origin) next.origin = t('search.missingOrigin');
    if (!destination) next.destination = t('search.missingDestination');
    if (origin && destination && origin.id === destination.id) {
      next.destination = t('search.sameOriginDestination');
    }
    if (date < minDate || date > maxDate) {
      next.date = t('search.dateOutOfRange', { from: minDate, to: maxDate });
    }
    setErrors(next);
    const firstError = next.origin ?? next.destination ?? next.date;
    if (firstError) {
      alert(firstError);
      return;
    }
    if (!origin || !destination) return;

    const state: SearchState = {
      originId: origin.id,
      destinationId: destination.id,
      date,
      operatorIds: operatorId ? [operatorId] : [],
      accessible,
      departFrom: departFrom || null,
      departTo: departTo || null,
      view: 'list',
      journeyId: null,
    };
    writeStoredFilter({
      accessible,
      operatorIds: operatorId ? [operatorId] : [],
      departFrom: departFrom || null,
      departTo: departTo || null,
    });
    // Remembered here rather than on the results page, so the memory records
    // what the reader asked for even when the answer is "nothing runs".
    setRecent(
      rememberSearch({
        originId: origin.id,
        destinationId: destination.id,
        originLabel: name(origin.name),
        destinationLabel: name(destination.name),
        date,
        at: new Date().toISOString(),
      }),
    );
    setInlineQuery({
      originId: origin.id,
      destinationId: destination.id,
      date,
      operatorIds: state.operatorIds,
      accessible: state.accessible,
      departFrom: state.departFrom ?? undefined,
      departTo: state.departTo ?? undefined,
    });
    // Pushed, not replaced, so back from a journey returns to this answer.
    setParams(writeSearchState(state));
  };

  const filterCount = activeFilterCount({
    originId: origin?.id ?? null,
    destinationId: destination?.id ?? null,
    date,
    operatorIds: operatorId ? [operatorId] : [],
    accessible,
    departFrom: departFrom || null,
    departTo: departTo || null,
    view: 'list',
    journeyId: null,
  });

  const inlineResults = useAsync(
    (signal) => (inlineQuery ? source.journeys(inlineQuery, signal) : Promise.reject(new Error('no search yet'))),
    [source, inlineQuery],
    { enabled: inlineQuery !== null },
  );

  /*
   * A page still needs its level-one heading while it loads.
   *
   * These branches returned a bare spinner, so for as long as the data took
   * there was no h1 anywhere on the page: a screen reader landing mid-load
   * had nothing to orient by, and axe flagged it on whichever page the timing
   * happened to catch.
   */
  if (meta.status === 'loading' || manifest.status === 'loading') {
    return (
      <div className="od-page od-page--narrow">
        <h1 className="od-page__title">{t('meta.search.title')}</h1>
        <Loading />
      </div>
    );
  }
  if (meta.status === 'error') return <DataError error={meta.error} onRetry={reload} />;
  if (manifest.status === 'error') return <DataError error={manifest.error} onRetry={reload} />;

  return (
    <div className="od-page od-search-page">
      {/*
        The landing composition: the claim, the picture of the world the product
        describes, and the form, read in that order. The picture is editorial
        context from Wikimedia with its credit attached; it is never evidence of
        a service, which is why it carries no operator, time or route.
      */}
      <div className="od-hero">
        <div className="od-hero__frame">
          <GreekTravelImage
            className="od-hero__picture"
            src={ROAD_PICTURE.src}
            alt={t('search.heroPictureAlt')}
            title={ROAD_PICTURE.title}
            creator={ROAD_PICTURE.creator}
            licence={ROAD_PICTURE.licence}
            licenceHref={ROAD_PICTURE.licenceHref}
            source={ROAD_PICTURE.source}
            href={ROAD_PICTURE.href}
          />
        </div>

        <div className="od-hero__column">
          <div className="od-hero__words">
            <h1 className="od-hero__title">{t('search.heroTitle')}</h1>
            <p className="od-hero__lede">{t('search.heroLede')}</p>
            {metaValue ? (
              <p className="od-hero__coverage">
                <CoverageBadge state={metaValue.coverage.state} />{' '}
                <span>{name(metaValue.coverage.note)}</span>
              </p>
            ) : null}
          </div>

      <Card as="form" className="od-search-form od-search-form--floating">
        <div className="od-search-form__places">
          <PlaceField
            name="origin"
            label={t('search.origin')}
            placeholder={t('search.originPlaceholder')}
            value={origin}
            onChange={(place) => {
              setOrigin(place);
              setErrors((e) => ({ ...e, origin: undefined }));
            }}
            search={searchPlaces}
            popular={popularPlaces}
            error={errors.origin ?? null}
            required
          />
          <div className="od-search-form__swap">
            <Button
              tone="quiet"
              aria-label={t('search.swap')}
              onClick={() => {
                setOrigin(destination);
                setDestination(origin);
                setErrors({});
                announce(t('search.swap'));
              }}
            >
              <svg viewBox="0 0 16 16" width="20" height="20" aria-hidden="true" focusable="false">
                <path
                  d="M4 3v10M4 3 2 5.5M4 3l2 2.5M12 13V3M12 13l2-2.5M12 13l-2-2.5"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.5"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            </Button>
          </div>
          <PlaceField
            name="destination"
            label={t('search.destination')}
            placeholder={t('search.destinationPlaceholder')}
            value={destination}
            onChange={(place) => {
              setDestination(place);
              setErrors((e) => ({ ...e, destination: undefined }));
            }}
            search={searchPlaces}
            popular={popularPlaces}
            error={errors.destination ?? null}
            required
          />
        </div>

        {origin?.coverage === 'not_covered' ? (
          <StateBlock
            kind="restricted"
            headingLevel={2}
            title={t('coverage.stateNotCovered')}
            body={<p>{t('search.notCoveredPlace', { name: name(origin.name) })}</p>}
            announce={false}
          />
        ) : null}
        {destination?.coverage === 'not_covered' ? (
          <StateBlock
            kind="restricted"
            headingLevel={2}
            title={t('coverage.stateNotCovered')}
            body={<p>{t('search.notCoveredPlace', { name: name(destination.name) })}</p>}
            announce={false}
          />
        ) : null}

        <ServiceDateField
          value={date}
          onChange={(next) => {
            setDate(next);
            setErrors((e) => ({ ...e, date: undefined }));
          }}
          min={minDate}
          max={maxDate}
          error={errors.date ?? null}
        />

        <Disclosure summary={`${t('search.filters')} · ${t(filterCount === 0 ? 'search.filtersApplied_zero' : filterCount === 1 ? 'search.filtersApplied_one' : 'search.filtersApplied_other', { count: filterCount })}`}>
          <div className="od-filters">
            {operators.state.status === 'ready' ? (
              <SelectField
                id="filter-operator"
                label={t('search.operatorFilter')}
                value={operatorId}
                onChange={(event) => setOperatorId(event.target.value)}
              >
                <option value="">{t('search.allOperators')}</option>
                {operators.state.value.operators.map((operator) => (
                  <option key={operator.id} value={operator.id}>
                    {name(operator.name)}
                  </option>
                ))}
              </SelectField>
            ) : null}

            <Checkbox
              id="filter-accessible"
              label={t('search.accessibleFilter')}
              hint={t('search.accessibleHelp')}
              checked={accessible}
              onChange={(event) => setAccessible(event.target.checked)}
            />

            <div className="od-filters__window">
              <TextField
                id="filter-from"
                label={t('search.departFrom')}
                type="time"
                value={departFrom}
                onChange={(event) => setDepartFrom(event.target.value)}
              />
              <TextField
                id="filter-to"
                label={t('search.departTo')}
                type="time"
                value={departTo}
                onChange={(event) => setDepartTo(event.target.value)}
              />
            </div>

            {filterCount > 0 ? (
              <Button
                tone="quiet"
                onClick={() => {
                  setAccessible(false);
                  setOperatorId('');
                  setDepartFrom('');
                  setDepartTo('');
                  announce(t('search.clearFilters'));
                }}
              >
                {t('search.clearFilters')}
              </Button>
            ) : null}
          </div>
        </Disclosure>

        <Button tone="accent" full type="submit" onClick={(event) => { event.preventDefault(); submit(); }}>
          {t('search.submit')}
        </Button>
      </Card>
        </div>
      </div>

      {/*
        Four capabilities, each one a thing the product actually does. None of
        them claims live data: the release is dated and the freshness of every
        fact is shown beside it.
      */}
      <ul className="od-capabilities">
        {([
          ['search.capability.coaches', 'search.capability.coachesNote'],
          ['search.capability.freshness', 'search.capability.freshnessNote'],
          ['search.capability.wallet', 'search.capability.walletNote'],
          ['search.capability.offline', 'search.capability.offlineNote'],
        ] as const).map(([titleKey, noteKey]) => (
          <li key={titleKey} className="od-capabilities__item">
            <span className="od-capabilities__title">{t(titleKey)}</span>
            <span className="od-capabilities__note">{t(noteKey)}</span>
          </li>
        ))}
      </ul>

      {inlineQuery ? (
        <Section
          title={t('results.title', { origin: origin ? name(origin.name) : '', destination: destination ? name(destination.name) : '' })}
          description={inlineQuery.date}
        >
          {inlineResults.state.status === 'loading' ? <Loading /> : null}
          {inlineResults.state.status === 'error' ? <DataError error={inlineResults.state.error} onRetry={inlineResults.reload} /> : null}
          {inlineResults.state.status === 'ready' && inlineResults.state.value.results.length === 0 ? (
            <StateBlock
              kind={inlineResults.state.value.unavailableReason === 'outside_coverage' ? 'restricted' : inlineResults.state.value.unavailableReason === 'no_offline_data_for_date' ? 'unavailable' : 'empty'}
              headingLevel={3}
              title={
                inlineResults.state.value.unavailableReason === 'outside_coverage' ? t('results.notCoveredTitle')
                  : inlineResults.state.value.unavailableReason === 'origin_equals_destination' ? t('results.sameTitle')
                    : inlineResults.state.value.unavailableReason === 'no_offline_data_for_date' ? t('results.noOfflineDataTitle')
                      : t('results.emptyTitle')
              }
              body={<p>
                {inlineResults.state.value.unavailableReason === 'outside_coverage' ? t('results.notCoveredBody', { origin: origin ? name(origin.name) : '', destination: destination ? name(destination.name) : '' })
                  : inlineResults.state.value.unavailableReason === 'origin_equals_destination' ? t('results.sameBody')
                    : inlineResults.state.value.unavailableReason === 'no_offline_data_for_date' ? t('results.noOfflineDataBody', { date: inlineQuery.date })
                      : t('results.emptyBody', { date: inlineQuery.date })}
              </p>}
              announce={false}
            />
          ) : null}
          {inlineResults.state.status === 'ready' && inlineResults.state.value.results.length > 0 ? (
            <ul className="od-list od-list--cards">
              {inlineResults.state.value.results.map((journey) => (
                <li key={journey.id}>
                  <JourneyCard
                    journey={journey}
                    to={`/journey/${encodeURIComponent(journey.id)}?date=${encodeURIComponent(journey.serviceDate)}`}
                  />
                </li>
              ))}
            </ul>
          ) : null}
        </Section>
      ) : null}

      <Section
        title={t('search.recent')}
        actions={
          recent.length > 0 ? (
            <Button
              tone="quiet"
              onClick={() => {
                clearRecentSearches();
                setRecent([]);
                announce(t('search.clearRecent'));
              }}
            >
              {t('search.clearRecent')}
            </Button>
          ) : null
        }
      >
        {recent.length === 0 ? (
          <>
            <p className="od-muted">{t('search.noRecent')}</p>
            {athensStarts.length > 0 ? (
              <div className="od-start">
                <p className="od-start__label">{t('search.startFromAthens')}</p>
                <ul className="od-start__list">
                  {athensStarts.map((place) => (
                    <li key={place.id}>
                      <Button
                        tone="secondary"
                        onClick={() => {
                          setOrigin(place);
                          setErrors((e) => ({ ...e, origin: undefined }));
                          announce(t('search.originSet', { place: name(place.name) }));
                        }}
                      >
                        {name(place.name)}
                      </Button>
                    </li>
                  ))}
                </ul>
              </div>
            ) : null}
          </>
        ) : (
          <ul className="od-list od-list--plain">
            {recent.map((entry) => (
              <li key={`${entry.originId}-${entry.destinationId}-${entry.date}`}>
                <Link
                  className="od-recent"
                  to={{
                    pathname: '/results',
                    search: `?${writeSearchState({
                      originId: entry.originId,
                      destinationId: entry.destinationId,
                      date: entry.date,
                      operatorIds: [],
                      accessible: false,
                      departFrom: null,
                      departTo: null,
                      view: 'list',
                      journeyId: null,
                    }).toString()}`,
                  }}
                >
                  <span className="od-recent__route">
                    {entry.originLabel} {'→'} {entry.destinationLabel}
                  </span>
                  <span className="od-recent__date">{entry.date}</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </Section>
    </div>
  );
}
