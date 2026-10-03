package dev.peterdsp.odivrelo.ui.journey

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.peterdsp.odivrelo.core.model.Geometry
import dev.peterdsp.odivrelo.core.model.GeometryConfidence
import dev.peterdsp.odivrelo.core.model.JourneyStop
import dev.peterdsp.odivrelo.core.model.SegmentRole
import dev.peterdsp.odivrelo.theme.OdivreloPalette
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** The OpenFreeMap liberty style, the same provider the Web client uses. */
const val OPENFREEMAP_LIBERTY_STYLE = "https://tiles.openfreemap.org/styles/liberty"

/**
 * An embedded MapLibre map of one journey: the OpenFreeMap basemap, the route
 * line, and the stops resolved by their own coordinates.
 *
 * Stops are plotted at their own latitude and longitude, never at a route
 * geometry vertex. The boarded and alighted stops of the selected segment are
 * emphasised. MapLibre shows the OpenFreeMap and OpenStreetMap attribution
 * itself. The caller keeps the stop list as the complete, accessible
 * alternative, so this view is an addition, not a requirement.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun CoachMap(
    stops: List<JourneyStop>,
    geometry: Geometry?,
    boardingStopId: String?,
    alightStopId: String?,
    modifier: Modifier = Modifier,
    styleUrl: String = OPENFREEMAP_LIBERTY_STYLE,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val located = remember(stops) {
        stops.filter { it.latitude != null && it.longitude != null }
    }

    val mapView = remember {
        MapLibre.getInstance(context)
        val options = MapLibreMapOptions.createFromAttributes(context)
            // A texture-backed surface composes correctly inside a scrolling
            // Compose list, where a plain SurfaceView would fight the z-order.
            .textureMode(true)
            .attributionEnabled(true)
            .logoEnabled(true)
        MapView(context, options).apply { onCreate(null) }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(modifier = modifier, factory = { mapView }) { view ->
        view.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                addRoute(style, geometry, located)
                addStops(style, located, boardingStopId, alightStopId)
                frameCamera(map, geometry, located)
            }
        }
    }
}

private fun routePoints(geometry: Geometry?, located: List<JourneyStop>): List<Point> {
    val fromGeometry = geometry?.coordinates
        ?.filter { it.size == 2 }
        ?.map { Point.fromLngLat(it[0], it[1]) }
        .orEmpty()
    if (fromGeometry.size >= 2) return fromGeometry
    // No drawable geometry: join the located stops in order so the map still
    // shows the shape of the journey.
    return located.mapNotNull { stop ->
        val lat = stop.latitude
        val lon = stop.longitude
        if (lat != null && lon != null) Point.fromLngLat(lon, lat) else null
    }
}

private fun addRoute(style: Style, geometry: Geometry?, located: List<JourneyStop>) {
    val points = routePoints(geometry, located)
    if (points.size < 2) return
    style.addSource(
        org.maplibre.android.style.sources.GeoJsonSource(
            "od-route",
            Feature.fromGeometry(LineString.fromLngLats(points)),
        ),
    )
    val reviewed = geometry?.confidence == GeometryConfidence.REVIEWED
    val line = LineLayer("od-route-line", "od-route").withProperties(
        PropertyFactory.lineColor(colorInt(OdivreloPalette.Brand.deepTealBlue)),
        PropertyFactory.lineWidth(if (reviewed) 5f else 4f),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    )
    if (!reviewed) {
        // A line that has not been surveyed is dashed, never a solid confident line.
        line.setProperties(PropertyFactory.lineDasharray(arrayOf(1.6f, 1.4f)))
    }
    style.addLayer(line)
}

private fun addStops(
    style: Style,
    located: List<JourneyStop>,
    boardingStopId: String?,
    alightStopId: String?,
) {
    if (located.isEmpty()) return

    fun pointFeatures(filter: (JourneyStop) -> Boolean): List<Feature> =
        located.mapNotNull { stop ->
            val lat = stop.latitude
            val lon = stop.longitude
            if (lat == null || lon == null || !filter(stop)) return@mapNotNull null
            Feature.fromGeometry(Point.fromLngLat(lon, lat))
        }

    fun isEndpoint(stop: JourneyStop): Boolean =
        stop.stopId == boardingStopId || stop.stopId == alightStopId ||
            stop.segmentRole == SegmentRole.BOARD || stop.segmentRole == SegmentRole.ALIGHT

    // Every stop as a plain marker.
    style.addSource(
        org.maplibre.android.style.sources.GeoJsonSource(
            "od-stops",
            FeatureCollection.fromFeatures(pointFeatures { true }),
        ),
    )
    style.addLayer(
        CircleLayer("od-stops-circles", "od-stops").withProperties(
            PropertyFactory.circleRadius(5f),
            PropertyFactory.circleColor(colorInt(OdivreloPalette.Brand.lightBackground)),
            PropertyFactory.circleStrokeColor(colorInt(OdivreloPalette.Brand.deepTealBlue)),
            PropertyFactory.circleStrokeWidth(2f),
        ),
    )

    // The boarded and alighted stops, emphasised on top.
    val endpoints = pointFeatures(::isEndpoint)
    if (endpoints.isNotEmpty()) {
        style.addSource(
            org.maplibre.android.style.sources.GeoJsonSource(
                "od-endpoints",
                FeatureCollection.fromFeatures(endpoints),
            ),
        )
        style.addLayer(
            CircleLayer("od-endpoints-circles", "od-endpoints").withProperties(
                PropertyFactory.circleRadius(7.5f),
                PropertyFactory.circleColor(colorInt(OdivreloPalette.Brand.warmOrange)),
                PropertyFactory.circleStrokeColor(colorInt(OdivreloPalette.Brand.deepTealBlue)),
                PropertyFactory.circleStrokeWidth(2.5f),
            ),
        )
    }
}

private fun frameCamera(map: MapLibreMap, geometry: Geometry?, located: List<JourneyStop>) {
    val coordinates = mutableListOf<LatLng>()
    located.forEach { stop ->
        val lat = stop.latitude
        val lon = stop.longitude
        if (lat != null && lon != null) coordinates.add(LatLng(lat, lon))
    }
    if (coordinates.size < 2) {
        geometry?.coordinates?.filter { it.size == 2 }?.forEach {
            coordinates.add(LatLng(it[1], it[0]))
        }
    }
    when {
        coordinates.isEmpty() -> Unit
        coordinates.size == 1 ->
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(coordinates.first(), 11.0))
        else -> {
            val bounds = LatLngBounds.Builder().includes(coordinates).build()
            runCatching { map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 64)) }
        }
    }
}

/** 0xAARRGGBB int from a Compose color, for the MapLibre style API. */
private fun colorInt(color: Color): Int =
    android.graphics.Color.argb(
        (color.alpha * 255f).toInt(),
        (color.red * 255f).toInt(),
        (color.green * 255f).toInt(),
        (color.blue * 255f).toInt(),
    )
