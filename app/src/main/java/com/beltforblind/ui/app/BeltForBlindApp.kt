package com.beltforblind.ui.app

import android.os.SystemClock
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.beltforblind.motor.BleMotorController
import com.beltforblind.route.location.LocationSimulationProvider
import com.beltforblind.ui.components.AppBottomBar
import com.beltforblind.ui.components.AppDestination
import com.beltforblind.ui.record.RecordScreen
import com.beltforblind.ui.saved.SavedRoutesScreen
import com.beltforblind.ui.sport.DebugGpsScreen
import com.beltforblind.ui.sport.SportScreen

@Composable
fun BeltForBlindApp() {
    val context = LocalContext.current
    val motorController = remember(context) {
        BleMotorController(context.applicationContext)
    }
    var selectedPage by remember { mutableStateOf(AppDestination.Record) }
    var showDebugGps by remember { mutableStateOf(false) }
    var sportTapCount by remember { mutableIntStateOf(0) }
    var lastSportTapAt by remember { mutableLongStateOf(0L) }

    DisposableEffect(motorController) {
        onDispose { motorController.close() }
    }

    val selectPage: (AppDestination) -> Unit = { page ->
        if (page == AppDestination.Sport && LocationSimulationProvider.isAvailable) {
            val now = SystemClock.elapsedRealtime()
            sportTapCount = if (now - lastSportTapAt <= DEBUG_TAP_TIMEOUT_MS) {
                sportTapCount + 1
            } else {
                1
            }
            lastSportTapAt = now
            if (sportTapCount >= DEBUG_TAP_COUNT) {
                showDebugGps = true
                sportTapCount = 0
            }
        } else {
            sportTapCount = 0
            showDebugGps = false
        }
        selectedPage = page
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AppBottomBar(
                selectedDestination = selectedPage,
                onDestinationSelected = selectPage,
            )
        },
    ) { padding ->
        if (showDebugGps) {
            DebugGpsScreen(
                onOpenRecordPage = {
                    showDebugGps = false
                    selectedPage = AppDestination.Record
                },
                onClose = { showDebugGps = false },
                motorController = motorController,
                modifier = Modifier.padding(padding),
            )
        } else {
            when (selectedPage) {
                AppDestination.Record -> RecordScreen(
                    motorController = motorController,
                    modifier = Modifier.padding(padding),
                )
                AppDestination.Sport -> SportScreen(
                    onOpenRecordPage = { selectedPage = AppDestination.Record },
                    motorController = motorController,
                    modifier = Modifier.padding(padding),
                )
                AppDestination.Saved -> SavedRoutesScreen(
                    onOpenRecordPage = { selectedPage = AppDestination.Record },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

private const val DEBUG_TAP_COUNT = 5
private const val DEBUG_TAP_TIMEOUT_MS = 2500L
