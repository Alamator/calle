package pl.robert.calle.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import pl.robert.calle.R
import pl.robert.calle.graph.LatLon
import pl.robert.calle.graph.StreetWay
import pl.robert.calle.graph.VeniceGraph
import pl.robert.calle.ui.theme.Amber
import pl.robert.calle.ui.theme.Ink
import pl.robert.calle.ui.theme.Panel

private const val SOURCE_STREETS = "streets"
private const val SOURCE_CANALS = "canals"
private const val SOURCE_DEBUG = "debug"
private const val LAYER_CANALS = "canals-line"
private const val LAYER_UNWALKED = "streets-unwalked"
private const val LAYER_WALKED = "streets-walked"
private const val LAYER_DEBUG_LINE = "debug-line"
private const val LAYER_DEBUG_RAW = "debug-raw"
private const val LAYER_DEBUG_SNAP = "debug-snap"

private val VENICE_CENTER = LatLng(45.4372, 12.3350)

private const val DARK_STYLE = """
{
  "version": 8,
  "name": "Calle Dark",
  "sources": {},
  "layers": [
    {
      "id": "background",
      "type": "background",
      "paint": { "background-color": "#0B0F14" }
    }
  ]
}
"""

@Composable
fun MapScreen(viewModel: CalleViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val graph by viewModel.graph.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        val ok = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) viewModel.startTracking(context)
    }

    LaunchedEffect(state.toast) {
        val message = state.toast ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.consumeToast()
    }

    LaunchedEffect(graph, styleReady, mapRef) {
        val g = graph
        val map = mapRef
        if (g != null && styleReady && map != null) {
            map.getStyle { style ->
                (style.getSource(SOURCE_CANALS) as? GeoJsonSource)
                    ?.setGeoJson(canalsCollection(g))
                (style.getSource(SOURCE_STREETS) as? GeoJsonSource)
                    ?.setGeoJson(streetsCollection(g.streets, emptySet()))
            }
        }
    }

    LaunchedEffect(state.walkedIds, styleReady, mapRef, graph) {
        val g = graph ?: return@LaunchedEffect
        val map = mapRef ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        map.getStyle { style ->
            (style.getSource(SOURCE_STREETS) as? GeoJsonSource)
                ?.setGeoJson(streetsCollection(g.streets, state.walkedIds))
        }
    }

    LaunchedEffect(state.raw, state.snapped, state.debug, styleReady, mapRef) {
        val map = mapRef ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        map.getStyle { style ->
            val source = style.getSource(SOURCE_DEBUG) as? GeoJsonSource ?: return@getStyle
            source.setGeoJson(debugCollection(state.raw, state.snapped, state.debug))
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Ink)) {
        val mapView = rememberMapView()
        AndroidView(
            factory = { view ->
                view.getMapAsync { map ->
                    map.uiSettings.isAttributionEnabled = false
                    map.uiSettings.isLogoEnabled = false
                    map.uiSettings.isCompassEnabled = true
                    map.cameraPosition = CameraPosition.Builder()
                        .target(VENICE_CENTER)
                        .zoom(15.2)
                        .build()
                    map.setStyle(Style.Builder().fromJson(DARK_STYLE)) { style ->
                        installLayers(style)
                        map.addOnMapClickListener { latLng ->
                            viewModel.onMapClick(latLng.latitude, latLng.longitude)
                            true
                        }
                        mapRef = map
                        styleReady = true
                    }
                }
                view
            },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 36.dp, start = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Panel, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Amber,
                    )
                    val percent = if (state.streetCount == 0) 0.0
                    else 100.0 * state.walkedCount / state.streetCount
                    Text(
                        text = stringResource(
                            R.string.walked_stats,
                            state.walkedCount,
                            state.streetCount,
                            percent,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (state.snappedName != null && state.tracking) {
                        Text(
                            text = state.snappedName ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Amber,
                        )
                    }
                }
                FilterChip(
                    selected = state.debug,
                    onClick = { viewModel.setDebug(!state.debug) },
                    label = { Text(stringResource(R.string.debug)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Amber,
                        selectedLabelColor = Ink,
                    ),
                )
            }
            if (state.debug) {
                Text(
                    text = debugCaption(state),
                    modifier = Modifier
                        .background(Panel, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.manual_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            )
            Text(
                text = stringResource(R.string.osm_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            )
            Button(
                onClick = {
                    if (state.tracking) {
                        viewModel.stopTracking(context)
                    } else {
                        val permissions = buildList {
                            add(Manifest.permission.ACCESS_FINE_LOCATION)
                            add(Manifest.permission.ACCESS_COARSE_LOCATION)
                            if (Build.VERSION.SDK_INT >= 33) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }.toTypedArray()
                        permissionLauncher.launch(permissions)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.tracking) MaterialTheme.colorScheme.error else Amber,
                    contentColor = if (state.tracking) MaterialTheme.colorScheme.onError else Ink,
                ),
            ) {
                Text(
                    text = stringResource(if (state.tracking) R.string.stop_tracking else R.string.start_tracking),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 108.dp),
        )
    }
}

@Composable
private fun rememberMapView(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply { onCreate(Bundle()) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                Lifecycle.Event.ON_CREATE,
                Lifecycle.Event.ON_ANY,
                -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }
    return mapView
}

private fun installLayers(style: Style) {
    style.addSource(GeoJsonSource(SOURCE_CANALS, FeatureCollection.fromFeatures(emptyArray())))
    style.addSource(GeoJsonSource(SOURCE_STREETS, FeatureCollection.fromFeatures(emptyArray())))
    style.addSource(GeoJsonSource(SOURCE_DEBUG, FeatureCollection.fromFeatures(emptyArray())))

    val zoomWidth = Expression.interpolate(
        Expression.exponential(1.5f),
        Expression.zoom(),
        Expression.literal(12),
        Expression.literal(0.7f),
        Expression.literal(16),
        Expression.literal(2.1f),
        Expression.literal(19),
        Expression.literal(5.2f),
    )

    style.addLayer(
        LineLayer(LAYER_CANALS, SOURCE_CANALS).withProperties(
            PropertyFactory.lineColor("#15202B"),
            PropertyFactory.lineWidth(3.4f),
            PropertyFactory.lineOpacity(0.95f),
            PropertyFactory.lineCap("round"),
            PropertyFactory.lineJoin("round"),
        ),
    )
    style.addLayer(
        LineLayer(LAYER_UNWALKED, SOURCE_STREETS)
            .withFilter(Expression.eq(Expression.get("walked"), Expression.literal(false)))
            .withProperties(
                PropertyFactory.lineColor("#5A6470"),
                PropertyFactory.lineWidth(zoomWidth),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round"),
            ),
    )
    style.addLayer(
        LineLayer(LAYER_WALKED, SOURCE_STREETS)
            .withFilter(Expression.eq(Expression.get("walked"), Expression.literal(true)))
            .withProperties(
                PropertyFactory.lineColor("#E6A23C"),
                PropertyFactory.lineWidth(
                    Expression.interpolate(
                        Expression.exponential(1.5f),
                        Expression.zoom(),
                        Expression.literal(12),
                        Expression.literal(1.1f),
                        Expression.literal(16),
                        Expression.literal(2.8f),
                        Expression.literal(19),
                        Expression.literal(6.4f),
                    ),
                ),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round"),
            ),
    )
    style.addLayer(
        LineLayer(LAYER_DEBUG_LINE, SOURCE_DEBUG)
            .withFilter(Expression.eq(Expression.geometryType(), Expression.literal("LineString")))
            .withProperties(
                PropertyFactory.lineColor("#E25B5B"),
                PropertyFactory.lineWidth(1.4f),
                PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
            ),
    )
    style.addLayer(
        CircleLayer(LAYER_DEBUG_RAW, SOURCE_DEBUG)
            .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("raw")))
            .withProperties(
                PropertyFactory.circleColor("#E25B5B"),
                PropertyFactory.circleRadius(5.5f),
                PropertyFactory.circleStrokeColor("#0B0F14"),
                PropertyFactory.circleStrokeWidth(1.2f),
            ),
    )
    style.addLayer(
        CircleLayer(LAYER_DEBUG_SNAP, SOURCE_DEBUG)
            .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("snap")))
            .withProperties(
                PropertyFactory.circleColor("#E6A23C"),
                PropertyFactory.circleRadius(6.2f),
                PropertyFactory.circleStrokeColor("#0B0F14"),
                PropertyFactory.circleStrokeWidth(1.2f),
            ),
    )
}

private fun streetsCollection(streets: List<StreetWay>, walked: Set<Long>): FeatureCollection {
    val features = streets.map { way ->
        val line = LineString.fromLngLats(way.points.map { Point.fromLngLat(it.lon, it.lat) })
        Feature.fromGeometry(line).apply {
            addNumberProperty("id", way.id.toDouble())
            addBooleanProperty("walked", way.id in walked)
            addBooleanProperty("bridge", way.bridge)
            way.name?.let { addStringProperty("name", it) }
        }
    }
    return FeatureCollection.fromFeatures(features)
}

private fun canalsCollection(graph: VeniceGraph): FeatureCollection {
    val features = graph.canals.map { canal ->
        Feature.fromGeometry(
            LineString.fromLngLats(canal.points.map { Point.fromLngLat(it.lon, it.lat) }),
        )
    }
    return FeatureCollection.fromFeatures(features)
}

private fun debugCollection(raw: LatLon?, snapped: LatLon?, enabled: Boolean): FeatureCollection {
    if (!enabled || raw == null) {
        return FeatureCollection.fromFeatures(emptyArray())
    }
    val features = mutableListOf<Feature>()
    val rawPoint = Point.fromLngLat(raw.lon, raw.lat)
    features += Feature.fromGeometry(rawPoint).apply { addStringProperty("kind", "raw") }
    if (snapped != null) {
        val snapPoint = Point.fromLngLat(snapped.lon, snapped.lat)
        features += Feature.fromGeometry(snapPoint).apply { addStringProperty("kind", "snap") }
        features += Feature.fromGeometry(LineString.fromLngLats(listOf(rawPoint, snapPoint)))
    }
    return FeatureCollection.fromFeatures(features)
}

private fun debugCaption(state: MapUiState): String {
    val raw = state.raw
    val snap = state.snapped
    return buildString {
        append("GPS surowy: ")
        append(if (raw == null) "—" else "%.5f, %.5f".format(raw.lat, raw.lon))
        append("  →  przyciągnięty: ")
        append(if (snap == null) "—" else "%.5f, %.5f".format(snap.lat, snap.lon))
        if (state.heldByHysteresis) append("  (histereza)")
    }
}
