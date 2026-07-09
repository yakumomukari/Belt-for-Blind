package com.beltforblind.ui.app

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
import com.beltforblind.ui.sport.SportScreen

@Composable
fun BeltForBlindApp() {
    var selectedPage by remember { mutableStateOf(AppPage.Record) }

    Scaffold(
        bottomBar = {
            AppBottomBar(
                selectedPage = selectedPage,
                onPageSelected = { selectedPage = it },
            )
        },
    ) { padding ->
        when (selectedPage) {
            AppPage.Record -> RecordScreen(modifier = Modifier.padding(padding))
            AppPage.Sport -> SportScreen(modifier = Modifier.padding(padding))
        }
    }
}

private enum class AppPage(
    val label: String,
    val shortLabel: String,
) {
    Record(label = "记录", shortLabel = "记"),
    Sport(label = "运动", shortLabel = "动"),
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
