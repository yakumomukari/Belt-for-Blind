package com.beltforblind.ui.saved

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.beltforblind.route.location.AMapLocationDataSource
import com.beltforblind.route.model.RouteRecord
import com.beltforblind.route.recorder.RouteRecordingManager
import com.beltforblind.route.recorder.RouteRecorder
import com.beltforblind.route.storage.JsonRouteStore
import com.beltforblind.ui.record.MockRouteRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SavedRoutesViewModel(
    private val routeRecorder: RouteRecorder = MockRouteRecorder(),
) : ViewModel() {
    private val _routes = MutableStateFlow<List<RouteRecord>>(emptyList())
    val routes: StateFlow<List<RouteRecord>> = _routes.asStateFlow()

    fun loadRoutes() {
        _routes.value = routeRecorder.loadRoutes()
    }

    fun deleteRoute(routeId: String): Boolean {
        val deleted = routeRecorder.deleteRoute(routeId)
        if (deleted) {
            _routes.value = _routes.value.filterNot { it.id == routeId }
        }
        return deleted
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val recorder = RouteRecordingManager(
                        locationDataSource = AMapLocationDataSource(context.applicationContext),
                        routeStore = JsonRouteStore(context.applicationContext),
                    )
                    return SavedRoutesViewModel(recorder) as T
                }
            }
        }
    }
}
