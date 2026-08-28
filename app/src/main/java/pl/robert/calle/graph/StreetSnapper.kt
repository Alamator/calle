package pl.robert.calle.graph

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class SnapHit(
    val way: StreetWay,
    val point: LatLon,
    val distanceM: Double,
    val segmentIndex: Int,
)

data class SnapResult(
    val hit: SnapHit,
    val raw: LatLon,
    val heldByHysteresis: Boolean,
)

class StreetSnapper(
    private val graph: VeniceGraph,
    private val maxSnapM: Double = 28.0,
    private val keepWayM: Double = 20.0,
    private val switchMarginM: Double = 8.0,
) {
    fun snap(raw: LatLon, lastWayId: Long?): SnapResult? {
        val candidates = collectCandidates(raw, lastWayId) 
        val last = lastWayId?.let { id -> candidates.firstOrNull { it.way.id == id } }
        val best = candidates.minByOrNull { it.distanceM } ?: return null

        if (last != null && last.distanceM <= keepWayM) {
            val shouldSwitch =
                best.way.id != last.way.id &&
                    best.distanceM + switchMarginM < last.distanceM
            val chosen = if (shouldSwitch) best else last
            return SnapResult(
                hit = chosen,
                raw = raw,
                heldByHysteresis = !shouldSwitch && chosen.way.id != best.way.id,
            )
        }

        return if (best.distanceM <= maxSnapM) {
            SnapResult(hit = best, raw = raw, heldByHysteresis = false)
        } else {
            null
        }
    }

    fun nearestWay(raw: LatLon, maxM: Double): SnapHit? {
        return collectCandidates(raw, lastWayId = null, maxDistanceM = maxM)
            .minByOrNull { it.distanceM }
    }

    private fun collectCandidates(
        raw: LatLon,
        lastWayId: Long?,
        maxDistanceM: Double = maxSnapM * 1.6,
    ): List<SnapHit> {
        val hits = ArrayList<SnapHit>()
        for (index in graph.streetIndex.nearby(raw.lat, raw.lon, radiusCells = 2)) {
            val way = graph.streets[index]
            val hit = closestOnWay(way, raw) ?: continue
            if (hit.distanceM > maxDistanceM) continue
            val keepLast = lastWayId != null && way.id == lastWayId && hit.distanceM <= keepWayM
            if (hit.distanceM > maxSnapM && !keepLast) continue
            if (!way.bridge && crossesCanal(raw, hit.point)) continue
            hits += hit
        }
        return hits
    }

    private fun closestOnWay(way: StreetWay, raw: LatLon): SnapHit? {
        var bestDist = Double.POSITIVE_INFINITY
        var bestPoint: LatLon? = null
        var bestSeg = 0
        val pts = way.points
        for (i in 0 until pts.lastIndex) {
            val projected = projectOnSegment(raw, pts[i], pts[i + 1])
            val d = metres(raw, projected)
            if (d < bestDist) {
                bestDist = d
                bestPoint = projected
                bestSeg = i
            }
        }
        val point = bestPoint ?: return null
        return SnapHit(way = way, point = point, distanceM = bestDist, segmentIndex = bestSeg)
    }

    private fun crossesCanal(from: LatLon, to: LatLon): Boolean {
        if (metres(from, to) < 0.4) return false
        for (index in graph.canalIndex.nearby(from.lat, from.lon, radiusCells = 2)) {
            val canal = graph.canals[index]
            val pts = canal.points
            for (i in 0 until pts.lastIndex) {
                if (segmentsIntersect(from, to, pts[i], pts[i + 1])) return true
            }
        }
        // Also probe cells around the snap point — canals may sit in a neighbour cell.
        for (index in graph.canalIndex.nearby(to.lat, to.lon, radiusCells = 1)) {
            val canal = graph.canals[index]
            val pts = canal.points
            for (i in 0 until pts.lastIndex) {
                if (segmentsIntersect(from, to, pts[i], pts[i + 1])) return true
            }
        }
        return false
    }

    companion object {
        fun metres(a: LatLon, b: LatLon): Double {
            val meanLat = Math.toRadians((a.lat + b.lat) * 0.5)
            val dx = (b.lon - a.lon) * METRES_PER_DEG_LON * cos(meanLat)
            val dy = (b.lat - a.lat) * METRES_PER_DEG_LAT
            return hypot(dx, dy)
        }

        fun projectOnSegment(p: LatLon, a: LatLon, b: LatLon): LatLon {
            val meanLat = Math.toRadians((a.lat + b.lat) * 0.5)
            val mx = METRES_PER_DEG_LON * cos(meanLat)
            val my = METRES_PER_DEG_LAT
            val ax = a.lon * mx
            val ay = a.lat * my
            val bx = b.lon * mx
            val by = b.lat * my
            val px = p.lon * mx
            val py = p.lat * my
            val dx = bx - ax
            val dy = by - ay
            val denom = dx * dx + dy * dy
            if (denom < 1e-9) return a
            val t = ((px - ax) * dx + (py - ay) * dy) / denom
            val clamped = min(1.0, max(0.0, t))
            return LatLon(lat = a.lat + (b.lat - a.lat) * clamped, lon = a.lon + (b.lon - a.lon) * clamped)
        }

        fun segmentsIntersect(a1: LatLon, a2: LatLon, b1: LatLon, b2: LatLon): Boolean {
            val o1 = orientation(a1, a2, b1)
            val o2 = orientation(a1, a2, b2)
            val o3 = orientation(b1, b2, a1)
            val o4 = orientation(b1, b2, a2)
            if (o1 != o2 && o3 != o4) {
                // Ignore near-end touches so GPS sitting on a fondamenta doesn't count as crossing.
                if (metres(a1, b1) < 1.2 || metres(a1, b2) < 1.2 || metres(a2, b1) < 1.2 || metres(a2, b2) < 1.2) {
                    return false
                }
                return true
            }
            return false
        }

        private fun orientation(a: LatLon, b: LatLon, c: LatLon): Int {
            val v = (b.lon - a.lon) * (c.lat - a.lat) - (b.lat - a.lat) * (c.lon - a.lon)
            return when {
                abs(v) < 1e-15 -> 0
                v > 0 -> 1
                else -> 2
            }
        }

        private const val METRES_PER_DEG_LAT = 110_540.0
        private const val METRES_PER_DEG_LON = 111_320.0
    }
}
