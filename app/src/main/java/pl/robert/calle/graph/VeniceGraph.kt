package pl.robert.calle.graph

data class LatLon(
    val lat: Double,
    val lon: Double,
)

data class StreetWay(
    val id: Long,
    val name: String?,
    val bridge: Boolean,
    val highway: String?,
    val points: List<LatLon>,
)

data class CanalWay(
    val id: Long,
    val points: List<LatLon>,
)

data class CellKey(val x: Int, val y: Int)

class GridIndex(
    private val cellDeg: Double,
    private val buckets: Map<CellKey, IntArray>,
) {
    fun nearby(lat: Double, lon: Double, radiusCells: Int = 1): Sequence<Int> {
        val cx = floorDiv(lon, cellDeg)
        val cy = floorDiv(lat, cellDeg)
        return sequence {
            val seen = HashSet<Int>()
            for (dx in -radiusCells..radiusCells) {
                for (dy in -radiusCells..radiusCells) {
                    val ids = buckets[CellKey(cx + dx, cy + dy)] ?: continue
                    for (id in ids) {
                        if (seen.add(id)) yield(id)
                    }
                }
            }
        }
    }

    companion object {
        fun build(polylines: List<List<LatLon>>, cellDeg: Double): GridIndex {
            val acc = HashMap<CellKey, MutableList<Int>>()
            polylines.forEachIndexed { index, points ->
                val cells = HashSet<CellKey>()
                for (p in points) {
                    cells += CellKey(floorDiv(p.lon, cellDeg), floorDiv(p.lat, cellDeg))
                }
                for (cell in cells) {
                    acc.getOrPut(cell) { mutableListOf() }.add(index)
                }
            }
            val frozen = acc.mapValues { it.value.toIntArray() }
            return GridIndex(cellDeg, frozen)
        }

        private fun floorDiv(value: Double, cell: Double): Int {
            return kotlin.math.floor(value / cell).toInt()
        }
    }
}

data class VeniceGraph(
    val streets: List<StreetWay>,
    val canals: List<CanalWay>,
    val streetsById: Map<Long, StreetWay>,
    val streetIndex: GridIndex,
    val canalIndex: GridIndex,
) {
    companion object {
        const val CELL_DEG = 0.0008
    }
}
