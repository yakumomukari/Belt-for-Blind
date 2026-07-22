package com.beltforblind.navigation.vibration

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationInputFreshnessPolicyTest {
    @Test
    fun reportsFreshWhenBothInputsAreRecent() {
        assertEquals(
            NavigationInputFreshness.Fresh,
            NavigationInputFreshnessPolicy.evaluate(
                nowMillis = 20_000L,
                lastLocationUpdateMillis = 14_000L,
                lastHeadingUpdateMillis = 19_000L,
            ),
        )
    }

    @Test
    fun locationTimeoutHasPriority() {
        assertEquals(
            NavigationInputFreshness.LocationStale,
            NavigationInputFreshnessPolicy.evaluate(
                nowMillis = 20_001L,
                lastLocationUpdateMillis = 10_000L,
                lastHeadingUpdateMillis = 20_000L,
            ),
        )
    }

    @Test
    fun reportsMissingLocationAsStale() {
        assertEquals(
            NavigationInputFreshness.LocationStale,
            NavigationInputFreshnessPolicy.evaluate(
                nowMillis = 20_000L,
                lastLocationUpdateMillis = null,
                lastHeadingUpdateMillis = 20_000L,
            ),
        )
    }

    @Test
    fun reportsHeadingTimeoutAfterLocationPasses() {
        assertEquals(
            NavigationInputFreshness.HeadingStale,
            NavigationInputFreshnessPolicy.evaluate(
                nowMillis = 20_001L,
                lastLocationUpdateMillis = 20_000L,
                lastHeadingUpdateMillis = 17_000L,
            ),
        )
    }

    @Test
    fun rejectsClockMovingBackwards() {
        assertEquals(
            NavigationInputFreshness.LocationStale,
            NavigationInputFreshnessPolicy.evaluate(
                nowMillis = 10_000L,
                lastLocationUpdateMillis = 10_001L,
                lastHeadingUpdateMillis = 10_000L,
            ),
        )
    }
}
