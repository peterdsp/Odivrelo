import { useCallback, useEffect, useId, useRef, useState } from 'react';
import type { Place } from '../data/contract';
import { useI18n } from '../i18n/I18nProvider';
import { Badge } from './primitives';

/**
 * Place picker with explicit disambiguation.
 *
 * "Velmara Central Terminal" and "Velmara Central, Bay A1" are different things,
 * and choosing the wrong one puts a passenger on the wrong side of a station.
 * Each suggestion therefore states whether it is a terminal or a single boarding
 * point, how many boarding points a terminal has, and which municipality it is
 * in, and a terminal's own boarding points are shown indented beneath it.
 *
 * Implemented as the ARIA 1.2 combobox pattern: a text input that owns a
 * listbox, driven by `aria-activedescendant` so the input keeps focus and the
 * reader hears each option as it is reached.
 */

export interface PlaceFieldProps {
  readonly label: string;
  readonly placeholder: string;
  readonly value: Place | null;
  readonly onChange: (place: Place | null) => void;
  readonly search: (query: string) => Promise<readonly Place[]>;
  readonly error?: string | null;
  readonly hint?: string;
  readonly name: string;
  readonly required?: boolean;
}

export function PlaceField({
  label,
  placeholder,
  value,
  onChange,
  search,
  error,
  hint,
  name,
  required = false,
}: PlaceFieldProps) {
  const { t, name: localName } = useI18n();
  const id = useId();
  const inputId = `${id}-input`;
  const listId = `${id}-list`;
  const statusId = `${id}-status`;

  const [text, setText] = useState(() => (value ? localName(value.name) : ''));
  const [options, setOptions] = useState<readonly Place[]>([]);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const [searching, setSearching] = useState(false);
  const blurTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Keep the field text in step when the value is changed from outside, for
  // example by the swap control or by restoring state from the URL.
  useEffect(() => {
    setText(value ? localName(value.name) : '');
  }, [value, localName]);

  const runSearch = useCallback(
    async (query: string) => {
      setSearching(true);
      try {
        const found = await search(query);
        /*
         * With nothing typed, lead with the terminals. A bare list of every bay
         * at every station is noise, and the terminal is nearly always what a
         * passenger means; typing anything shows boarding points too. The data
         * layer returns the whole list either way, because other callers need to
         * resolve an arbitrary place id from it.
         */
        const terminalsOnly = query.trim().length === 0 ? found.filter((place) => place.kind === 'stop_place') : found;
        const shown = terminalsOnly.length > 0 ? terminalsOnly : found;
        setOptions(shown);
        setActive(shown.length > 0 ? 0 : -1);
      } catch {
        // A failed lookup leaves the list empty; the page-level error state
        // reports why, and typing again retries.
        setOptions([]);
        setActive(-1);
      } finally {
        setSearching(false);
      }
    },
    [search],
  );

  useEffect(() => {
    if (!open) return;
    const handle = globalThis.setTimeout(() => void runSearch(text), 140);
    return () => globalThis.clearTimeout(handle);
  }, [open, text, runSearch]);

  const choose = (place: Place) => {
    onChange(place);
    setText(localName(place.name));
    setOpen(false);
    setActive(-1);
  };

  const onKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (!open) {
        setOpen(true);
        return;
      }
      if (options.length === 0) return;
      const delta = event.key === 'ArrowDown' ? 1 : -1;
      setActive((current) => (current + delta + options.length) % options.length);
      return;
    }
    if (event.key === 'Home' && open) {
      event.preventDefault();
      setActive(0);
      return;
    }
    if (event.key === 'End' && open) {
      event.preventDefault();
      setActive(options.length - 1);
      return;
    }
    if (event.key === 'Enter') {
      if (open && active >= 0 && options[active]) {
        event.preventDefault();
        choose(options[active]);
      }
      return;
    }
    if (event.key === 'Escape') {
      if (open) {
        event.preventDefault();
        setOpen(false);
        setActive(-1);
      }
    }
  };

  const described = [hint ? `${id}-hint` : '', error ? `${id}-error` : '', statusId].filter(Boolean).join(' ');
  const activeId = open && active >= 0 && options[active] ? `${id}-option-${options[active].id}` : undefined;

  return (
    <div className={['od-field', 'od-place-field', error ? 'od-field--invalid' : ''].filter(Boolean).join(' ')}>
      <label className="od-field__label" htmlFor={inputId}>
        {label}
      </label>
      <div className="od-place-field__control">
        <input
          id={inputId}
          name={name}
          className="od-input"
          type="text"
          role="combobox"
          autoComplete="off"
          spellCheck={false}
          aria-expanded={open}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-describedby={described}
          aria-invalid={error ? true : undefined}
          {...(activeId ? { 'aria-activedescendant': activeId } : {})}
          {...(required ? { required: true } : {})}
          placeholder={placeholder}
          value={text}
          onFocus={() => setOpen(true)}
          onBlur={() => {
            // A click on an option fires blur first; give it a tick to land.
            blurTimer.current = globalThis.setTimeout(() => setOpen(false), 120);
          }}
          onChange={(event) => {
            setText(event.target.value);
            setOpen(true);
            // Typing invalidates a previous choice: the form must not submit a
            // place the reader can no longer see in the field.
            if (value) onChange(null);
          }}
          onKeyDown={onKeyDown}
        />
        {value ? (
          <span className="od-place-field__chosen">
            {value.kind === 'stop_place' ? (
              <Badge tone="info" icon="route">
                {t('search.terminal')}
              </Badge>
            ) : (
              <Badge tone="accent" icon="check">
                {t('search.boardingPoint')}
              </Badge>
            )}
          </span>
        ) : null}
      </div>

      {hint ? (
        <p className="od-field__hint" id={`${id}-hint`}>
          {hint}
        </p>
      ) : null}
      {error ? (
        <p className="od-field__error" id={`${id}-error`}>
          {error}
        </p>
      ) : null}

      <p className="od-visually-hidden" id={statusId} role="status" aria-live="polite">
        {open
          ? searching
            ? t('search.searching')
            : options.length === 0
              ? t('search.noMatches', { query: text })
              : t('search.boardingPointCount_other', { count: options.length })
          : ''}
      </p>

      <ul
        className="od-place-field__list"
        id={listId}
        role="listbox"
        aria-label={t('search.results')}
        hidden={!open || options.length === 0}
      >
        {options.map((place, index) => (
          <li
            key={place.id}
            id={`${id}-option-${place.id}`}
            role="option"
            aria-selected={index === active}
            className={[
              'od-place-option',
              index === active ? 'od-place-option--active' : '',
              place.kind === 'stop' ? 'od-place-option--child' : '',
            ]
              .filter(Boolean)
              .join(' ')}
            onMouseDown={(event) => {
              // Prevent the input losing focus before the click is handled.
              event.preventDefault();
              if (blurTimer.current !== null) globalThis.clearTimeout(blurTimer.current);
              choose(place);
            }}
            onMouseEnter={() => setActive(index)}
          >
            <span className="od-place-option__name">{localName(place.name)}</span>
            <span className="od-place-option__meta">
              {place.kind === 'stop_place' ? t('search.terminal') : t('search.boardingPoint')}
              {' · '}
              {place.municipality}
              {place.kind === 'stop_place'
                ? ` · ${t(
                    place.boardingPointCount === 0
                      ? 'search.boardingPointCount_zero'
                      : place.boardingPointCount === 1
                        ? 'search.boardingPointCount_one'
                        : 'search.boardingPointCount_other',
                    { count: place.boardingPointCount },
                  )}`
                : place.bay
                  ? ` · ${t('journey.boardingBay', { bay: place.bay })}`
                  : ''}
              {place.coverage === 'not_covered' ? ` · ${t('coverage.stateNotCovered')}` : ''}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
