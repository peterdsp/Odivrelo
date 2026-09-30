import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useI18n } from '../i18n/I18nProvider';
import { useHead } from '../hooks/useHead';
import { useData, useDataMode } from '../app/DataProvider';
import { useOnline } from '../hooks/useOnline';
import { Badge, Button, Card, Disclosure, Fact, FactList, SelectField, Section } from '../components/primitives';
import { StateBlock } from '../components/states';
import { FreshnessBadge, StepFreeBadge } from '../components/quality';
import { useAnnouncer } from '../components/Announcer';
import { deleteFavorite, listFavorites, listWalletItems, type Favorite, type WalletItem } from '../lib/db';
import { hasMaterialChange, loadSavedTrips, readinessOf, removeTrip, updateTrip, type SavedTrip } from '../features/savedTrips';
import {
  canDeliverInBackground,
  notificationSupport,
  reminderInstant,
  REMINDER_CHOICES,
  requestNotificationPermission,
  scheduleReminder,
  type NotificationSupport,
  type ScheduledReminder,
} from '../features/reminders';
import { hoursSince } from '../lib/time';

/**
 * Saved trips, favourites, Trip Ready and reminders.
 *
 * Everything on this page is read from IndexedDB, so it renders identically with
 * no connection. Each trip states which data release it came from and how old that
 * copy is, because a saved trip is a snapshot and presenting it as current would
 * be the one dishonesty that actually costs a passenger a coach.
 */
export function Saved() {
  const { t, language, name, formatClock, formatDateTime, formatServiceDate } = useI18n();
  const { source, manifest, storageAvailable } = useData();
  const dataMode = useDataMode();
  const online = useOnline();
  const { announce, alert } = useAnnouncer();

  const [trips, setTrips] = useState<SavedTrip[] | null>(null);
  const [favorites, setFavorites] = useState<Favorite[]>([]);
  const [wallet, setWallet] = useState<WalletItem[]>([]);
  const [permission, setPermission] = useState<NotificationSupport>(() => notificationSupport());
  const [checked, setChecked] = useState<Record<string, 'unchanged' | 'changed'>>({});
  const [reminderNotice, setReminderNotice] = useState<string | null>(null);
  const scheduled = useRef(new Map<string, ScheduledReminder>());

  useHead({ title: t('meta.saved.title'), description: t('saved.emptyBody'), path: '/saved', language, dataMode });

  const refresh = useCallback(async () => {
    setTrips(await loadSavedTrips());
    setFavorites(await listFavorites());
    setWallet(await listWalletItems());
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  // Re-arm the in-page reminders whenever the saved trips change. A timer only
  // lives as long as this page, which is exactly what the wording promises.
  useEffect(() => {
    for (const entry of scheduled.current.values()) entry.cancel();
    scheduled.current.clear();
    if (!trips || permission !== 'granted') return;
    for (const trip of trips) {
      if (trip.reminderMinutes === null) continue;
      const at = reminderInstant(trip.detail.departure.at, trip.reminderMinutes);
      if (!at) continue;
      const entry = scheduleReminder({
        tripId: trip.id,
        at,
        title: name(trip.detail.routeName ?? trip.detail.operator.name),
        body: t('reminders.bodyText', {
          time: formatClock(trip.detail.departure.at),
          place: name(trip.detail.boardingPoint.name),
        }),
        onFired: () => setReminderNotice(t('reminders.testFired')),
        onRevoked: () => setReminderNotice(t('reminders.permissionRevoked')),
      });
      if (entry) scheduled.current.set(trip.id, entry);
    }
    const armed = scheduled.current;
    return () => {
      for (const entry of armed.values()) entry.cancel();
      armed.clear();
    };
  }, [trips, permission, name, t, formatClock]);

  const manifestReleaseId = manifest.status === 'ready' ? manifest.value.releaseId : null;
  const backgroundCapable = useMemo(() => canDeliverInBackground(), []);

  const checkForChanges = async (trip: SavedTrip) => {
    try {
      const fresh = await source.journey(trip.journeyId, trip.serviceDate);
      const changed = hasMaterialChange(trip.detail, fresh.journey);
      setChecked((current) => ({ ...current, [trip.id]: changed ? 'changed' : 'unchanged' }));
      announce(changed ? t('saved.changed') : t('saved.unchanged'));
    } catch {
      alert(t('saved.refreshOffline'));
    }
  };

  const setReminder = async (trip: SavedTrip, minutes: number | null) => {
    if (minutes !== null && permission !== 'granted') {
      const result = await requestNotificationPermission();
      setPermission(result);
      if (result !== 'granted') {
        alert(result === 'denied' ? t('reminders.permissionDeniedBody') : t('reminders.unsupportedBody'));
        return;
      }
    }
    const at = minutes === null ? null : reminderInstant(trip.detail.departure.at, minutes);
    await updateTrip({
      ...trip,
      reminderMinutes: minutes,
      reminderScheduledFor: at ? at.toISOString() : null,
    });
    announce(minutes === null ? t('reminders.none') : t('reminders.scheduled', { when: at ? formatDateTime(at.toISOString()) : '' }));
    await refresh();
  };

  if (!storageAvailable) {
    return (
      <div className="od-page od-page--narrow">
        <h1 className="od-page__title">{t('saved.title')}</h1>
        <StateBlock kind="unavailable" title={t('saved.storageUnavailable')} body={<p>{t('offline.storageUnavailableBody')}</p>} />
      </div>
    );
  }

  return (
    <div className="od-page od-page--narrow">
      <h1 className="od-page__title">{t('saved.title')}</h1>

      {reminderNotice ? (
        <StateBlock kind="empty" headingLevel={2} title={reminderNotice} announce={false} />
      ) : null}

      {/* -- Reminders, with the limitation stated before the opt-in ------- */}
      <Section title={t('reminders.title')} level={2} description={t('reminders.intro')}>
        <Card tone="muted">
          <h3 className="od-card__title">{t('reminders.backgroundLimitation')}</h3>
          <p>{t('reminders.backgroundLimitationBody')}</p>
          <p className="od-muted">{t('reminders.privacy')}</p>
          {permission === 'unsupported' ? (
            <StateBlock
              kind="unavailable"
              headingLevel={3}
              title={t('reminders.unsupported')}
              body={<p>{t('reminders.unsupportedBody')}</p>}
              announce={false}
            />
          ) : permission === 'denied' ? (
            <StateBlock
              kind="permission_denied"
              headingLevel={3}
              title={t('reminders.permissionDenied')}
              body={<p>{t('reminders.permissionDeniedBody')}</p>}
              announce={false}
            />
          ) : permission === 'default' ? (
            <Button
              tone="secondary"
              onClick={async () => {
                const result = await requestNotificationPermission();
                setPermission(result);
                if (result === 'denied') alert(t('reminders.permissionDeniedBody'));
              }}
            >
              {t('reminders.permissionPrompt')}
            </Button>
          ) : (
            <Badge tone="success" icon="check">
              {t('settings.notifications')}
            </Badge>
          )}
          {!backgroundCapable ? <p className="od-muted">{t('reminders.backgroundLimitationBody')}</p> : null}
        </Card>
      </Section>

      {/* -- Saved trips --------------------------------------------------- */}
      <Section title={t('saved.tripsHeading')} level={2}>
        {trips === null ? (
          <p className="od-muted">{t('app.loading')}</p>
        ) : trips.length === 0 ? (
          <StateBlock
            kind="empty"
            headingLevel={3}
            title={t('saved.emptyTitle')}
            body={<p>{t('saved.emptyBody')}</p>}
            announce={false}
          />
        ) : (
          <ul className="od-list od-list--cards">
            {trips.map((trip) => {
              const readiness = readinessOf(trip);
              const ageHours = hoursSince(trip.savedAt);
              const stale = manifestReleaseId !== null && manifestReleaseId !== trip.releaseId;
              const attached = wallet.filter((item) => trip.walletItemIds.includes(item.id));
              return (
                <li key={trip.id}>
                  <Card as="article">
                    <h3 className="od-card__title">
                      <Link
                        to={`/journey/${encodeURIComponent(trip.journeyId)}?date=${trip.serviceDate}`}
                        className="od-link"
                      >
                        {name(trip.detail.departure.stopName)} {'→'} {name(trip.detail.arrival.stopName)}
                      </Link>
                    </h3>
                    <p>
                      <time dateTime={trip.detail.departure.at}>{formatClock(trip.detail.departure.at)}</time>
                      <span aria-hidden="true"> {'→'} </span>
                      <time dateTime={trip.detail.arrival.at}>{formatClock(trip.detail.arrival.at)}</time>
                      {' · '}
                      <time dateTime={trip.serviceDate}>{formatServiceDate(trip.serviceDate)}</time>
                    </p>
                    <p>
                      <FreshnessBadge freshness={trip.detail.freshness} />{' '}
                      <StepFreeBadge stepFree={trip.detail.boardingPoint.stepFree} />
                    </p>

                    <FactList>
                      <Fact label={t('saved.fromRelease', { releaseId: '' }).trim()}>
                        <span className="od-mono">{trip.releaseId}</span>
                      </Fact>
                      <Fact label={t('saved.savedAt', { when: '' }).trim()}>
                        <time dateTime={trip.savedAt}>{formatDateTime(trip.savedAt)}</time>
                      </Fact>
                      <Fact label={t('journey.boarding')}>
                        {name(trip.detail.boardingPoint.name)}
                        {trip.detail.boardingPoint.bay
                          ? ` · ${t('journey.boardingBay', { bay: trip.detail.boardingPoint.bay })}`
                          : ''}
                      </Fact>
                    </FactList>

                    <p className="od-notice od-notice--warning">
                      {t('saved.ageWarning', { age: t('freshness.age_other', { count: ageHours }) })}
                    </p>
                    {stale ? <p className="od-notice od-notice--info">{t('saved.staleRelease')}</p> : null}
                    {checked[trip.id] === 'changed' ? (
                      <p className="od-notice od-notice--warning">{t('saved.changed')}</p>
                    ) : checked[trip.id] === 'unchanged' ? (
                      <p className="od-notice od-notice--info">{t('saved.unchanged')}</p>
                    ) : null}

                    {/* -- Trip Ready --------------------------------------- */}
                    <Disclosure summary={t('tripReady.title')} defaultOpen>
                      <p className="od-muted">{t('tripReady.help')}</p>
                      <FactList>
                        <Fact label={t('tripReady.schedule')}>
                          <ReadyMark ready={readiness.schedule} />
                        </Fact>
                        <Fact label={t('tripReady.boarding')}>
                          <ReadyMark ready={readiness.boarding} />
                        </Fact>
                        <Fact label={t('tripReady.contacts')}>
                          <ReadyMark ready={readiness.contacts} />
                        </Fact>
                        <Fact label={t('tripReady.ticket')}>
                          {readiness.ticket ? <ReadyMark ready /> : <span className="od-muted">{t('tripReady.ticketNone')}</span>}
                        </Fact>
                        <Fact label={t('tripReady.mapData')}>
                          <ReadyMark ready={readiness.mapData} />
                        </Fact>
                        <Fact label={t('tripReady.mapTiles')}>
                          <span className="od-muted">{t('tripReady.mapTilesNever')}</span>
                        </Fact>
                        <Fact label={t('tripReady.live')}>
                          <span className="od-muted">{t('tripReady.liveNever')}</span>
                        </Fact>
                      </FactList>
                      {attached.length > 0 ? (
                        <p>
                          {t('wallet.linkedTo', { trip: attached.map((item) => item.label || item.fileName).join(', ') })}
                        </p>
                      ) : (
                        <p>
                          <Link to="/wallet" className="od-link">
                            {t('wallet.import')}
                          </Link>
                        </p>
                      )}
                    </Disclosure>

                    {/* -- Reminder for this trip --------------------------- */}
                    <SelectField
                      id={`reminder-${trip.id}`}
                      label={t('reminders.when')}
                      value={trip.reminderMinutes === null ? '' : String(trip.reminderMinutes)}
                      onChange={(event) => void setReminder(trip, event.target.value === '' ? null : Number(event.target.value))}
                      hint={
                        trip.reminderScheduledFor
                          ? t('reminders.scheduled', { when: formatDateTime(trip.reminderScheduledFor) })
                          : t('reminders.none')
                      }
                    >
                      <option value="">{t('reminders.none')}</option>
                      {REMINDER_CHOICES.map((minutes) => (
                        <option key={minutes} value={minutes}>
                          {t('reminders.minutes_other', { count: minutes })}
                        </option>
                      ))}
                    </SelectField>

                    <div className="od-pack__actions">
                      {online ? (
                        <Button tone="secondary" onClick={() => void checkForChanges(trip)}>
                          {t('saved.refresh')}
                        </Button>
                      ) : (
                        <Button tone="secondary" unavailableReason={t('saved.refreshOffline')}>
                          {t('saved.refresh')}
                        </Button>
                      )}
                      <Button
                        tone="danger"
                        onClick={async () => {
                          await removeTrip(trip.id);
                          announce(t('saved.removed'));
                          await refresh();
                        }}
                      >
                        {t('saved.remove')}
                      </Button>
                    </div>
                  </Card>
                </li>
              );
            })}
          </ul>
        )}
      </Section>

      {/* -- Favourites ---------------------------------------------------- */}
      <Section title={t('saved.favoritesHeading')} level={2}>
        {favorites.length === 0 ? (
          <p className="od-muted">{t('saved.favoritesEmpty')}</p>
        ) : (
          <ul className="od-list od-list--plain">
            {favorites.map((favorite) => (
              <li key={favorite.id}>
                <Link
                  to={favorite.kind === 'operator' ? `/operators/${encodeURIComponent(favorite.ref)}` : `/stations/${encodeURIComponent(favorite.ref)}`}
                  className="od-link"
                >
                  {name(favorite.label)}
                </Link>{' '}
                <Button
                  tone="quiet"
                  onClick={async () => {
                    await deleteFavorite(favorite.id);
                    announce(t('saved.removeFavorite'));
                    await refresh();
                  }}
                >
                  {t('app.remove')}
                </Button>
              </li>
            ))}
          </ul>
        )}
      </Section>
    </div>
  );
}

function ReadyMark({ ready }: { ready: boolean }) {
  const { t } = useI18n();
  return ready ? (
    <Badge tone="success" icon="check">
      {t('tripReady.ready')}
    </Badge>
  ) : (
    <Badge tone="neutral" icon="question">
      {t('tripReady.missing')}
    </Badge>
  );
}
