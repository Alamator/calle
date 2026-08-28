package pl.robert.calle.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.robert.calle.CalleApp
import pl.robert.calle.db.WalkedWayDao
import pl.robert.calle.db.WalkedWayEntity
import pl.robert.calle.graph.GraphHolder
import pl.robert.calle.graph.LatLon
import pl.robert.calle.graph.StreetSnapper
import pl.robert.calle.graph.VeniceGraph
import pl.robert.calle.location.TrackingHub
import pl.robert.calle.location.TrackingService

data class MapUiState(
    val graphReady: Boolean = false,
    val streetCount: Int = 0,
    val walkedCount: Int = 0,
    val walkedIds: Set<Long> = emptySet(),
    val tracking: Boolean = false,
    val debug: Boolean = false,
    val raw: LatLon? = null,
    val snapped: LatLon? = null,
    val snappedName: String? = null,
    val heldByHysteresis: Boolean = false,
    val toast: String? = null,
)

class CalleViewModel(
    private val app: CalleApp,
    private val dao: WalkedWayDao,
) : ViewModel() {
    private val _graph = MutableStateFlow<VeniceGraph?>(null)
    val graph: StateFlow<VeniceGraph?> = _graph.asStateFlow()

    private val _debug = MutableStateFlow(false)
    private val _toast = MutableStateFlow<String?>(null)
    private lateinit var snapper: StreetSnapper

    val uiState: StateFlow<MapUiState> = combine(
        combine(_graph, dao.observeIds(), TrackingHub.tracking, _debug) { graph, walkedList, tracking, debug ->
            Quad(graph, walkedList.toSet(), tracking, debug)
        },
        combine(TrackingHub.raw, TrackingHub.snapped, _toast) { raw, snap, toast ->
            Triple(raw, snap, toast)
        },
    ) { base, loc ->
        val (graph, walked, tracking, debug) = base
        val (raw, snap, toast) = loc
        MapUiState(
            graphReady = graph != null,
            streetCount = graph?.streets?.size ?: 0,
            walkedCount = walked.size,
            walkedIds = walked,
            tracking = tracking,
            debug = debug,
            raw = raw,
            snapped = snap?.hit?.point,
            snappedName = snap?.hit?.way?.name,
            heldByHysteresis = snap?.heldByHysteresis == true,
            toast = toast,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.Default) { GraphHolder.get(app) }
            snapper = StreetSnapper(loaded)
            _graph.value = loaded
        }
    }

    fun setDebug(enabled: Boolean) {
        _debug.value = enabled
    }

    fun consumeToast() {
        _toast.value = null
    }

    fun startTracking(context: Context) {
        TrackingService.start(context.applicationContext)
    }

    fun stopTracking(context: Context) {
        TrackingService.stop(context.applicationContext)
    }

    fun onMapClick(lat: Double, lon: Double) {
        if (!::snapper.isInitialized) return
        val hit = snapper.nearestWay(LatLon(lat, lon), maxM = 24.0) ?: return
        viewModelScope.launch {
            val already = dao.isWalked(hit.way.id)
            if (already) {
                dao.unmark(hit.way.id)
                _toast.value = app.getString(
                    pl.robert.calle.R.string.unmarked,
                    hit.way.name ?: app.getString(pl.robert.calle.R.string.unnamed_street),
                )
            } else {
                dao.mark(
                    WalkedWayEntity(
                        wayId = hit.way.id,
                        markedAtEpochMs = System.currentTimeMillis(),
                        source = "manual",
                    ),
                )
                _toast.value = app.getString(
                    pl.robert.calle.R.string.marked,
                    hit.way.name ?: app.getString(pl.robert.calle.R.string.unnamed_street),
                )
            }
        }
    }

    companion object {
        fun factory(app: CalleApp): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(CalleViewModel::class.java)) {
                        return CalleViewModel(app, app.database.walkedWayDao()) as T
                    }
                    error("Unknown ViewModel ${modelClass.name}")
                }
            }
        }
    }
}
