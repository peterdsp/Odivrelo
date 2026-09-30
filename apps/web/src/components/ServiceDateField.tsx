import { useId } from 'react';
import { useI18n } from '../i18n/I18nProvider';
import { addServiceDays, todayServiceDate } from '../lib/time';
import { isServiceDate } from '../data/contract';
import { Button } from './primitives';

/**
 * Service-date picker.
 *
 * Built on `<input type="date">` deliberately. A hand-rolled calendar grid is
 * where date pickers go to fail keyboard and screen-reader testing; the native
 * control is fully operable from the keyboard, understands the reader's locale
 * and calendar, and honours `min` and `max` so a date outside the release cannot
 * be typed in the first place.
 *
 * The buttons beside it move one day at a time, which is what a passenger
 * actually wants when a service does not run today, and they respect the same
 * bounds.
 */

export interface ServiceDateFieldProps {
  readonly value: string;
  readonly onChange: (serviceDate: string) => void;
  /** Earliest and latest date the data release holds timetables for. */
  readonly min: string;
  readonly max: string;
  readonly error?: string | null;
  readonly now?: Date;
}

export function ServiceDateField({ value, onChange, min, max, error, now }: ServiceDateFieldProps) {
  const { t, formatServiceDate } = useI18n();
  const id = useId();
  const inputId = `${id}-date`;
  const today = todayServiceDate(now);
  const tomorrow = addServiceDays(today, 1);

  const clamp = (candidate: string): string => (candidate < min ? min : candidate > max ? max : candidate);
  const step = (days: number) => {
    const next = clamp(addServiceDays(value, days));
    if (next !== value) onChange(next);
  };

  const describedBy = [`${id}-hint`, `${id}-readable`, error ? `${id}-error` : ''].filter(Boolean).join(' ');

  return (
    <div className={['od-field', 'od-date-field', error ? 'od-field--invalid' : ''].filter(Boolean).join(' ')}>
      <label className="od-field__label" htmlFor={inputId}>
        {t('search.date')}
      </label>
      <div className="od-date-field__row">
        <Button
          tone="quiet"
          onClick={() => step(-1)}
          aria-label={`${t('app.back')}: ${formatServiceDate(clamp(addServiceDays(value, -1)))}`}
          {...(value <= min ? { unavailableReason: t('search.dateOutOfRange', { from: min, to: max }) } : {})}
        >
          <span aria-hidden="true">{'−'}</span>
        </Button>
        <input
          id={inputId}
          name="date"
          className="od-input od-input--date"
          type="date"
          value={value}
          min={min}
          max={max}
          required
          aria-describedby={describedBy}
          aria-invalid={error ? true : undefined}
          onChange={(event) => {
            const next = event.target.value;
            // An empty or half-typed value arrives while the reader is still
            // typing; only a complete, valid, in-range date is committed.
            if (isServiceDate(next)) onChange(clamp(next));
          }}
        />
        <Button
          tone="quiet"
          onClick={() => step(1)}
          aria-label={`${t('search.tomorrow')}: ${formatServiceDate(clamp(addServiceDays(value, 1)))}`}
          {...(value >= max ? { unavailableReason: t('search.dateOutOfRange', { from: min, to: max }) } : {})}
        >
          <span aria-hidden="true">+</span>
        </Button>
      </div>
      <div className="od-date-field__shortcuts">
        <Button
          tone={value === today ? 'primary' : 'quiet'}
          aria-pressed={value === today}
          onClick={() => onChange(clamp(today))}
          {...(today < min || today > max ? { unavailableReason: t('search.dateOutOfRange', { from: min, to: max }) } : {})}
        >
          {t('search.today')}
        </Button>
        <Button
          tone={value === tomorrow ? 'primary' : 'quiet'}
          aria-pressed={value === tomorrow}
          onClick={() => onChange(clamp(tomorrow))}
          {...(tomorrow < min || tomorrow > max
            ? { unavailableReason: t('search.dateOutOfRange', { from: min, to: max }) }
            : {})}
        >
          {t('search.tomorrow')}
        </Button>
      </div>
      {/* The machine-readable value is in the input; this is the sentence a
          reader actually understands, and it is what the input is described by. */}
      <p className="od-date-field__readable" id={`${id}-readable`}>
        {formatServiceDate(value)}
      </p>
      <p className="od-field__hint" id={`${id}-hint`}>
        {t('search.dateHelp')}
      </p>
      {error ? (
        <p className="od-field__error" id={`${id}-error`}>
          {error}
        </p>
      ) : null}
    </div>
  );
}
