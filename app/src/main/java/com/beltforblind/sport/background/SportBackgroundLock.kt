package com.beltforblind.sport.background

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object SportBackgroundLock {
    fun start(context: Context) {
        ContextCompat.startForegroundService(
            context.applicationContext,
            Intent(context, SportBackgroundService::class.java),
        )
    }

    fun stop(context: Context) {
        context.applicationContext.stopService(
            Intent(context, SportBackgroundService::class.java),
        )
    }
}
