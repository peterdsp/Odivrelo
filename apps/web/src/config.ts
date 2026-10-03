/**
 * Build-time and run-time configuration.
 *
 * The four stamps (version, commit, build time, data release id) are surfaced
 * verbatim in Settings. The first three are fixed at build time; the fourth
 * comes from whichever release the app is actually reading, so it is resolved
 * at run time by the data layer rather than guessed here.
 */
import { BRAND } from './brand/brand';

declare const __APP_VERSION__: string;
declare const __GIT_COMMIT__: string;
declare const __BUILD_TIME__: string;

function defined(value: unknown, fallback: string): string {
  return typeof value === 'string' && value.length > 0 ? value : fallback;
}

export const buildStamp = {
  version: defined(typeof __APP_VERSION__ === 'string' ? __APP_VERSION__ : undefined, BRAND.version),
  commit: defined(typeof __GIT_COMMIT__ === 'string' ? __GIT_COMMIT__ : undefined, 'unknown'),
  builtAt: defined(typeof __BUILD_TIME__ === 'string' ? __BUILD_TIME__ : undefined, 'unknown'),
} as const;

export type DataSourceKind = 'static' | 'http';

export interface AppConfig {
  readonly dataSource: DataSourceKind;
  readonly apiBaseUrl: string | null;
  readonly staticDataBase: string;
  /**
   * The MapLibre style the basemap loads. Defaults to the free, keyless
   * OpenFreeMap "liberty" style. Set `VITE_MAP_STYLE_URL` to point at another
   * provider, or to the empty string to draw the route and stops on a plain
   * background with no network request (the offline-first diagram).
   */
  readonly mapStyleUrl: string;
}

/** The documented free, keyless, attribution-required OpenFreeMap style. */
export const DEFAULT_MAP_STYLE_URL = 'https://tiles.openfreemap.org/styles/liberty';

function readEnv(): AppConfig {
  const env = import.meta.env ?? {};
  const apiBaseUrl = typeof env.VITE_API_BASE_URL === 'string' && env.VITE_API_BASE_URL.length > 0 ? env.VITE_API_BASE_URL : null;
  const requested = env.VITE_DATA_SOURCE;
  // Static is the default, and stays the default when no API is configured:
  // a selection that cannot work is not honoured silently.
  const dataSource: DataSourceKind = requested === 'http' && apiBaseUrl ? 'http' : 'static';
  const mapStyleUrl =
    typeof env.VITE_MAP_STYLE_URL === 'string' ? env.VITE_MAP_STYLE_URL : DEFAULT_MAP_STYLE_URL;
  return { dataSource, apiBaseUrl, staticDataBase: '/data/', mapStyleUrl };
}

export const appConfig: AppConfig = readEnv();

/** Where the app can be reached. Used for canonical links and structured data. */
export const siteUrl = BRAND.url;
