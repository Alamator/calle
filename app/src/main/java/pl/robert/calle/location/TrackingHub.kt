package pl.robert.calle.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.robert.calle.graph.LatLon
import pl.robert.calle.graph.SnapResult

object TrackingHub {
    private val _tracking = MutableStateFlow(false)
    val tracking: StateFlow<Boolean> = _tracking.asStateFlow()

    private val _raw = MutableStateFlow<LatLon?>(null)
    val raw: StateFlow<LatLon?> = _raw.asStateFlow()

    private val _snapped = MutableStateFlow<SnapResult?>(null)
    val snapped: StateFlow<SnapResult?> = _snapped.asStateFlow()

    var lastWayId: Long? = null
        private set

    fun setTracking(active: Boolean) {
        _tracking.value = active
        if (!active) {
            _raw.value = null
            _snapped.value = null
            lastWayId = null
        }
    }

    fun publish(raw: LatLon, snap: SnapResult?) {
        _raw.value = raw
        _snapped.value = snap
        lastWayId = snap?.hit?.way?.id
    }
}
