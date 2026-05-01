package com.safesteps.map

import com.safesteps.data.Coordenada
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationInstructionsTest {
    @Test
    fun buildNavigationRouteModel_detectsRightTurn() {
        val route = buildNavigationRouteModel(
            coordinates = listOf(
                Coordenada(lat = 41.3851, lon = 2.1734),
                Coordenada(lat = 41.3851, lon = 2.1747),
                Coordenada(lat = 41.3842, lon = 2.1747)
            )
        )

        assertNotNull(route)
        assertEquals(NavigationManeuver.RIGHT, route!!.instructions.first().maneuver)
    }

    @Test
    fun resolveNavigationProgress_returnsUpcomingTurnDistance() {
        val route = buildNavigationRouteModel(
            coordinates = listOf(
                Coordenada(lat = 41.3851, lon = 2.1734),
                Coordenada(lat = 41.3851, lon = 2.1747),
                Coordenada(lat = 41.3842, lon = 2.1747)
            )
        )!!

        val progress = resolveNavigationProgress(
            route = route,
            currentLocation = Coordenada(lat = 41.3851, lon = 2.17405)
        )

        assertEquals(NavigationManeuver.RIGHT, progress.instruction.maneuver)
        assertTrue(progress.instruction.distanceMeters in 40.0..70.0)
    }

    @Test
    fun resolveNavigationProgress_fallsBackToContinueOnStraightRoute() {
        val route = buildNavigationRouteModel(
            coordinates = listOf(
                Coordenada(lat = 41.3851, lon = 2.1734),
                Coordenada(lat = 41.3851, lon = 2.1747),
                Coordenada(lat = 41.3851, lon = 2.1760)
            )
        )!!

        val progress = resolveNavigationProgress(
            route = route,
            currentLocation = Coordenada(lat = 41.3851, lon = 2.1740)
        )

        assertEquals(NavigationManeuver.CONTINUE, progress.instruction.maneuver)
        assertTrue(progress.instruction.remainingDistanceMeters > 100.0)
    }

    @Test
    fun resolveNavigationProgress_marksArrivalNearDestination() {
        val route = buildNavigationRouteModel(
            coordinates = listOf(
                Coordenada(lat = 41.3851, lon = 2.1734),
                Coordenada(lat = 41.3851, lon = 2.1747),
                Coordenada(lat = 41.3851, lon = 2.1760)
            )
        )!!

        val progress = resolveNavigationProgress(
            route = route,
            currentLocation = Coordenada(lat = 41.3851, lon = 2.17595)
        )

        assertEquals(NavigationManeuver.ARRIVE, progress.instruction.maneuver)
        assertTrue(progress.instruction.distanceMeters <= 25.0)
    }

    @Test
    fun resolveNavigationProgress_reportsDistanceToRoute() {
        val route = buildNavigationRouteModel(
            coordinates = listOf(
                Coordenada(lat = 41.3851, lon = 2.1734),
                Coordenada(lat = 41.3851, lon = 2.1760)
            )
        )!!

        val progress = resolveNavigationProgress(
            route = route,
            currentLocation = Coordenada(lat = 41.3869, lon = 2.1742)
        )

        assertTrue(progress.distanceToRouteMeters > 180.0)
    }

    @Test
    fun resolveNavigationProgress_marksUturnWhenGoingBackwards() {
        val route = buildNavigationRouteModel(
            coordinates = listOf(
                Coordenada(lat = 41.3851, lon = 2.1734),
                Coordenada(lat = 41.3851, lon = 2.1747),
                Coordenada(lat = 41.3842, lon = 2.1747)
            )
        )!!

        val progress = resolveNavigationProgress(
            route = route,
            currentLocation = Coordenada(lat = 41.3851, lon = 2.17375),
            minimumProgressMeters = 70.0
        )

        assertEquals(NavigationManeuver.U_TURN, progress.instruction.maneuver)
        assertTrue(progress.instruction.isCorrective)
        assertEquals(NavigationManeuver.RIGHT, progress.instruction.followUpManeuver)
        assertTrue((progress.instruction.followUpDistanceMeters ?: 0.0) > 70.0)
    }

    @Test
    fun resolveStartedRouteMode_usesFixedOverviewWhenOriginIsFarFromUser() {
        val routeMode = resolveStartedRouteMode(
            selectedOrigin = Coordenada(lat = 41.3851, lon = 2.1734),
            currentLocation = Coordenada(lat = 41.3875, lon = 2.1762)
        )

        assertEquals(ActiveRouteMode.FIXED_OVERVIEW, routeMode)
    }

    @Test
    fun resolveStartedRouteMode_usesLiveNavigationWhenOriginMatchesUserLocation() {
        val routeMode = resolveStartedRouteMode(
            selectedOrigin = Coordenada(lat = 41.3851, lon = 2.1734),
            currentLocation = Coordenada(lat = 41.38511, lon = 2.17341)
        )

        assertEquals(ActiveRouteMode.USER_LOCATION_NAVIGATION, routeMode)
    }

    @Test
    fun remainingRouteCoordinates_keepsOnlyPendingRouteSegment() {
        val route = buildNavigationRouteModel(
            coordinates = listOf(
                Coordenada(lat = 41.3851, lon = 2.1734),
                Coordenada(lat = 41.3851, lon = 2.1747),
                Coordenada(lat = 41.3842, lon = 2.1747)
            )
        )!!

        val remainingCoordinates = remainingRouteCoordinates(
            route = route,
            progressMeters = 60.0
        )

        assertTrue(remainingCoordinates.size >= 2)
        assertTrue(remainingCoordinates.first().lon > route.points.first().lon)
        assertEquals(route.points.last(), remainingCoordinates.last())
    }
}
