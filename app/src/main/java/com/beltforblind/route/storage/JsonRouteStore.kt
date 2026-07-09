package com.beltforblind.route.storage

import android.content.Context
import com.beltforblind.route.model.RoutePoint
import com.beltforblind.route.model.RouteRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class JsonRouteStore(
    context: Context,
) : RouteStore {
    private val routesDir = File(context.filesDir, ROUTES_DIR_NAME)

    override fun save(route: RouteRecord): RouteRecord {
        if (!routesDir.exists()) {
            routesDir.mkdirs()
        }

        val file = File(routesDir, "${route.id}.json")
        file.writeText(route.toJson().toString(2), Charsets.UTF_8)
        return route
    }

    override fun loadAll(): List<RouteRecord> {
        if (!routesDir.exists()) {
            return emptyList()
        }

        return routesDir
            .listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .mapNotNull { file ->
                runCatching {
                    JSONObject(file.readText(Charsets.UTF_8)).toRouteRecord()
                }.getOrNull()
            }
            .sortedByDescending { it.createdAt }
    }

    private fun RouteRecord.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("name", name)
            .put("createdAt", createdAt)
            .put(
                "points",
                JSONArray().also { array ->
                    points.forEach { point ->
                        array.put(point.toJson())
                    }
                },
            )
    }

    private fun RoutePoint.toJson(): JSONObject {
        return JSONObject()
            .put("latitude", latitude)
            .put("longitude", longitude)
            .put("timestamp", timestamp)
            .put("accuracy", accuracy)
    }

    private fun JSONObject.toRouteRecord(): RouteRecord {
        val pointsJson = getJSONArray("points")
        val points = buildList {
            for (index in 0 until pointsJson.length()) {
                add(pointsJson.getJSONObject(index).toRoutePoint())
            }
        }

        return RouteRecord(
            id = getString("id"),
            name = getString("name"),
            createdAt = getLong("createdAt"),
            points = points,
        )
    }

    private fun JSONObject.toRoutePoint(): RoutePoint {
        val accuracy = if (isNull("accuracy")) {
            null
        } else {
            getDouble("accuracy").toFloat()
        }

        return RoutePoint(
            latitude = getDouble("latitude"),
            longitude = getDouble("longitude"),
            timestamp = getLong("timestamp"),
            accuracy = accuracy,
        )
    }

    private companion object {
        const val ROUTES_DIR_NAME = "routes"
    }
}
