package com.safesteps.map

import com.safesteps.data.Coordenada
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class NavigationManeuver {
    CONTINUE,
    SLIGHT_LEFT,
    LEFT,
    SHARP_LEFT,
    SLIGHT_RIGHT,
    RIGHT,
    SHARP_RIGHT,
    U_TURN,
    ARRIVE
}

data class ActiveNavigationInstruction(
    val maneuver: NavigationManeuver,
    val distanceMeters: Double,
    val remainingDistanceMeters: Double,
    val followUpManeuver: NavigationManeuver? = null,
    val followUpDistanceMeters: Double? = null,
    val isCorrective: Boolean = false
)

internal data class NavigationProgressResult(
    val progressMeters: Double,
    val currentProgressMeters: Double,
    val distanceToRouteMeters: Double,
    val instruction: ActiveNavigationInstruction
)

internal data class NavigationRouteModel(
    val points: List<Coordenada>,
    val cumulativeDistancesMeters: List<Double>,
    val instructions: List<RouteInstruction>,
    val totalDistanceMeters: Double
)

internal data class RouteInstruction(
    val maneuver: NavigationManeuver,
    val distanceFromStartMeters: Double,
    val point: Coordenada
)

private data class MeterPoint(
    val x: Double,
    val y: Double
)

private data class SegmentProjection(
    val distanceToSegmentMeters: Double,
    val distanceAlongSegmentMeters: Double
)

private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val DUPLICATE_POINT_TOLERANCE_METERS = 1.5
private const val INSTRUCTION_ANCHOR_DISTANCE_METERS = 18.0
private const val MIN_INSTRUCTION_SPACING_METERS = 24.0
private const val PASSED_MANEUVER_DISTANCE_METERS = 12.0
internal const val ARRIVAL_DISTANCE_METERS = 25.0
private const val BACKTRACKING_THRESHOLD_METERS = 20.0

internal fun buildNavigationRouteModel(
    coordinates: List<Coordenada>,
    origin: Coordenada? = null,
    destination: Coordenada? = null
): NavigationRouteModel? {
    val points = buildList {
        origin?.let { add(it) }
        addAll(coordinates)
        destination?.let { add(it) }
    }.removeConsecutiveDuplicates()

    if (points.size < 2) {
        return null
    }

    val cumulativeDistances = mutableListOf(0.0)
    for (index in 1 until points.size) {
        cumulativeDistances += cumulativeDistances.last() + distanceMeters(points[index - 1], points[index])
    }

    val instructions = mutableListOf<RouteInstruction>()
    for (index in 1 until points.lastIndex) {
        val previousIndex = findAnchorIndexBackward(
            cumulativeDistances = cumulativeDistances,
            currentIndex = index,
            minimumDistanceMeters = INSTRUCTION_ANCHOR_DISTANCE_METERS
        ) ?: continue
        val nextIndex = findAnchorIndexForward(
            cumulativeDistances = cumulativeDistances,
            currentIndex = index,
            minimumDistanceMeters = INSTRUCTION_ANCHOR_DISTANCE_METERS
        ) ?: continue

        val maneuver = classifyManeuver(
            previous = points[previousIndex],
            current = points[index],
            next = points[nextIndex]
        )

        if (maneuver == NavigationManeuver.CONTINUE) {
            continue
        }

        val distanceFromStart = cumulativeDistances[index]
        val previousInstruction = instructions.lastOrNull()
        if (
            previousInstruction != null &&
            distanceFromStart - previousInstruction.distanceFromStartMeters < MIN_INSTRUCTION_SPACING_METERS
        ) {
            if (maneuverPriority(maneuver) >= maneuverPriority(previousInstruction.maneuver)) {
                instructions[instructions.lastIndex] = RouteInstruction(
                    maneuver = maneuver,
                    distanceFromStartMeters = distanceFromStart,
                    point = points[index]
                )
            }
            continue
        }

        instructions += RouteInstruction(
            maneuver = maneuver,
            distanceFromStartMeters = distanceFromStart,
            point = points[index]
        )
    }

    instructions += RouteInstruction(
        maneuver = NavigationManeuver.ARRIVE,
        distanceFromStartMeters = cumulativeDistances.last(),
        point = points.last()
    )

    return NavigationRouteModel(
        points = points,
        cumulativeDistancesMeters = cumulativeDistances,
        instructions = instructions,
        totalDistanceMeters = cumulativeDistances.last()
    )
}

internal fun resolveNavigationProgress(
    route: NavigationRouteModel,
    currentLocation: Coordenada,
    minimumProgressMeters: Double = 0.0
): NavigationProgressResult {
    val routeProjection = projectRouteMetrics(
        points = route.points,
        cumulativeDistancesMeters = route.cumulativeDistancesMeters,
        currentLocation = currentLocation
    )
    val rawProgressMeters = routeProjection.progressMeters.coerceIn(0.0, route.totalDistanceMeters)
    val progressMeters = max(minimumProgressMeters, rawProgressMeters)
        .coerceIn(0.0, route.totalDistanceMeters)
    val isBacktracking = minimumProgressMeters - rawProgressMeters > BACKTRACKING_THRESHOLD_METERS
    val currentProgressMeters = if (isBacktracking) rawProgressMeters else progressMeters
    val remainingDistanceMeters = (route.totalDistanceMeters - currentProgressMeters).coerceAtLeast(0.0)

    if (isBacktracking) {
        val followUpInstruction = route.instructions.firstOrNull { instruction ->
            instruction.distanceFromStartMeters > rawProgressMeters + PASSED_MANEUVER_DISTANCE_METERS
        }
        val followUpDistanceMeters = when {
            followUpInstruction == null || followUpInstruction.maneuver == NavigationManeuver.ARRIVE -> {
                remainingDistanceMeters
            }

            else -> (followUpInstruction.distanceFromStartMeters - rawProgressMeters).coerceAtLeast(0.0)
        }

        return NavigationProgressResult(
            progressMeters = progressMeters,
            currentProgressMeters = currentProgressMeters,
            distanceToRouteMeters = routeProjection.distanceToRouteMeters,
            instruction = ActiveNavigationInstruction(
                maneuver = NavigationManeuver.U_TURN,
                distanceMeters = 0.0,
                remainingDistanceMeters = remainingDistanceMeters,
                followUpManeuver = followUpInstruction?.maneuver?.takeUnless {
                    it == NavigationManeuver.ARRIVE
                },
                followUpDistanceMeters = followUpDistanceMeters.takeIf { it > 0.0 },
                isCorrective = true
            )
        )
    }

    val nextInstruction = route.instructions.firstOrNull { instruction ->
        instruction.distanceFromStartMeters > currentProgressMeters + PASSED_MANEUVER_DISTANCE_METERS
    }

    val activeInstruction = when {
        nextInstruction == null && remainingDistanceMeters <= ARRIVAL_DISTANCE_METERS -> {
            ActiveNavigationInstruction(
                maneuver = NavigationManeuver.ARRIVE,
                distanceMeters = remainingDistanceMeters,
                remainingDistanceMeters = remainingDistanceMeters
            )
        }

        nextInstruction == null || nextInstruction.maneuver == NavigationManeuver.ARRIVE -> {
            ActiveNavigationInstruction(
                maneuver = if (remainingDistanceMeters <= ARRIVAL_DISTANCE_METERS) {
                    NavigationManeuver.ARRIVE
                } else {
                    NavigationManeuver.CONTINUE
                },
                distanceMeters = remainingDistanceMeters,
                remainingDistanceMeters = remainingDistanceMeters
            )
        }

        else -> {
            ActiveNavigationInstruction(
                maneuver = nextInstruction.maneuver,
                distanceMeters = (nextInstruction.distanceFromStartMeters - progressMeters).coerceAtLeast(0.0),
                remainingDistanceMeters = remainingDistanceMeters
            )
        }
    }

    return NavigationProgressResult(
        progressMeters = progressMeters,
        currentProgressMeters = currentProgressMeters,
        distanceToRouteMeters = routeProjection.distanceToRouteMeters,
        instruction = activeInstruction
    )
}

internal fun remainingRouteCoordinates(
    route: NavigationRouteModel,
    progressMeters: Double
): List<Coordenada> {
    if (route.points.isEmpty()) {
        return emptyList()
    }

    val normalizedProgressMeters = progressMeters.coerceIn(0.0, route.totalDistanceMeters)
    if (normalizedProgressMeters <= 0.0) {
        return route.points
    }

    if (normalizedProgressMeters >= route.totalDistanceMeters) {
        return emptyList()
    }

    for (index in 1 until route.cumulativeDistancesMeters.size) {
        val segmentStartMeters = route.cumulativeDistancesMeters[index - 1]
        val segmentEndMeters = route.cumulativeDistancesMeters[index]
        if (normalizedProgressMeters > segmentEndMeters) {
            continue
        }

        val segmentLengthMeters = segmentEndMeters - segmentStartMeters
        val interpolatedPoint = if (segmentLengthMeters <= 0.0) {
            route.points[index]
        } else {
            interpolateCoordinate(
                start = route.points[index - 1],
                end = route.points[index],
                ratio = ((normalizedProgressMeters - segmentStartMeters) / segmentLengthMeters)
                    .coerceIn(0.0, 1.0)
            )
        }

        return buildList {
            add(interpolatedPoint)
            addAll(route.points.subList(index, route.points.size))
        }.removeConsecutiveDuplicates()
    }

    return emptyList()
}

private fun List<Coordenada>.removeConsecutiveDuplicates(): List<Coordenada> {
    if (isEmpty()) {
        return emptyList()
    }

    val deduplicated = mutableListOf(first())
    for (index in 1 until size) {
        val candidate = this[index]
        if (distanceMeters(deduplicated.last(), candidate) > DUPLICATE_POINT_TOLERANCE_METERS) {
            deduplicated += candidate
        }
    }
    return deduplicated
}

private fun interpolateCoordinate(
    start: Coordenada,
    end: Coordenada,
    ratio: Double
): Coordenada {
    return Coordenada(
        lat = start.lat + (end.lat - start.lat) * ratio,
        lon = start.lon + (end.lon - start.lon) * ratio
    )
}

private fun findAnchorIndexBackward(
    cumulativeDistances: List<Double>,
    currentIndex: Int,
    minimumDistanceMeters: Double
): Int? {
    var candidate = currentIndex - 1
    while (candidate >= 0) {
        if (cumulativeDistances[currentIndex] - cumulativeDistances[candidate] >= minimumDistanceMeters) {
            return candidate
        }
        candidate--
    }
    return null
}

private fun findAnchorIndexForward(
    cumulativeDistances: List<Double>,
    currentIndex: Int,
    minimumDistanceMeters: Double
): Int? {
    var candidate = currentIndex + 1
    while (candidate < cumulativeDistances.size) {
        if (cumulativeDistances[candidate] - cumulativeDistances[currentIndex] >= minimumDistanceMeters) {
            return candidate
        }
        candidate++
    }
    return null
}

private fun classifyManeuver(
    previous: Coordenada,
    current: Coordenada,
    next: Coordenada
): NavigationManeuver {
    val incomingBearing = bearingDegrees(previous, current)
    val outgoingBearing = bearingDegrees(current, next)
    val signedDelta = normalizeSignedAngle(outgoingBearing - incomingBearing)
    val absoluteDelta = abs(signedDelta)

    if (absoluteDelta < 25.0) {
        return NavigationManeuver.CONTINUE
    }

    if (absoluteDelta >= 170.0) {
        return NavigationManeuver.U_TURN
    }

    val isRightTurn = signedDelta > 0.0
    return when {
        absoluteDelta < 55.0 -> {
            if (isRightTurn) NavigationManeuver.SLIGHT_RIGHT else NavigationManeuver.SLIGHT_LEFT
        }

        absoluteDelta < 125.0 -> {
            if (isRightTurn) NavigationManeuver.RIGHT else NavigationManeuver.LEFT
        }

        else -> {
            if (isRightTurn) NavigationManeuver.SHARP_RIGHT else NavigationManeuver.SHARP_LEFT
        }
    }
}

private fun maneuverPriority(maneuver: NavigationManeuver): Int {
    return when (maneuver) {
        NavigationManeuver.CONTINUE -> 0
        NavigationManeuver.SLIGHT_LEFT, NavigationManeuver.SLIGHT_RIGHT -> 1
        NavigationManeuver.LEFT, NavigationManeuver.RIGHT -> 2
        NavigationManeuver.SHARP_LEFT, NavigationManeuver.SHARP_RIGHT -> 3
        NavigationManeuver.U_TURN -> 4
        NavigationManeuver.ARRIVE -> 5
    }
}

private data class RouteProjectionMetrics(
    val progressMeters: Double,
    val distanceToRouteMeters: Double
)

private fun projectRouteMetrics(
    points: List<Coordenada>,
    cumulativeDistancesMeters: List<Double>,
    currentLocation: Coordenada
): RouteProjectionMetrics {
    if (points.size < 2) {
        return RouteProjectionMetrics(
            progressMeters = 0.0,
            distanceToRouteMeters = 0.0
        )
    }

    var closestDistanceMeters = Double.POSITIVE_INFINITY
    var bestProgressMeters = 0.0

    for (index in 0 until points.lastIndex) {
        val projection = projectOntoSegment(
            point = currentLocation,
            start = points[index],
            end = points[index + 1]
        )
        if (projection.distanceToSegmentMeters < closestDistanceMeters) {
            closestDistanceMeters = projection.distanceToSegmentMeters
            bestProgressMeters = cumulativeDistancesMeters[index] + projection.distanceAlongSegmentMeters
        }
    }

    return RouteProjectionMetrics(
        progressMeters = bestProgressMeters,
        distanceToRouteMeters = closestDistanceMeters
    )
}

private fun projectOntoSegment(
    point: Coordenada,
    start: Coordenada,
    end: Coordenada
): SegmentProjection {
    val originLatitudeRadians = Math.toRadians(start.lat)
    val originLongitudeRadians = Math.toRadians(start.lon)
    val endPoint = toMeterPoint(end, originLatitudeRadians, originLongitudeRadians)
    val currentPoint = toMeterPoint(point, originLatitudeRadians, originLongitudeRadians)
    val segmentLengthSquared = endPoint.x * endPoint.x + endPoint.y * endPoint.y

    if (segmentLengthSquared <= 0.0) {
        return SegmentProjection(
            distanceToSegmentMeters = distanceMeters(point, start),
            distanceAlongSegmentMeters = 0.0
        )
    }

    val projectionRatio = (
        (currentPoint.x * endPoint.x + currentPoint.y * endPoint.y) / segmentLengthSquared
        ).coerceIn(0.0, 1.0)
    val projectedX = endPoint.x * projectionRatio
    val projectedY = endPoint.y * projectionRatio

    return SegmentProjection(
        distanceToSegmentMeters = hypot(currentPoint.x - projectedX, currentPoint.y - projectedY),
        distanceAlongSegmentMeters = sqrt(segmentLengthSquared) * projectionRatio
    )
}

private fun toMeterPoint(
    coordinate: Coordenada,
    originLatitudeRadians: Double,
    originLongitudeRadians: Double
): MeterPoint {
    val latitudeRadians = Math.toRadians(coordinate.lat)
    val longitudeRadians = Math.toRadians(coordinate.lon)

    return MeterPoint(
        x = (longitudeRadians - originLongitudeRadians) * cos((latitudeRadians + originLatitudeRadians) / 2.0) * EARTH_RADIUS_METERS,
        y = (latitudeRadians - originLatitudeRadians) * EARTH_RADIUS_METERS
    )
}

private fun distanceMeters(
    start: Coordenada,
    end: Coordenada
): Double {
    val latitudeDelta = Math.toRadians(end.lat - start.lat)
    val longitudeDelta = Math.toRadians(end.lon - start.lon)
    val startLatitudeRadians = Math.toRadians(start.lat)
    val endLatitudeRadians = Math.toRadians(end.lat)

    val a = sin(latitudeDelta / 2.0) * sin(latitudeDelta / 2.0) +
        cos(startLatitudeRadians) * cos(endLatitudeRadians) *
        sin(longitudeDelta / 2.0) * sin(longitudeDelta / 2.0)

    return 2.0 * EARTH_RADIUS_METERS * atan2(sqrt(a), sqrt(1.0 - a))
}

private fun bearingDegrees(
    start: Coordenada,
    end: Coordenada
): Double {
    val startLatitudeRadians = Math.toRadians(start.lat)
    val endLatitudeRadians = Math.toRadians(end.lat)
    val longitudeDeltaRadians = Math.toRadians(end.lon - start.lon)

    val y = sin(longitudeDeltaRadians) * cos(endLatitudeRadians)
    val x = cos(startLatitudeRadians) * sin(endLatitudeRadians) -
        sin(startLatitudeRadians) * cos(endLatitudeRadians) * cos(longitudeDeltaRadians)

    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

private fun normalizeSignedAngle(angleDegrees: Double): Double {
    return ((angleDegrees + 540.0) % 360.0) - 180.0
}

internal fun formatDistanceLabel(distanceMeters: Double): String {
    val safeDistanceMeters = max(0.0, distanceMeters)
    return if (safeDistanceMeters < 1000.0) {
        "${safeDistanceMeters.roundToInt()} m"
    } else {
        val kilometers = safeDistanceMeters / 1000.0
        "${(min(99.9, kilometers) * 10.0).roundToInt() / 10.0} km"
    }
}
