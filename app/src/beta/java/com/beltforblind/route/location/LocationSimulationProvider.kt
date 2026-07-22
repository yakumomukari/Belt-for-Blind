package com.beltforblind.route.location

import com.beltforblind.route.model.RoutePoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LocationSimulationProvider : LocationSimulationGateway {
    override val isAvailable: Boolean = false
    override val isEnabled: Boolean = false

    private val mutableState = MutableStateFlow(LocationSimulationState())
    override val state: StateFlow<LocationSimulationState> = mutableState.asStateFlow()

    override fun start(scenario: SimulationScenario) = Unit

    override fun stop() = Unit

    override fun addListener(listener: (RoutePoint) -> Unit) = Unit

    override fun removeListener(listener: (RoutePoint) -> Unit) = Unit
}
