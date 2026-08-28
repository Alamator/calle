package pl.robert.calle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.robert.calle.graph.CanalWay
import pl.robert.calle.graph.GridIndex
import pl.robert.calle.graph.LatLon
import pl.robert.calle.graph.StreetSnapper
import pl.robert.calle.graph.StreetWay
import pl.robert.calle.graph.VeniceGraph

class StreetSnapperTest {
    private val calleA = street(
        id = 1,
        name = "Calle A",
        points = listOf(LatLon(45.43700, 12.33500), LatLon(45.43740, 12.33500)),
    )
    private val calleB = street(
        id = 2,
        name = "Calle B",
        points = listOf(LatLon(45.43700, 12.33540), LatLon(45.43740, 12.33540)),
    )
    private val ponte = street(
        id = 3,
        name = "Ponte Test",
        bridge = true,
        points = listOf(LatLon(45.43720, 12.33500), LatLon(45.43720, 12.33540)),
    )
    private val canal = CanalWay(
        id = 9,
        points = listOf(LatLon(45.43690, 12.33520), LatLon(45.43750, 12.33520)),
    )

    private val graph = graphOf(listOf(calleA, calleB, ponte), listOf(canal))
    private val snapper = StreetSnapper(graph)

    @Test
    fun snapsToNearestCalle() {
        val hit = snapper.snap(LatLon(45.43720, 12.33503), lastWayId = null)
        assertNotNull(hit)
        assertEquals(1L, hit!!.hit.way.id)
        assertTrue(hit.hit.distanceM < 5.0)
    }

    @Test
    fun hysteresisKeepsCurrentWay() {
        val first = snapper.snap(LatLon(45.43720, 12.33503), lastWayId = null)
        assertEquals(1L, first!!.hit.way.id)
        val midway = LatLon(45.43720, 12.33518)
        val held = snapper.snap(midway, lastWayId = 1L)
        assertNotNull(held)
        assertEquals(1L, held!!.hit.way.id)
        assertTrue(held.heldByHysteresis || held.hit.way.id == 1L)
    }

    @Test
    fun rejectsCanalCrossingUnlessPonte() {
        val onlyCalleA = graphOf(listOf(calleA), listOf(canal))
        val isolated = StreetSnapper(onlyCalleA)
        val acrossCanal = isolated.snap(LatLon(45.43720, 12.33530), lastWayId = null)
        assertNull(acrossCanal)

        val onPonte = snapper.snap(LatLon(45.43721, 12.33520), lastWayId = null)
        assertNotNull(onPonte)
        assertEquals(3L, onPonte!!.hit.way.id)
        assertTrue(onPonte.hit.way.bridge)
    }

    @Test
    fun farFixIsIgnored() {
        val miss = snapper.snap(LatLon(45.44000, 12.34000), lastWayId = null)
        assertNull(miss)
    }

    private fun street(
        id: Long,
        name: String,
        points: List<LatLon>,
        bridge: Boolean = false,
    ): StreetWay = StreetWay(id, name, bridge, "footway", points)

    private fun graphOf(streets: List<StreetWay>, canals: List<CanalWay>): VeniceGraph {
        return VeniceGraph(
            streets = streets,
            canals = canals,
            streetsById = streets.associateBy { it.id },
            streetIndex = GridIndex.build(streets.map { it.points }, VeniceGraph.CELL_DEG),
            canalIndex = GridIndex.build(canals.map { it.points }, VeniceGraph.CELL_DEG),
        )
    }
}
