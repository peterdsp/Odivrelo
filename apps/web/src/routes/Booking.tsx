import { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { isServiceDate, type PurchaseKind } from '../data/contract';
import { useI18n } from '../i18n/I18nProvider';
import { useHead } from '../hooks/useHead';
import { useData, useDataMode } from '../app/DataProvider';
import { useAsync } from '../hooks/useAsync';
import { Button, ButtonLink, Card, CopyButton, ExternalLink, Fact, FactList, Section } from '../components/primitives';
import { DataError, Loading, StateBlock } from '../components/states';
import { decomposeJourneyId } from '../data/packQuery';
import { hostnameOf, safeHref } from '../data/contract';
import { readPref, writePref } from '../lib/prefs';

/**
 * The official booking handoff.
 *
 * Odivrelo sells nothing, so the only thing this page can honestly do is take the
 * reader to the operator, or tell them exactly where to buy in person. Both
 * outcomes are complete: the online case names the operator and its own domain,
 * the offline case gives the verified office, telephone, address and opening
 * hours with the date they were checked, and the "not established" case says that
 * rather than pretending tickets cannot be bought.
 *
 * Returning: the handoff opens in a new context, so this page and everything
 * behind it stay exactly as they were. A note records that the reader went out,
 * so coming back is acknowledged rather than silently identical.
 */

const RETURN_PREF = 'bookingHandoff';

interface HandoffRecord {
  readonly journeyId: string;
  readonly at: string;
}

const KIND_ORDER: readonly PurchaseKind[] = ['online', 'ticket_office', 'phone', 'onboard', 'unavailable'];

export function Booking() {
  const { t, language, name, formatDateTime, formatClock, formatServiceDate } = useI18n();
  const { source } = useData();
  const dataMode = useDataMode();
  const params = useParams<{ id: string }>();
  const [query] = useSearchParams();
  const [returned, setReturned] = useState(false);

  const journeyId = params.id ?? '';
  const dateParam = query.get('date');
  const serviceDate = dateParam && isServiceDate(dateParam) ? dateParam : null;
  const shapeValid = decomposeJourneyId(journeyId) !== null;

  const loaded = useAsync(
    (signal) => source.journey(journeyId, serviceDate as string, signal),
    [source, journeyId, serviceDate],
    { enabled: shapeValid && serviceDate !== null },
  );
  const journey = loaded.state.status === 'ready' ? loaded.state.value.journey : null;

  const operator = useAsync(
    (signal) => source.operator(journey?.operator.id as string, signal),
    [source, journey?.operator.id],
    { enabled: Boolean(journey?.operator.id) },
  );

  useHead({
    title: t('booking.title'),
    description: t('booking.disclaimer'),
    path: `/journey/${journeyId}/booking${serviceDate ? `?date=${serviceDate}` : ''}`,
    language,
    dataMode,
  });

  // If a handoff was recorded for this journey, the reader has come back.
  useEffect(() => {
    const record = readPref<HandoffRecord | null>(RETURN_PREF, null, (raw) => {
      if (!raw || typeof raw !== 'object') return null;
      const value = raw as Record<string, unknown>;
      return typeof value.journeyId === 'string' && typeof value.at === 'string'
        ? { journeyId: value.journeyId, at: value.at }
        : null;
    });
    if (record && record.journeyId === journeyId) setReturned(true);
  }, [journeyId]);

  const recordHandoff = () => {
    writePref(RETURN_PREF, { journeyId, at: new Date().toISOString() } satisfies HandoffRecord);
  };

  if (!serviceDate || !shapeValid) {
    return (
      <div className="od-page od-page--narrow">
        <h1 className="od-page__title">{t('state.invalidLinkTitle')}</h1>
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

  if (loaded.state.status === 'loading') return <Loading />;
  if (loaded.state.status === 'error') {
    return (
      <div className="od-page od-page--narrow">
        <h1 className="od-page__title">{t('booking.title')}</h1>
        <DataError
          error={loaded.state.error}
          onRetry={loaded.reload}
          notFoundTitle={t('journey.notFoundTitle')}
          notFoundBody={<p>{t('journey.notFoundBody')}</p>}
        />
      </div>
    );
  }
  if (!journey) return null;

  const purchase = journey.purchase;
  const operatorName = name(journey.operator.name);
  const operatorValue = operator.state.status === 'ready' ? operator.state.value.operator : null;
  const officialDomain = hostnameOf(purchase.url);
  const verifiedAt = operatorValue?.verifiedAt ?? null;
  const kind: PurchaseKind = KIND_ORDER.includes(purchase.kind) ? purchase.kind : 'unavailable';

  const hasOffice = Boolean(purchase.address || purchase.phone || purchase.openingHours);

  return (
    <div className="od-page od-page--narrow">
      <nav aria-label={t('a11y.breadcrumb')} className="od-breadcrumb">
        <Link to={`/journey/${encodeURIComponent(journeyId)}?date=${serviceDate}`} className="od-link">
          {t('journey.title', { origin: name(journey.departure.stopName), destination: name(journey.arrival.stopName) })}
        </Link>
      </nav>

      <h1 className="od-page__title">{t('booking.title')}</h1>

      <Card tone="muted">
        <p className="od-booking__journey">
          <time dateTime={journey.departure.at}>{formatClock(journey.departure.at)}</time>
          <span aria-hidden="true"> {'→'} </span>
          <time dateTime={journey.arrival.at}>{formatClock(journey.arrival.at)}</time>
          {' · '}
          <time dateTime={journey.serviceDate}>{formatServiceDate(journey.serviceDate)}</time>
        </p>
        <p>{operatorName}</p>
      </Card>

      {returned ? (
        <StateBlock kind="empty" headingLevel={2} title={t('booking.returned')} announce={false} />
      ) : null}

      {/* -- The primary action, whatever it honestly is -------------------- */}
      <Section title={t('journey.operator')} level={2}>
        {kind === 'online' && safeHref(purchase.url) ? (
          <>
            <ExternalLink
              href={safeHref(purchase.url)!}
              tone="primary"
              accessibleLabel={t('booking.onlineAction', { operator: operatorName })}
              onClick={recordHandoff}
            >
              {t('booking.onlineAction', { operator: operatorName })}
            </ExternalLink>
            <p className="od-detail__note">{t('booking.onlineBody', { operator: operatorName })}</p>
            {officialDomain ? <p className="od-mono od-detail__note">{officialDomain}</p> : null}
            <p className="od-detail__note">{t('booking.returnHere')}</p>
          </>
        ) : null}

        {kind === 'ticket_office' ? (
          <>
            <h3 className="od-booking__subtitle">{t('booking.officeAction')}</h3>
            <p>{t('booking.officeBody')}</p>
          </>
        ) : null}

        {kind === 'phone' ? (
          <>
            <h3 className="od-booking__subtitle">{t('booking.phoneAction', { operator: operatorName })}</h3>
            <p>{t('booking.phoneBody')}</p>
          </>
        ) : null}

        {kind === 'onboard' ? (
          <>
            <h3 className="od-booking__subtitle">{t('booking.onboardTitle')}</h3>
            <p>{t('booking.onboardBody')}</p>
          </>
        ) : null}

        {kind === 'unavailable' ? (
          <StateBlock
            kind="restricted"
            headingLevel={2}
            title={t('booking.unavailableTitle')}
            body={<p>{t('booking.unavailableBody')}</p>}
            announce={false}
          />
        ) : null}

        {kind !== 'online' ? <p className="od-detail__note">{t('booking.noSaleTitle')}</p> : null}
      </Section>

      {/* -- The verified fallback, always shown when it exists ------------- */}
      {hasOffice ? (
        <Section title={t('booking.office')} level={2}>
          <Card>
            <FactList>
              {purchase.address ? (
                <Fact label={t('booking.address')}>
                  {purchase.address} <CopyButton value={purchase.address} />
                </Fact>
              ) : null}
              {purchase.phone ? (
                <Fact label={t('booking.phone')}>
                  <a className="od-link" href={`tel:${purchase.phone.replace(/\s+/g, '')}`}>
                    {purchase.phone}
                  </a>{' '}
                  <CopyButton value={purchase.phone} />
                </Fact>
              ) : null}
              {purchase.openingHours ? <Fact label={t('booking.openingHours')}>{purchase.openingHours}</Fact> : null}
              {safeHref(operatorValue?.officialSiteUrl) ? (
                <Fact label={t('operator.official')}>
                  <ExternalLink href={safeHref(operatorValue?.officialSiteUrl)!} accessibleLabel={operatorName}>
                    {hostnameOf(operatorValue?.officialSiteUrl) ?? operatorName}
                  </ExternalLink>
                </Fact>
              ) : null}
            </FactList>
            {verifiedAt ? <p className="od-detail__note">{t('booking.verifiedAt', { when: formatDateTime(verifiedAt) })}</p> : null}
          </Card>
        </Section>
      ) : (
        <Section title={t('booking.office')} level={2}>
          <p className="od-muted">{t('operator.noContact')}</p>
        </Section>
      )}

      {/* -- The statement, on this screen as on every other --------------- */}
      <Card tone="muted" className="od-booking__disclaimer">
        <p>{t('booking.disclaimer')}</p>
        <p>{t('app.independence')}</p>
      </Card>

      <div className="od-detail__actions">
        <ButtonLink tone="secondary" to={`/journey/${encodeURIComponent(journeyId)}?date=${serviceDate}`}>
          {t('app.back')}
        </ButtonLink>
        {returned ? null : (
          <Button
            tone="quiet"
            onClick={() => {
              recordHandoff();
              setReturned(true);
            }}
          >
            {t('booking.markReturned')}
          </Button>
        )}
      </div>
    </div>
  );
}
