package com.beltforblind.route.location

import com.beltforblind.route.model.RoutePoint
import kotlinx.coroutines.flow.StateFlow

enum class SimulationScenario {
    Straight,
    RightAngleTurn,
    AccuracyFilter,
}

data class LocationSimulationState(
    val enabled: Boolean = false,
    val running: Boolean = false,
    val scenario: SimulationScenario = SimulationScenario.RightAngleTurn,
    val emittedPointCount: Int = 0,
    val currentPoint: RoutePoint? = null,
)

interface LocationSimulationGateway {
    val isAvailable: Boolean
    val isEnabled: Boolean
    val state: StateFlow<LocationSimulationState>

    fun start(scenario: SimulationScenario)

    fun stop()

    fun addListener(listener: (RoutePoint) -> Unit)

    fun removeListener(listener: (RoutePoint) -> Unit)
}
