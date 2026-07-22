package com.beltforblind.motor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BeltGpsRepository {
    private val mutableSample = MutableStateFlow<BeltGpsSample?>(null)
    val sample: StateFlow<BeltGpsSample?> = mutableSample.asStateFlow()

    internal fun publish(sample: BeltGpsSample) {
        mutableSample.value = sample
    }

    internal fun clear() {
        mutableSample.value = null
    }
}
