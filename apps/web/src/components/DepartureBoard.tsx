import { useMemo } from 'react';
import sample from '../data/boardSample.generated.json';
import { useI18n } from '../i18n/I18nProvider';
import type { Language } from '../data/contract';

/**
 * The welcome page's object: a terminal departures board.
 *
 * Odivrelo's whole claim is that a departure carries its own provenance, so the
 * board's last column is the thing no coach timetable has ever printed: when
 * this row was last checked, and how old that makes it. That column is the
 * reason the board is here rather than a photograph of a bus station.
 *
 * Every row is real. `scripts/generate-board-sample.py` bakes them out of the
 * published release at release time, because this page is shown before any pack
 * is installed and cannot read the 13.8 MB stops pack to find them itself. The
 * release the rows came from is printed on the board, so a stale extract is
 * visible rather than silent.
 */

type LocalizedText = Readonly<Record<Language, string>>;

interface BoardRow {
  readonly at: string;
  readonly destination: LocalizedText;
  readonly operator: LocalizedText | null;
  readonly quality: string | null;
  readonly checkedAt: string | null;
  readonly freshness: string | null;
}

const ROWS = sample.rows as readonly BoardRow[];

function clock(iso: string): string {
  // The board shows the wall clock of the departure as published, not the
  // reader's own timezone: a coach leaves Nafplio at the time printed in
  // Nafplio.
  const match = /T(\d{2}):(\d{2})/.exec(iso);
  return match ? `${match[1]}:${match[2]}` : '';
}

function dayMonth(iso: string | null, language: Language): string {
  if (!iso) return '';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  return new Intl.DateTimeFormat(language, { day: 'numeric', month: 'short' }).format(date);
}

export function DepartureBoard(): React.JSX.Element {
  const { t, language } = useI18n();
  const origin = (sample.originName as LocalizedText)[language];
  const rows = useMemo(() => ROWS, []);

  return (
    <figure className="od-board" data-ui>
      <div className="od-board__head">
        <span className="od-board__origin">{origin}</span>
        <span className="od-board__date">{sample.serviceDate}</span>
      </div>
      <table className="od-board__table">
        <caption className="od-visually-hidden">
          {t('welcome.boardCaption', { origin })}
        </caption>
        <thead>
          <tr>
            <th scope="col">{t('welcome.boardDeparts')}</th>
            <th scope="col">{t('welcome.boardTo')}</th>
            <th scope="col">{t('welcome.boardChecked')}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={`${row.at}-${row.destination.en}`}>
              <td className="od-board__time">{clock(row.at)}</td>
              <td className="od-board__to">
                <span className="od-board__place">{row.destination[language]}</span>
                {row.operator ? (
                  <span className="od-board__operator">{row.operator[language]}</span>
                ) : null}
              </td>
              <td className="od-board__checked">
                {/*
                  Never a bare colour: the state is a word, and the date is the
                  evidence behind it.
                */}
                <span className="od-board__state" data-freshness={row.freshness ?? 'unknown'}>
                  {t(
                    row.freshness === 'fresh'
                      ? 'freshness.fresh'
                      : row.freshness === 'aging'
                        ? 'freshness.aging'
                        : 'freshness.stale',
                  )}
                </span>
                <span className="od-board__when">{dayMonth(row.checkedAt, language)}</span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <figcaption className="od-board__foot">
        {t('welcome.boardFoot', { release: sample.releaseId })}
      </figcaption>
    </figure>
  );
}
