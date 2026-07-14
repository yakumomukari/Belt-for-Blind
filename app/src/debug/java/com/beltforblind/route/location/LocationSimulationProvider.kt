package com.beltforblind.route.location

import com.beltforblind.route.model.RoutePoint
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.cos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object LocationSimulationProvider : LocationSimulationGateway {
    override val isAvailable: Boolean = true
    override val isEnabled: Boolean
        get() = _state.value.enabled

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val listeners = CopyOnWriteArraySet<(RoutePoint) -> Unit>()
    private val _state = MutableStateFlow(LocationSimulationState())
    override val state: StateFlow<LocationSimulationState> = _state.asStateFlow()
    private var playbackJob: Job? = null

    override fun start(scenario: SimulationScenario) {
        playbackJob?.cancel()
        val route = scenario.createRoute()
        val playbackRoute = route + route.asReversed().drop(1).dropLast(1)
        _state.value = LocationSimulationState(
            enabled = true,
            running = true,
            scenario = scenario,
        )

        playbackJob = scope.launch {
            while (isActive) {
                playbackRoute.forEach { template ->
                    if (!isActive) {
                        return@launch
                    }
                    val point = template.copy(timestamp = System.currentTimeMillis())
                    val nextCount = _state.value.emittedPointCount + 1
                    _state.value = _state.value.copy(
                        running = true,
                        emittedPointCount = nextCount,
                        currentPoint = point,
                    )
                    listeners.forEach { it(point) }
                    delay(LOCATION_INTERVAL_MS)
                }
            }
        }
    }

    override fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        _state.value = _state.value.copy(
            enabled = false,
            running = false,
            currentPoint = null,
        )
    }

    override fun addListener(listener: (RoutePoint) -> Unit) {
        listeners += listener
        if (isEnabled) {
            _state.value.currentPoint?.let(listener)
        }
    }

    override fun removeListener(listener: (RoutePoint) -> Unit) {
        listeners -= listener
    }

    private fun SimulationScenario.createRoute(): List<RoutePoint> {
        return when (this) {
            SimulationScenario.Straight -> buildList {
                for (index in 0..80) {
                    add(createPoint(northMeters = 0.0, eastMeters = index * 5.0, accuracy = 3f))
                }
            }
            SimulationScenario.RightAngleTurn -> buildList {
                for (index in 0..30) {
                    add(createPoint(northMeters = index * 5.0, eastMeters = 0.0, accuracy = 3f))
                }
                for (index in 1..30) {
                    add(createPoint(northMeters = 150.0, eastMeters = index * 5.0, accuracy = 3f))
                }
            }
            SimulationScenario.AccuracyFilter -> buildList {
                for (index in 0..60) {
                    add(
                        createPoint(
                            northMeters = index * 5.0,
                            eastMeters = 0.0,
                            accuracy = if (index % 2 == 0) 3f else 15f,
                        ),
                    )
                }
            }
        }
    }

    private fun createPoint(
        northMeters: Double,
        eastMeters: Double,
        accuracy: Float,
    ): RoutePoint {
        val latitude = ORIGIN_LATITUDE + northMeters / METERS_PER_LATITUDE_DEGREE
        val longitudeMetersPerDegree = METERS_PER_LATITUDE_DEGREE *
            cos(Math.toRadians(ORIGIN_LATITUDE))
        val longitude = ORIGIN_LONGITUDE + eastMeters / longitudeMetersPerDegree
        return RoutePoint(
            latitude = latitude,
            longitude = longitude,
            timestamp = 0L,
            accuracy = accuracy,
        )
    }

    private const val LOCATION_INTERVAL_MS = 3000L
    private const val ORIGIN_LATITUDE = 39.9928
    private const val ORIGIN_LONGITUDE = 116.3109
    private const val METERS_PER_LATITUDE_DEGREE = 111_320.0
}
