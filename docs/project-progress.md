# 项目进度说明

## 项目概述

本项目是一个面向盲人跑步辅助腰带的 Android/Kotlin App。当前阶段主要关注路线记录的底层能力：定位点采集、路线数据维护、本地 JSON 保存、读取已保存路线，以及基础多页面框架。

当前阶段不做以下功能：

- 导航
- 蓝牙连接
- 震动电机控制
- 路线切线计算
- 路线匹配
- 腰带方向识别
- 后台长期运行
- 云同步

## 当前仓库状态

仓库目前已经搭建了 Android/Kotlin 项目框架，并已合入一版基于 Jetpack Compose 的多页面入口。当前包含“记录”和“运动”两个页面：记录页保留路线记录、定位、保存、已保存路线详情和高德 2D 地图预览能力；运动页目前仅作为占位页面，后续再接入运动过程相关功能。路线会保存为 App 私有目录下的 JSON 文件，重启 App 后仍可读取。

已创建结构：

```text
settings.gradle.kts
build.gradle.kts
app/
  build.gradle.kts
  src/main/
    AndroidManifest.xml
    java/com/beltforblind/
      MainActivity.kt
      route/
        model/
          RoutePoint.kt
          RouteRecord.kt
        recorder/
          RouteRecorder.kt
          RouteRecordingManager.kt
        location/
          LocationPermissionGateway.kt
          LocationDataSource.kt
          AMapLocationDataSource.kt
        storage/
          RouteStore.kt
          JsonRouteStore.kt
      ui/record/
        RecordingUiState.kt
        MockRouteRecorder.kt
        RecordViewModel.kt
        RecordScreen.kt
      ui/app/
        BeltForBlindApp.kt
      ui/sport/
        SportScreen.kt
    res/values/
      strings.xml
      styles.xml
docs/project-progress.md
```

## 已定义对外接口

UI 层后续应通过 `RouteRecorder` 使用路线记录能力，不应该直接接触 GPS API 或直接操作文件：

```text
startRecord()
stopRecord()
getPointCount()
getLatestAccuracy()
saveRoute(name)
loadRoutes()
```

## 数据模型

`RoutePoint` 当前定义了定位点必需字段：

```text
latitude
longitude
timestamp
accuracy
```

`RouteRecord` 当前定义了路线必需字段：

```text
id
name
createdAt
points
```

## 模块边界

- `route.model`：只放数据对象。
- `route.recorder`：面向 UI 的记录接口和协调管理类。
- `route.location`：定位权限和定位数据源边界。
- `route.storage`：路线持久化边界。
- `ui.record`：Compose 页面、页面状态、ViewModel、Mock 记录器。

## 当前可以测试

当前可以通过 UI 测试以下流程：

- 进入 App 后看到记录页面。
- 点击“开始记录”后触发定位权限申请。
- 授权后进入“记录中”状态。
- App 当前通过高德定位 SDK 接收定位点，定位模式为高精度并优先 GPS。
- 定位请求间隔为 3 秒，避免定位点刷新过快。
- 开始记录后先进行 15 秒 GPS 预热，预热阶段收到的点只用于诊断，不保存。
- 定位点会经过精度过滤：`accuracy == null` 或 `accuracy > 8m` 的点会被丢弃。
- 页面会显示已记录点数和最近一次有效精度。
- 定位请求会等待更准确的初始位置，减少刚开始记录时的低精度点。
- 页面会显示最近原始精度和精度状态，区分“合格已保存”和“不合格已丢弃”。
- 页面会显示 GPS 预热剩余时间和已丢弃点数。
- 页面会显示最近 5 个有效定位点的纬度、经度、时间和精度。
- 点击“停止记录”后进入等待保存状态。
- 输入路线名称后可以点击“保存路线”。
- 保存成功后可以点击“查看已保存路线”，看到本地 JSON 中的路线列表。
- 点击已保存路线后进入详情页，可查看高德 2D 地图预览、路径点 Canvas 预览图和前 20 个定位点详情。
- 地图预览会在地图上显示所有路径点、起终点和路径连线，并尽量放大到路径点范围。

这些测试依赖设备或模拟器可用定位。路线文件保存到 `filesDir/routes/`，App 重启后仍可读取。
地图预览使用高德原生 2D `MapView`，依赖设备网络和已配置的高德 Key。

## 尚未实现

以下内容仍为 TODO，后续继续实现：

- 针对记录状态、点过滤、保存读取行为的基础测试。
- 更强的轨迹质量控制，例如速度跳变过滤、距离跳变过滤、平滑处理。

## 建议下一步

1. 增加单元测试，覆盖点过滤、预热丢弃、保存读取和记录状态。
2. 在真机或模拟器上验证定位权限、开始记录、停止记录、点数和精度显示。
3. 根据实测数据决定是否加入速度跳变过滤、距离跳变过滤或平滑处理。
