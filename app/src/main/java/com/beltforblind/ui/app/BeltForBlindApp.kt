package com.beltforblind.ui.app

import android.os.SystemClock
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.beltforblind.ui.record.RecordScreen
import com.beltforblind.ui.saved.SavedRoutesScreen
import com.beltforblind.ui.sport.DebugGpsScreen
import com.beltforblind.ui.sport.SportScreen
import com.beltforblind.route.location.LocationSimulationProvider

@Composable
fun BeltForBlindApp() {
    var selectedPage by remember { mutableStateOf(AppPage.Record) }
    var showDebugGps by remember { mutableStateOf(false) }
    var sportTapCount by remember { mutableStateOf(0) }
    var lastSportTapAt by remember { mutableStateOf(0L) }

    val selectPage: (AppPage) -> Unit = { page ->
        if (page == AppPage.Sport && LocationSimulationProvider.isAvailable) {
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
        bottomBar = {
            AppBottomBar(
                selectedPage = selectedPage,
                onPageSelected = selectPage,
            )
        },
    ) { padding ->
        if (showDebugGps) {
            DebugGpsScreen(
                onOpenRecordPage = {
                    showDebugGps = false
                    selectedPage = AppPage.Record
                },
                onClose = { showDebugGps = false },
                modifier = Modifier.padding(padding),
            )
        } else {
            when (selectedPage) {
                AppPage.Record -> RecordScreen(modifier = Modifier.padding(padding))
                AppPage.Sport -> SportScreen(modifier = Modifier.padding(padding))
                AppPage.Saved -> SavedRoutesScreen(modifier = Modifier.padding(padding))
            }
        }
    }
}

private const val DEBUG_TAP_COUNT = 5
private const val DEBUG_TAP_TIMEOUT_MS = 2500L

private enum class AppPage(
    val label: String,
    val shortLabel: String,
) {
    Record(label = "记录", shortLabel = "记"),
    Sport(label = "运动", shortLabel = "动"),
    Saved(label = "已保存", shortLabel = "存"),
}

@Composable
private fun AppBottomBar(
    selectedPage: AppPage,
    onPageSelected: (AppPage) -> Unit,
) {
    NavigationBar {
        AppPage.entries.forEach { page ->
            NavigationBarItem(
                selected = selectedPage == page,
                onClick = { onPageSelected(page) },
                icon = { Text(page.shortLabel) },
                label = { Text(page.label) },
            )
        }
    }
}
