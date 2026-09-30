import { useEffect, useRef, useState } from 'react';
import type { Geometry, JourneyStop } from '../data/contract';
import { useI18n } from '../i18n/I18nProvider';
import { BRAND_COLORS } from '../styles/brandColors.generated';
import { Button } from './primitives';
import { GeometryBadge } from './quality';
import { StateBlock } from './states';

/**
 * The route map.
 *
 * Three deliberate decisions:
 *
 * 1. MapLibre is imported dynamically, so a reader who never opens a map never
 *    downloads it. It is by far the largest dependency in the app.
 * 2. The style has no tile source, no sprite and no glyph URL, so the map makes
 *    no network request of any kind. It draws the route geometry and the stops
 *    from data the page already holds, which means it works identically offline
 *    and downloads no imagery this app has not shipped. The panel says so,
 *    rather than letting a blank background imply a failure.
 * 3. Nothing here is required. Every fact the map shows is also on the page as
 *    text, and if the import fails, WebGL is unavailable, or the reader simply
 *    prefers the list, the page is complete without it.
 */

export interface MapPanelProps {
  readonly stops: readonly JourneyStop[];
  readonly geometry: Geometry | null;
  readonly boardingStopId: string | null;
  readonly heightPx?: number;
  readonly reducedMotion: boolean;
}

interface Bounds {
  minLon: number;
  minLat: number;
  maxLon: number;
  maxLat: number;
}

function boundsOf(points: readonly (readonly [number, number])[]): Bounds | null {
  if (points.length === 0) return null;
  let [minLon, minLat] = points[0] as [number, number];
  let maxLon = minLon;
  let maxLat = minLat;
  for (const [lon, lat] of points) {
    minLon = Math.min(minLon, lon);
    maxLon = Math.max(maxLon, lon);
    minLat = Math.min(minLat, lat);
    maxLat = Math.max(maxLat, lat);
  }
  return { minLon, minLat, maxLon, maxLat };
}

export function MapPanel({ stops, geometry, boardingStopId, heightPx = 360, reducedMotion }: MapPanelProps) {
  const { t, name } = useI18n();
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<{ zoomIn: () => void; zoomOut: () => void; fit: () => void; remove: () => void } | null>(null);
  const [failed, setFailed] = useState<Error | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let cancelled = false;
    let cleanup: (() => void) | null = null;

    const boot = async () => {
      const node = containerRef.current;
      if (!node) return;
      try {
        const [{ Map: MapLibreMap, Marker }] = await Promise.all([
          import('maplibre-gl'),
          import('maplibre-gl/dist/maplibre-gl.css'),
        ]);
        if (cancelled) return;

        const stopPoints = stops.map((s) => [s.longitude, s.latitude] as [number, number]);
        const linePoints = geometry ? geometry.coordinates.map((c) => [c[0], c[1]] as [number, number]) : stopPoints;
        const bounds = boundsOf(linePoints.length > 1 ? linePoints : stopPoints);
        if (!bounds) return;

        const map = new MapLibreMap({
          container: node,
          // A style with no sources of its own and no glyphs or sprite: nothing
          // is fetched from anywhere.
          style: {
            version: 8,
            glyphs: undefined,
            sources: {},
            layers: [
              {
                id: 'od-background',
                type: 'background',
                paint: { 'background-color': getComputedStyle(node).getPropertyValue('--od-map-water').trim() || '#e3eeec' },
              },
            ],
          },
          bounds: [
            [bounds.minLon, bounds.minLat],
            [bounds.maxLon, bounds.maxLat],
          ],
          fitBoundsOptions: { padding: 48, animate: false },
          attributionControl: false,
          // Keyboard navigation is on by default and stays on: the canvas is
          // focusable and arrow keys pan, +/- zoom.
          keyboard: true,
          dragRotate: false,
          pitchWithRotate: false,
          touchZoomRotate: true,
          // Honour the reader's motion preference for every camera movement.
          fadeDuration: reducedMotion ? 0 : 300,
        });

        map.on('error', () => {
          // A style or rendering error must not take the page down; the list is
          // still authoritative.
        });

        map.on('load', () => {
          if (cancelled) return;
          if (linePoints.length > 1) {
            map.addSource('od-route', {
              type: 'geojson',
              data: { type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: linePoints } },
            });
            const unverified = geometry === null || geometry.confidence === 'unverified' || geometry.confidence === 'ordered_stops_only';
            map.addLayer({
              id: 'od-route-casing',
              type: 'line',
              source: 'od-route',
              paint: {
                'line-color': '#ffffff',
                'line-width': 10,
                'line-opacity': 0.9,
              },
              layout: { 'line-cap': 'round', 'line-join': 'round' },
            });
            map.addLayer({
              id: 'od-route-line',
              type: 'line',
              source: 'od-route',
              paint: {
                'line-color': BRAND_COLORS.deepTealBlue,
                'line-width': 6,
                // A line that has not been surveyed is drawn dashed, and the
                // badge beside the map says so. A solid line would be a claim
                // the data does not support.
                ...(unverified ? { 'line-dasharray': [1.5, 1.2] } : {}),
              },
              layout: { 'line-cap': 'round', 'line-join': 'round' },
            });
          }

          for (const stop of stops) {
            const element = globalThis.document.createElement('div');
            element.className =
              stop.stopId === boardingStopId ? 'od-map-marker od-map-marker--boarding' : 'od-map-marker';
            // The marker is decorative: the stop list is the accessible source
            // of truth, so it is hidden from assistive technology rather than
            // duplicating every name twice.
            element.setAttribute('aria-hidden', 'true');
            element.title = name(stop.name);
            new Marker({ element }).setLngLat([stop.longitude, stop.latitude]).addTo(map);
          }
          setReady(true);
        });

        mapRef.current = {
          zoomIn: () => map.zoomIn({ animate: !reducedMotion }),
          zoomOut: () => map.zoomOut({ animate: !reducedMotion }),
          fit: () =>
            map.fitBounds(
              [
                [bounds.minLon, bounds.minLat],
                [bounds.maxLon, bounds.maxLat],
              ],
              { padding: 48, animate: !reducedMotion },
            ),
          remove: () => map.remove(),
        };
        cleanup = () => map.remove();
      } catch (cause) {
        if (!cancelled) setFailed(cause instanceof Error ? cause : new Error(String(cause)));
      }
    };

    void boot();
    return () => {
      cancelled = true;
      cleanup?.();
      mapRef.current = null;
    };
  }, [stops, geometry, boardingStopId, reducedMotion, name]);

  if (failed) {
    return (
      <StateBlock
        kind="unavailable"
        headingLevel={3}
        title={t('journey.mapUnavailable')}
        body={<p>{t('journey.mapUnavailableBody')}</p>}
        announce={false}
      />
    );
  }

  return (
    <div className="od-map">
      <div className="od-map__canvas" ref={containerRef} style={{ blockSize: `${heightPx}px` }} data-ready={ready ? '' : undefined} />
      <div className="od-map__controls" role="group" aria-label={t('a11y.mapControls')}>
        <Button tone="quiet" onClick={() => mapRef.current?.zoomIn()} aria-label={t('a11y.zoomIn')}>
          <span aria-hidden="true">+</span>
        </Button>
        <Button tone="quiet" onClick={() => mapRef.current?.zoomOut()} aria-label={t('a11y.zoomOut')}>
          <span aria-hidden="true">{'−'}</span>
        </Button>
        <Button tone="quiet" onClick={() => mapRef.current?.fit()}>
          {t('a11y.recentre')}
        </Button>
      </div>
      <div className="od-map__notes">
        <GeometryBadge confidence={geometry?.confidence ?? 'unverified'} />
        <p className="od-map__note">{t('journey.mapNoTiles')}</p>
        {geometry ? <p className="od-map__attribution">{geometry.attribution}</p> : null}
      </div>
    </div>
  );
}
