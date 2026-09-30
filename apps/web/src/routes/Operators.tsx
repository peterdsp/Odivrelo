import { Link, useParams } from 'react-router-dom';
import { useI18n } from '../i18n/I18nProvider';
import { useHead, organizationNode } from '../hooks/useHead';
import { useData, useDataMode } from '../app/DataProvider';
import { useAsync } from '../hooks/useAsync';
import { Card, ExternalLink, Fact, FactList, Section } from '../components/primitives';
import { DataError, Loading } from '../components/states';
import { CoverageBadge, RightsBadge } from '../components/quality';
import { FavoriteToggle } from '../features/FavoriteToggle';
import { BRAND } from '../brand/brand';
import { hostnameOf, provenanceView, safeHref } from '../data/contract';

/**
 * The operator directory.
 *
 * Together with the station pages this is one of the two semantic, indexable
 * surfaces: a real `h1`, a description list rather than a grid of divs, structured
 * data, Open Graph tags, and an entry in the sitemap. While the release is
 * demonstration data every one of those pages also emits `noindex, nofollow`, and
 * the sitemap is not published at all: an invented region has no business in a
 * search index.
 */
export function Operators() {
  const { t, language, name } = useI18n();
  const { source } = useData();
  const dataMode = useDataMode();
  const operators = useAsync((signal) => source.operators(signal), [source]);

  useHead({
    title: t('meta.operators.title'),
    description: t('operators.intro'),
    path: '/operators',
    language,
    dataMode,
    indexable: true,
    structuredData:
      operators.state.status === 'ready'
        ? {
            '@context': 'https://schema.org',
            '@type': 'ItemList',
            name: t('operators.title'),
            itemListElement: operators.state.value.operators.map((operator, index) => ({
              '@type': 'ListItem',
              position: index + 1,
              item: {
                '@type': 'Organization',
                name: name(operator.name),
                url: `${BRAND.url}/operators/${operator.id}`,
              },
            })),
          }
        : undefined,
  });

  return (
    <div className="pv-page pv-page--narrow">
      <h1 className="pv-page__title">{t('operators.title')}</h1>
      <p className="pv-page__lede">{t('operators.intro')}</p>

      {operators.state.status === 'loading' ? <Loading /> : null}
      {operators.state.status === 'error' ? <DataError error={operators.state.error} onRetry={operators.reload} /> : null}

      {operators.state.status === 'ready' ? (
        <Section title={t('operators.count_other', { count: operators.state.value.operators.length })} level={2}>
          <ul className="pv-list pv-list--cards">
            {operators.state.value.operators.map((operator) => (
              <li key={operator.id}>
                <Card as="article">
                  <h3 className="pv-card__title">
                    <Link to={`/operators/${encodeURIComponent(operator.id)}`} className="pv-link">
                      {name(operator.name)}
                    </Link>
                  </h3>
                  <p>
                    <CoverageBadge state={operator.coverage.state} />
                  </p>
                  <p className="pv-muted">
                    {t(
                      operator.coverage.routeCount === 1 ? 'operator.routeCount_one' : 'operator.routeCount_other',
                      { count: operator.coverage.routeCount },
                    )}
                    {' · '}
                    {t(operator.coverage.stopCount === 1 ? 'operator.stopCount_one' : 'operator.stopCount_other', {
                      count: operator.coverage.stopCount,
                    })}
                  </p>
                </Card>
              </li>
            ))}
          </ul>
        </Section>
      ) : null}
      <p className="pv-muted">{t('operator.noLogo')}</p>
    </div>
  );
}

export function OperatorDetail() {
  const { t, language, name, formatDateTime } = useI18n();
  const { source } = useData();
  const dataMode = useDataMode();
  const params = useParams<{ id: string }>();
  const id = params.id ?? '';

  const loaded = useAsync((signal) => source.operator(id, signal), [source, id], { enabled: id.length > 0 });
  const operator = loaded.state.status === 'ready' ? loaded.state.value.operator : null;
  const operatorName = operator ? name(operator.name) : '';

  useHead({
    title: operator ? t('meta.operator.title', { name: operatorName }) : t('operator.notFoundTitle'),
    description: operator ? t('meta.operator.description', { name: operatorName }) : undefined,
    path: `/operators/${id}`,
    language,
    dataMode,
    indexable: operator !== null,
    structuredData: operator
      ? {
          '@context': 'https://schema.org',
          '@type': 'Organization',
          name: operatorName,
          url: operator.officialSiteUrl ?? `${BRAND.url}/operators/${operator.id}`,
          ...(operator.contact?.phone ? { telephone: operator.contact.phone } : {}),
          ...(operator.contact?.email ? { email: operator.contact.email } : {}),
          ...(operator.contact?.address ? { address: operator.contact.address } : {}),
          subjectOf: organizationNode(),
        }
      : undefined,
  });

  if (loaded.state.status === 'loading') return <Loading />;
  if (loaded.state.status === 'error') {
    return (
      <div className="pv-page pv-page--narrow">
        <h1 className="pv-page__title">{t('operator.notFoundTitle')}</h1>
        <DataError
          error={loaded.state.error}
          onRetry={loaded.reload}
          notFoundTitle={t('operator.notFoundTitle')}
          notFoundBody={<p>{t('operator.notFoundBody')}</p>}
        />
      </div>
    );
  }
  if (!operator) return null;

  return (
    <div className="pv-page pv-page--narrow">
      <nav aria-label={t('a11y.breadcrumb')} className="pv-breadcrumb">
        <Link to="/operators" className="pv-link">
          {t('operators.title')}
        </Link>
      </nav>

      <h1 className="pv-page__title">{operatorName}</h1>
      <p className="pv-page__lede">
        <CoverageBadge state={operator.coverage.state} />
      </p>

      <FavoriteToggle kind="operator" targetId={operator.id} label={operator.name} />

      <Section title={t('operator.contact')} level={2}>
        {operator.contact ? (
          <FactList>
            {operator.contact.phone ? (
              <Fact label={t('booking.phone')}>
                <a className="pv-link" href={`tel:${operator.contact.phone.replace(/\s+/g, '')}`}>
                  {operator.contact.phone}
                </a>
              </Fact>
            ) : null}
            {operator.contact.email ? (
              <Fact label={t('settings.supportEmail')}>
                <a className="pv-link" href={`mailto:${operator.contact.email}`}>
                  {operator.contact.email}
                </a>
              </Fact>
            ) : null}
            {operator.contact.address ? <Fact label={t('booking.address')}>{operator.contact.address}</Fact> : null}
            {safeHref(operator.officialSiteUrl) ? (
              <Fact label={t('operator.official')}>
                <ExternalLink href={safeHref(operator.officialSiteUrl)!} accessibleLabel={operatorName}>
                  {hostnameOf(operator.officialSiteUrl) ?? operatorName}
                </ExternalLink>
              </Fact>
            ) : null}
            {safeHref(operator.directoryUrl) ? (
              <Fact label={t('operator.directory')}>
                <ExternalLink href={safeHref(operator.directoryUrl)!} accessibleLabel={t('operator.directory')}>
                  {hostnameOf(operator.directoryUrl) ?? t('operator.directory')}
                </ExternalLink>
              </Fact>
            ) : null}
          </FactList>
        ) : (
          <p className="pv-muted">{t('operator.noContact')}</p>
        )}
        <p className="pv-detail__note">{t('operator.verifiedAt', { when: formatDateTime(operator.verifiedAt) })}</p>
      </Section>

      <Section title={t('operator.coverage')} level={2}>
        <FactList>
          <Fact label={t('operator.routes')}>
            {t(operator.coverage.routeCount === 1 ? 'operator.routeCount_one' : 'operator.routeCount_other', {
              count: operator.coverage.routeCount,
            })}
          </Fact>
          <Fact label={t('journey.stops')}>
            {t(operator.coverage.stopCount === 1 ? 'operator.stopCount_one' : 'operator.stopCount_other', {
              count: operator.coverage.stopCount,
            })}
          </Fact>
        </FactList>
      </Section>

      <Section title={t('operator.attribution')} level={2}>
        <ul className="pv-list pv-list--plain pv-provenance">
          {operator.sources.map((entry, index) => {
            const view = provenanceView(entry, index);
            return (
              <li key={view.key}>
                <FactList>
                  <Fact label={t('journey.source')}>
                    {view.url ? (
                      <ExternalLink href={view.url} accessibleLabel={view.name}>
                        {view.name}
                      </ExternalLink>
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
        {safeHref(operator.correctionUrl) ? (
          <p>
            <ExternalLink href={safeHref(operator.correctionUrl)!} accessibleLabel={t('journey.correction')}>
              {t('journey.correction')}
            </ExternalLink>
          </p>
        ) : null}
        <p className="pv-muted">{t('operator.noLogo')}</p>
      </Section>

      <Card tone="muted">
        <p>{t('app.neverSellsTickets')}</p>
        <p>{t('app.independence')}</p>
      </Card>
    </div>
  );
}
