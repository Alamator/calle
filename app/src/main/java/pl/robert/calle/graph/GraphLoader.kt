package pl.robert.calle.graph

import android.content.Context
import org.json.JSONObject

object GraphLoader {
    const val ASSET_NAME = "venice_graph.geojson"

    fun load(context: Context): VeniceGraph {
        val json = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        return parse(json)
    }

    fun parse(json: String): VeniceGraph {
        val root = JSONObject(json)
        val features = root.getJSONArray("features")
        val streets = ArrayList<StreetWay>(features.length())
        val canals = ArrayList<CanalWay>(256)
        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val props = feature.getJSONObject("properties")
            val geom = feature.getJSONObject("geometry")
            val coords = geom.getJSONArray("coordinates")
            val points = ArrayList<LatLon>(coords.length())
            for (c in 0 until coords.length()) {
                val pair = coords.getJSONArray(c)
                points += LatLon(lat = pair.getDouble(1), lon = pair.getDouble(0))
            }
            if (points.size < 2) continue
            val id = props.getLong("id")
            when (props.optString("kind")) {
                "street" -> streets += StreetWay(
                    id = id,
                    name = props.optString("name").ifBlank { null },
                    bridge = props.optBoolean("bridge"),
                    highway = props.optString("highway").ifBlank { null },
                    points = points,
                )
                "canal" -> canals += CanalWay(id = id, points = points)
            }
        }
        return VeniceGraph(
            streets = streets,
            canals = canals,
            streetsById = streets.associateBy { it.id },
            streetIndex = GridIndex.build(streets.map { it.points }, VeniceGraph.CELL_DEG),
            canalIndex = GridIndex.build(canals.map { it.points }, VeniceGraph.CELL_DEG),
        )
    }
}

object GraphHolder {
    @Volatile
    private var cached: VeniceGraph? = null

    fun get(context: Context): VeniceGraph {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: GraphLoader.load(context.applicationContext).also { cached = it }
        }
    }
}
