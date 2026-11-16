/**
 * One real day of departures from Athens, for the timetable sheet on the welcome page.
 *
 * Copied from the published release (data release fc6d12357efe64da, service date
 * Wednesday 11 November 2026), so the page shows what the search really returns
 * without loading a data pack before the reader has asked for anything. The page
 * names the date, so these rows never pass for today's departures.
 */
export type BoardTerminal = 'kifisos' | 'liosion';
export interface BoardRow {
  readonly depart: string;
  readonly arrive: string;
  readonly terminal: BoardTerminal;
  readonly fare: number;
}
export interface BoardRoute {
  readonly id: 'patras' | 'tiryns' | 'amfissa';
  readonly operator: { readonly el: string; readonly en: string; readonly sq: string };
  readonly rows: readonly BoardRow[];
}

export const BOARD_SERVICE_DATE = '2026-11-11';

export const BOARD_ROUTES: readonly BoardRoute[] = [
  {
    id: 'patras',
    operator: { el: 'ΚΤΕΛ Αχαΐας', en: 'KTEL Achaia', sq: 'KTEL Achaia' },
    rows: [
      { depart: '05:30', arrive: '08:14', terminal: 'kifisos', fare: 22.6 },
      { depart: '07:15', arrive: '09:59', terminal: 'kifisos', fare: 22.6 },
      { depart: '08:00', arrive: '10:44', terminal: 'kifisos', fare: 22.6 },
      { depart: '09:00', arrive: '11:44', terminal: 'kifisos', fare: 22.6 },
      { depart: '10:00', arrive: '12:44', terminal: 'kifisos', fare: 22.6 },
      { depart: '11:00', arrive: '13:44', terminal: 'kifisos', fare: 22.6 },
      { depart: '12:00', arrive: '14:44', terminal: 'kifisos', fare: 22.6 },
      { depart: '13:00', arrive: '15:44', terminal: 'kifisos', fare: 22.6 },
      { depart: '14:00', arrive: '16:44', terminal: 'kifisos', fare: 22.6 },
      { depart: '15:00', arrive: '17:44', terminal: 'kifisos', fare: 22.6 },
      { depart: '16:00', arrive: '18:44', terminal: 'kifisos', fare: 22.6 },
      { depart: '17:00', arrive: '19:44', terminal: 'kifisos', fare: 22.6 },
      { depart: '18:00', arrive: '20:44', terminal: 'kifisos', fare: 22.6 },
      { depart: '19:00', arrive: '21:44', terminal: 'kifisos', fare: 22.6 },
      { depart: '20:15', arrive: '22:59', terminal: 'kifisos', fare: 22.6 },
      { depart: '21:15', arrive: '23:59', terminal: 'kifisos', fare: 22.6 },
      { depart: '22:00', arrive: '00:44', terminal: 'kifisos', fare: 22.6 },
    ],
  },
  {
    id: 'tiryns',
    operator: { el: 'ΚΤΕΛ Αργολίδας', en: 'KTEL Argolida', sq: 'KTEL Argolida' },
    rows: [
      { depart: '06:00', arrive: '07:56', terminal: 'kifisos', fare: 15.3 },
      { depart: '08:00', arrive: '09:56', terminal: 'kifisos', fare: 15.3 },
      { depart: '09:30', arrive: '11:26', terminal: 'kifisos', fare: 15.3 },
      { depart: '11:00', arrive: '12:56', terminal: 'kifisos', fare: 15.3 },
      { depart: '12:30', arrive: '14:26', terminal: 'kifisos', fare: 15.3 },
      { depart: '14:00', arrive: '15:56', terminal: 'kifisos', fare: 15.3 },
      { depart: '15:30', arrive: '17:26', terminal: 'kifisos', fare: 15.3 },
      { depart: '16:30', arrive: '18:26', terminal: 'kifisos', fare: 15.3 },
      { depart: '17:30', arrive: '19:26', terminal: 'kifisos', fare: 15.3 },
      { depart: '18:30', arrive: '20:26', terminal: 'kifisos', fare: 15.3 },
      { depart: '20:00', arrive: '21:56', terminal: 'kifisos', fare: 15.3 },
      { depart: '21:30', arrive: '23:26', terminal: 'kifisos', fare: 15.3 },
    ],
  },
  {
    id: 'amfissa',
    operator: { el: 'ΚΤΕΛ Φωκίδας', en: 'KTEL Fokida', sq: 'KTEL Fokida' },
    rows: [
      { depart: '08:30', arrive: '11:25', terminal: 'liosion', fare: 20.6 },
      { depart: '10:30', arrive: '13:25', terminal: 'liosion', fare: 26.3 },
      { depart: '18:00', arrive: '20:55', terminal: 'liosion', fare: 20.6 },
    ],
  },
];
