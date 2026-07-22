package com.beltforblind.navigation.vibration

enum class NavigationInputFreshness {
    Fresh,
    LocationStale,
    HeadingStale,
}

object NavigationInputFreshnessPolicy {
    fun evaluate(
        nowMillis: Long,
        lastLocationUpdateMillis: Long?,
        lastHeadingUpdateMillis: Long?,
    ): NavigationInputFreshness {
        if (isExpired(nowMillis, lastLocationUpdateMillis, LOCATION_TIMEOUT_MILLIS)) {
            return NavigationInputFreshness.LocationStale
        }
        if (isExpired(nowMillis, lastHeadingUpdateMillis, HEADING_TIMEOUT_MILLIS)) {
            return NavigationInputFreshness.HeadingStale
        }
        return NavigationInputFreshness.Fresh
    }

    private fun isExpired(nowMillis: Long, updatedAtMillis: Long?, timeoutMillis: Long): Boolean {
        return updatedAtMillis == null ||
            nowMillis < updatedAtMillis ||
            nowMillis - updatedAtMillis > timeoutMillis
    }

    const val LOCATION_TIMEOUT_MILLIS = 10_000L
    const val HEADING_TIMEOUT_MILLIS = 3_000L
}
