import { useI18n } from '../i18n/I18nProvider';
import { useHead } from '../hooks/useHead';
import { useData, useDataMode } from '../app/DataProvider';
import { useAsync } from '../hooks/useAsync';
import { Card, ExternalLink, Fact, FactList, Section } from '../components/primitives';
import { DataError, Loading } from '../components/states';
import { CoverageBadge, RightsBadge } from '../components/quality';
import { provenanceView } from '../data/contract';

/**
 * Coverage and sources.
 *
 * This page exists so that the limits of the data are as easy to find as the data.
 * It states the coverage state, the counts behind it, and then the explicit list of
 * what is *not* covered, which is the part a passenger actually needs: no real
 * country in this release, no live tracking, no ticket sales, and no offline map
 * imagery.
 */
export function Coverage() {
  const { t, language, name, formatDateTime } = useI18n();
  const { source } = useData();
  const dataMode = useDataMode();

  const coverage = useAsync((signal) => source.coverage(signal), [source]);
  const sources = useAsync((signal) => source.sources(signal), [source]);

  useHead({ title: t('meta.coverage.title'), description: t('coverage.notCoveredTitle'), path: '/coverage', language, dataMode });

  return (
    <div className="pv-page pv-page--narrow">
      <h1 className="pv-page__title">{t('coverage.title')}</h1>

      {coverage.state.status === 'loading' ? <Loading /> : null}
      {coverage.state.status === 'error' ? <DataError error={coverage.state.error} onRetry={coverage.reload} /> : null}

      {coverage.state.status === 'ready' ? (
        <>
          <p className="pv-page__lede">
            <CoverageBadge state={coverage.state.value.coverage.state} />
          </p>
          <Card tone="muted">
            <p>{name(coverage.state.value.coverage.note)}</p>
          </Card>

          <Section title={t('coverage.state')} level={2}>
            <FactList>
              <Fact label={t('operators.title')}>{coverage.state.value.coverage.operatorCount}</Fact>
              <Fact label={t('operator.routes')}>{coverage.state.value.coverage.corridorCount}</Fact>
              {coverage.state.value.coverage.stopPlaceCount !== undefined ? (
                <Fact label={t('stations.title')}>{coverage.state.value.coverage.stopPlaceCount}</Fact>
              ) : null}
              {coverage.state.value.coverage.boardingPointCount !== undefined ? (
                <Fact label={t('station.boardingPoints')}>{coverage.state.value.coverage.boardingPointCount}</Fact>
              ) : null}
              {coverage.state.value.coverage.serviceDateFrom && coverage.state.value.coverage.serviceDateTo ? (
                <Fact label={t('search.date')}>
                  {t('coverage.dateRange', {
                    from: coverage.state.value.coverage.serviceDateFrom,
                    to: coverage.state.value.coverage.serviceDateTo,
                  })}
                </Fact>
              ) : null}
            </FactList>
          </Section>

          {coverage.state.value.notCovered && coverage.state.value.notCovered.length > 0 ? (
            <Section title={t('coverage.notCoveredTitle')} level={2}>
              <ul className="pv-list pv-list--cross">
                {coverage.state.value.notCovered.map((statement) => (
                  <li key={statement.code}>{name(statement.text)}</li>
                ))}
              </ul>
            </Section>
          ) : null}

          {coverage.state.value.absenceSemantics ? (
            <Section title={t('coverage.absenceTitle')} level={2} description={t('coverage.absenceHelp')}>
              <FactList>
                {Object.entries(coverage.state.value.absenceSemantics).map(([status, meaning]) => (
                  <Fact key={status} label={status}>
                    {meaning}
                  </Fact>
                ))}
              </FactList>
            </Section>
          ) : null}
        </>
      ) : null}

      <Section title={t('coverage.sourcesTitle')} level={2}>
        {sources.state.status === 'loading' ? <Loading /> : null}
        {sources.state.status === 'error' ? <DataError error={sources.state.error} onRetry={sources.reload} /> : null}
        {sources.state.status === 'ready' ? (
          <ul className="pv-list pv-list--cards pv-provenance">
            {sources.state.value.sources.map((entry, index) => {
              const view = provenanceView(entry, index);
              return (
                <li key={view.key}>
                  <Card as="article">
                    <h3 className="pv-card__title">
                      {view.url ? (
                        <ExternalLink href={view.url} accessibleLabel={view.name}>
                          {view.name}
                        </ExternalLink>
                      ) : (
                        view.name
                      )}
                    </h3>
                    <FactList>
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
                    {entry.note ? <p className="pv-muted">{name(entry.note)}</p> : null}
                  </Card>
                </li>
              );
            })}
          </ul>
        ) : null}
      </Section>

      <Card tone="muted">
        <p>{t('app.neverSellsTickets')}</p>
        <p>{t('app.independence')}</p>
      </Card>
    </div>
  );
}
