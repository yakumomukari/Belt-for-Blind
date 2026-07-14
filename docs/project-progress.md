# 项目进度说明

## 项目概述

本项目是一个面向盲人跑步辅助腰带的 Android/Kotlin App。当前阶段主要关注路线记录的底层能力和基础页面框架：定位点采集、路线数据维护、本地 JSON 保存、读取已保存路线、记录页地图展示、“记录 / 运动 / 已保存”多页面入口，以及路线切线方向的纯算法获取能力。

当前阶段不做以下功能：

- 导航
- 蓝牙连接
- 震动电机控制
- 路线匹配
- 腰带方向识别
- 后台长期运行
- 云同步

## 当前仓库状态

仓库目前已经搭建了 Android/Kotlin 项目框架，并已合入一版基于 Jetpack Compose 的多页面入口。当前包含“记录”“运动”“已保存”三个页面：记录页以高德 2D 地图为主视觉，授权后显示当前位置蓝点和定位按钮；点击 GO 后开始记录，按钮变为 STOP，并显示 50% 透明度的已记录点数浮层；地图会实时绘制当前有效路径点和轨迹；点击 STOP 后弹出是否保存确认，选择“是”后进入路线命名保存界面，选择“否”后放弃本次记录并回到初始记录界面。运动页目前仅作为占位页面，后续再接入运动过程相关功能；已保存页用于独立查看本地路线列表，并可进入路线详情。路线会保存为 App 私有目录下的 JSON 文件，重启 App 后仍可读取。路线切线能力已接入已保存路线详情页，用于显示路线末端方向，但尚未接入导航、蓝牙或震动反馈。

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
          AMapMapLocationSource.kt
        storage/
          RouteStore.kt
          JsonRouteStore.kt
        tangent/
          RouteTangent.kt
          RouteTangentCalculator.kt
      ui/record/
        RecordingUiState.kt
        MockRouteRecorder.kt
        RecordViewModel.kt
        RecordScreen.kt
      ui/app/
        BeltForBlindApp.kt
      ui/saved/
        SavedRoutesScreen.kt
        SavedRoutesViewModel.kt
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
getLatestReceivedAccuracy()
isLatestPointAccepted()
getDiscardedPointCount()
getWarmupRemainingSeconds()
getCurrentPoints()
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
- `ui.app`：App 级页面入口和底部导航。
- `ui.record`：记录页、页面状态、ViewModel、Mock 记录器。
- `ui.saved`：已保存路线列表页和复用的路线详情入口。
- `ui.sport`：运动页占位入口。
- `route.tangent`：路线切线方向、最近路段、离路线距离和路线进度的纯算法计算。

## 路线切线算法

当前已新增 `RouteTangentCalculator`，用于从路线点中获取当前位置对应的路线切线信息。

输入：

```text
routePoints: List<RoutePoint>
currentPoint: RoutePoint
```

输出 `RouteTangent`：

```text
segmentStartIndex
segmentEndIndex
projectionRatio
tangentBearingDegrees
distanceToRouteMeters
alongRouteDistanceMeters
totalRouteDistanceMeters
progress
```

语义：

- `segmentStartIndex` / `segmentEndIndex`：当前位置投影到的最近路线线段。
- `projectionRatio`：当前位置在线段上的投影比例，范围 `0.0..1.0`。
- `tangentBearingDegrees`：该线段的切线方向角，正北为 `0°`，顺时针增加。
- `distanceToRouteMeters`：当前位置到路线最近线段的距离。
- `alongRouteDistanceMeters`：当前位置投影点距离路线起点的累计距离。
- `totalRouteDistanceMeters`：路线总长度。
- `progress`：路线进度，范围 `0.0..1.0`。

当前算法不会修改 `RoutePoint` 或 `RouteRecord` 的保存结构，也不会直接驱动导航、蓝牙或震动电机。

## 当前可以测试

当前可以通过 UI 测试以下流程：

- 进入 App 后看到地图风格的记录页面。
- 初始状态显示地图、当前位置定位蓝点、地图定位按钮和缩小后的 GO 按钮。
- 首次进入记录页时触发精确位置权限申请；仅授予“大致位置”不满足 GPS 路线精度要求，授权后只启用地图定位，不会自动开始路线记录。
- 授权后开始记录，GO 按钮变为 STOP。
- App 当前通过高德定位 SDK 接收定位点，定位模式为高精度并优先 GPS。
- 定位请求间隔为 3 秒，避免定位点刷新过快。
- 开始记录后先进行 15 秒 GPS 预热，预热阶段收到的点只用于诊断，不保存。
- 定位点会经过精度过滤：`accuracy == null` 或 `accuracy > 8m` 的点会被丢弃。
- 点击 GO 后，按钮变为 STOP，记录页会通过 50% 透明度悬浮面板显示已记录点数。
- 定位请求会等待更准确的初始位置，减少刚开始记录时的低精度点。
- 记录页地图通过高德 2D 定位图层持续显示当前位置；记录开始后还会根据当前全部有效定位点实时绘制轨迹。
- 点击 STOP 后会先询问是否保存；选择“是”后显示路线名称输入和保存按钮，选择“否”后放弃本次记录并回到初始记录界面。
- 输入路线名称后可以点击“保存路线”。
- 保存成功后记录页会自动回到初始状态。
- 保存成功后可以通过底部“已保存”入口独立查看本地路线列表。
- 在已保存路线列表中长按路线，可在二次确认后删除对应的本地 JSON 路线文件。
- 点击已保存路线后进入详情页，可查看高德 2D 路线地图、定位点数量、平均定位精度和路线末端切线方向。
- 地图详情会显示起点、终点、路径连线，并用橙色箭头显示路线末端切线方向；独立的 Canvas 路径点预览和定位点详情列表已移除。
- 可以通过 `RouteTangentCalculator.getTangent(routePoints, currentPoint)` 获取当前位置对应的最近路线线段和切线方向。

这些测试依赖设备或模拟器可用定位。路线文件保存到 `filesDir/routes/`，App 重启后仍可读取。
地图预览使用高德原生 2D `MapView`，依赖设备网络和已配置的高德 Key。

## 尚未实现

以下内容仍为 TODO，后续继续实现：

- 针对记录状态、点过滤、保存读取行为的基础测试。
- 针对路线切线计算的单元测试。
- 更强的轨迹质量控制，例如速度跳变过滤、距离跳变过滤、平滑处理。

## 建议下一步

1. 增加单元测试，覆盖点过滤、预热丢弃、保存读取、记录状态和切线计算。
2. 在真机或模拟器上验证定位权限、开始记录、停止记录、点数和精度显示。
3. 根据实测数据决定是否加入速度跳变过滤、距离跳变过滤或平滑处理。
