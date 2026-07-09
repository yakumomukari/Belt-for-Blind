interface LocationRecorder {
    fun startRecord()
    fun stopRecord()
    fun getPointCount(): Int
    fun getLatestAccuracy(): Float
    fun getRecordedPoints(): List<LocationPoint>
    fun saveRoute(name: String): Boolean
    fun loadRoutes(): List<Route>
    fun getRouteById(id: String): Route?
    fun getPointsForRoute(id: String): List<LocationPoint>
}

class MockLocationRecorder : LocationRecorder {
    private var recording = false
    private var pointCount = 0
    private val points = mutableListOf<LocationPoint>()
    private var lastAccuracy = 3.0f
    private val savedRoutes = mutableListOf<Route>()
    private val savedPoints = mutableMapOf<String, List<LocationPoint>>()

    override fun startRecord() {
        recording = true
        pointCount = 0
        points.clear()
    }

    override fun stopRecord() {
        recording = false
    }

    override fun getPointCount(): Int {
        if (recording) {
            pointCount++
            points.add(
                LocationPoint(
                    latitude = 39.9 + (Math.random() * 0.01),
                    longitude = 116.3 + (Math.random() * 0.01),
                    accuracy = 2.0f + (Math.random() * 5f).toFloat(),
                    timestamp = System.currentTimeMillis()
                )
            )
            lastAccuracy = points.last().accuracy
        }
        return pointCount
    }

    override fun getLatestAccuracy(): Float = lastAccuracy

    override fun getRecordedPoints(): List<LocationPoint> = points.toList()

    override fun saveRoute(name: String): Boolean {
        if (name.isBlank() || points.isEmpty()) return false
        val route = Route(
            id = UUID.randomUUID().toString(),
            name = name,
            pointCount = points.size,
            savedTime = System.currentTimeMillis()
        )
        savedRoutes.add(route)
        savedPoints[route.id] = points.toList()
        return true
    }

    override fun loadRoutes(): List<Route> = savedRoutes.toList()

    override fun getRouteById(id: String): Route? = savedRoutes.find { it.id == id }

    override fun getPointsForRoute(id: String): List<LocationPoint> =
        savedPoints[id] ?: emptyList()
}