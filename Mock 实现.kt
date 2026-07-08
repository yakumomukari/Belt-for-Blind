class MockLocationRecorder : LocationRecorder {
    private var recording = false
    private var pointCount = 0
    private val random = Random(42)

    override fun startRecord() {
        recording = true
        pointCount = 0
    }

    override fun stopRecord() {
        recording = false
    }

    override fun getPointCount(): Int {
        if (recording) pointCount += 1  // 模拟每秒新增一个点
        return pointCount
    }

    override fun getLatestAccuracy(): Float = random.nextFloat() * 5f + 2f

    override fun saveRoute(name: String): Boolean {
        // 模拟写入 JSON 文件，返回结果
        return name.isNotBlank()
    }

    override fun loadRoutes(): List<Route> = emptyList()
}