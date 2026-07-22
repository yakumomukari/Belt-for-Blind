package com.beltforblind.route.location

import android.content.Context
import com.beltforblind.motor.BeltGpsRepository
import com.beltforblind.motor.BeltGpsSample
import com.beltforblind.route.model.RoutePoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BeltPreferredLocationDataSource(
    context: Context,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : LocationDataSource {
    private val phoneLocation = AMapLocationDataSource(context.applicationContext)
    private var scope: CoroutineScope? = null
    private var sampleJob: Job? = null
    private var lastBeltPointAtMillis = 0L

    override fun start(onPoint: (RoutePoint) -> Unit) {
        stop()
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = newScope
        phoneLocation.start { point ->
            if (!isFreshValidBeltSample(BeltGpsRepository.sample.value)) {
                onPoint(point)
            }
        }
        sampleJob = newScope.launch {
            BeltGpsRepository.sample.collectLatest { sample ->
                if (isFreshValidBeltSample(sample)) {
                    val validSample = requireNotNull(sample)
                    if (
                        lastBeltPointAtMillis == 0L ||
                        validSample.receivedAtMillis - lastBeltPointAtMillis >=
                            BELT_RECORDING_INTERVAL_MS
                    ) {
                        BeltGpsRoutePointMapper.toRoutePoint(validSample)?.let { point ->
                            lastBeltPointAtMillis = validSample.receivedAtMillis
                            onPoint(point)
                        }
                    }
                }
            }
        }
    }

    override fun stop() {
        phoneLocation.stop()
        sampleJob?.cancel()
        sampleJob = null
        scope?.cancel()
        scope = null
        lastBeltPointAtMillis = 0L
    }

    private fun isFreshValidBeltSample(sample: BeltGpsSample?): Boolean {
        return sample != null &&
            sample.isFixValid &&
            currentTimeMillis() - sample.receivedAtMillis in 0..BELT_GPS_STALE_AFTER_MS
    }

    companion object {
        const val BELT_GPS_STALE_AFTER_MS = 6_000L
        const val BELT_RECORDING_INTERVAL_MS = 3_000L
    }
}
