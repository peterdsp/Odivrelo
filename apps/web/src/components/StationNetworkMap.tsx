import { useEffect, useMemo, useRef, useState } from 'react';
import type { Place } from '../data/contract';
import { appConfig } from '../config';
import { useI18n } from '../i18n/I18nProvider';
import { BRAND_COLORS } from '../styles/brandColors.generated';
import { Button, Card } from './primitives';
import { StateBlock } from './states';

interface Props {
  readonly places: readonly Place[];
}

interface Point {
  readonly longitude: number;
  readonly latitude: number;
}

function boundsOf(points: readonly Point[]) {
  if (points.length === 0) return null;
  const first = points[0];
  if (!first) return null;
  return points.reduce(
    (bounds, point) => ({
      minLon: Math.min(bounds.minLon, point.longitude),
      minLat: Math.min(bounds.minLat, point.latitude),
      maxLon: Math.max(bounds.maxLon, point.longitude),
      maxLat: Math.max(bounds.maxLat, point.latitude),
    }),
    { minLon: first.longitude, minLat: first.latitude, maxLon: first.longitude, maxLat: first.latitude },
  );
}

/** A real-station overview with an explicitly labelled visual simulation. */
export function StationNetworkMap({ places }: Props) {
  const { t, name } = useI18n();
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<{ zoomIn: () => void; zoomOut: () => void; fit: () => void; remove: () => void } | null>(null);
  const [failed, setFailed] = useState(false);
  const [tilesFailed, setTilesFailed] = useState(false);

  const stations = useMemo(
    () =>
      places
        .filter((place) => place.kind === 'stop_place' && Number.isFinite(place.latitude) && Number.isFinite(place.longitude))
        .slice(0, 24),
    [places],
  );

  useEffect(() => {
    let cancelled = false;
    let cleanup: (() => void) | null = null;

    const boot = async () => {
      const node = containerRef.current;
      if (!node || stations.length < 2) return;
      try {
        const [{ Map: MapLibreMap, Marker }] = await Promise.all([
          import('maplibre-gl'),
          import('maplibre-gl/dist/maplibre-gl.css'),
        ]);
        if (cancelled) return;
        const points = stations.map((station) => ({ longitude: station.longitude, latitude: station.latitude }));
        const bounds = boundsOf(points);
        if (!bounds) return;
        const linePoints = points.map((point) => [point.longitude, point.latitude] as [number, number]);
        const styleUrl = appConfig.mapStyleUrl;
        const tilelessStyle = {
          version: 8 as const,
          sources: {},
          layers: [{ id: 'od-network-background', type: 'background' as const, paint: { 'background-color': '#dbe9e6' } }],
        };
        const map = new MapLibreMap({
          container: node,
          style: styleUrl || tilelessStyle,
          bounds: [[bounds.minLon, bounds.minLat], [bounds.maxLon, bounds.maxLat]],
          fitBoundsOptions: { padding: 48, animate: false },
          attributionControl: styleUrl ? { compact: true } : false,
          dragRotate: false,
          pitchWithRotate: false,
        });
        map.on('error', (event: { error?: { status?: number; message?: string } }) => {
          const message = event.error?.message ?? '';
          if (styleUrl && (event.error?.status != null || /tile|style|sprite|glyph|fetch|load/i.test(message))) setTilesFailed(true);
        });
        map.on('load', () => {
          if (cancelled) return;
          map.addSource('od-network-line', {
            type: 'geojson',
            data: { type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: linePoints } },
          });
          map.addLayer({
            id: 'od-network-line',
            type: 'line',
            source: 'od-network-line',
            paint: { 'line-color': BRAND_COLORS.deepTealBlue, 'line-width': 5, 'line-opacity': 0.82 },
            layout: { 'line-cap': 'round', 'line-join': 'round' },
          });
          for (const station of stations) {
            const element = document.createElement('div');
            element.className = 'od-network-marker';
            element.setAttribute('aria-hidden', 'true');
            element.title = name(station.name);
            new Marker({ element }).setLngLat([station.longitude, station.latitude]).addTo(map);
          }
          // Deliberately synthetic: the moving marker is a visual demonstration,
          // never presented as a real-time vehicle position.
          const bus = document.createElement('div');
          bus.className = 'od-network-bus';
          bus.textContent = '🚌';
          bus.setAttribute('aria-hidden', 'true');
          const busMarker = new Marker({ element: bus }).setLngLat(linePoints[0]!).addTo(map);
          let index = 0;
          const timer = window.setInterval(() => {
            index = (index + 1) % linePoints.length;
            busMarker.setLngLat(linePoints[index]!);
          }, 1800);
          cleanup = () => {
            window.clearInterval(timer);
            map.remove();
          };
        });
        mapRef.current = {
          zoomIn: () => map.zoomIn(),
          zoomOut: () => map.zoomOut(),
          fit: () => map.fitBounds([[bounds.minLon, bounds.minLat], [bounds.maxLon, bounds.maxLat]], { padding: 48 }),
          remove: () => map.remove(),
        };
      } catch {
        if (!cancelled) setFailed(true);
      }
    };
    void boot();
    return () => {
      cancelled = true;
      cleanup?.();
      mapRef.current = null;
    };
  }, [name, stations]);

  if (failed || stations.length < 2) {
    return <StateBlock kind="unavailable" headingLevel={3} title={t('stations.mapUnavailable')} body={<p>{t('stations.mapNoTiles')}</p>} announce={false} />;
  }

  return (
    <Card className="od-network-map" tone="muted">
      <div className="od-network-map__header">
        <div>
          <h2 className="od-section__title">{t('stations.networkTitle')}</h2>
          <p className="od-muted">{t('stations.networkBody')}</p>
        </div>
        <span className="od-network-map__badge">{t('stations.simulated')}</span>
      </div>
      <div className="od-network-map__canvas" ref={containerRef} />
      <div className="od-map__controls" role="group" aria-label={t('a11y.mapControls')}>
        <Button tone="quiet" onClick={() => mapRef.current?.zoomIn()} aria-label={t('a11y.zoomIn')}>+</Button>
        <Button tone="quiet" onClick={() => mapRef.current?.zoomOut()} aria-label={t('a11y.zoomOut')}>−</Button>
        <Button tone="quiet" onClick={() => mapRef.current?.fit()}>{t('a11y.recentre')}</Button>
      </div>
      <p className="od-network-map__legend"><span className="od-network-map__legend-dot" /> {t('stations.realStations')} · <span aria-hidden="true">🚌</span> {t('stations.simulated')}</p>
      {(!appConfig.mapStyleUrl || tilesFailed) ? <p className="od-map__note">{t('stations.mapNoTiles')}</p> : null}
    </Card>
  );
}
