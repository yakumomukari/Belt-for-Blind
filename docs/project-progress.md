# 项目进度说明

## 项目概述

本项目是一个面向盲人跑步辅助腰带的 Android/Kotlin App。当前阶段只关注路线记录的底层能力：定位点采集、路线数据维护、本地 JSON 保存，以及读取已保存路线。

当前阶段不做以下功能：

- 地图显示
- 导航
- 蓝牙连接
- 震动电机控制
- 路线切线计算
- 路线匹配
- 腰带方向识别
- 后台长期运行
- 云同步

## 当前仓库状态

仓库目前已经搭建了最小 Android/Kotlin 项目框架，并已合入一版基于 Jetpack Compose 的 UI Mock 页面。当前 UI 可以用于手动测试“开始记录、停止记录、输入路线名、保存路线、查看已保存路线”的前端流程，但底层仍然使用 Mock 数据，没有实现真实 GPS 监听、真实权限弹窗、过滤规则、JSON 序列化或文件读写。

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
          FusedLocationDataSource.kt
        storage/
          RouteStore.kt
          JsonRouteStore.kt
      ui/record/
        RecordingUiState.kt
        MockRouteRecorder.kt
        RecordViewModel.kt
        RecordScreen.kt
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
- `ui.record`：Compose 测试页面、页面状态、ViewModel、Mock 记录器。

## 当前可以测试

当前可以通过 UI Mock 测试以下流程：

- 进入 App 后看到记录页面。
- 点击“开始记录”后进入“记录中”状态。
- 记录中点数会按 Mock 逻辑递增。
- 点击“停止记录”后进入等待保存状态。
- 输入路线名称后可以点击“保存路线”。
- 保存成功后可以点击“查看已保存路线”，看到 Mock 路线列表。

这些测试不依赖真实 GPS，不会生成本地 JSON 文件。

## 尚未实现

以下内容仍为 TODO，后续继续实现：

- 运行时定位权限申请与检查流程。
- `FusedLocationProviderClient` 接入。
- 开始/停止定位更新。
- 当前路线 `rawPoints` 列表维护。
- 精度过滤规则：当 `accuracy != null && accuracy > 20m` 时丢弃该点。
- 停止记录后生成路线对象。
- JSON 序列化与反序列化。
- 保存路线文件到 `filesDir/routes/`。
- 读取已保存路线 JSON 文件。
- 针对记录状态、点过滤、保存读取行为的基础测试。

## 建议下一步

1. 添加 Google Play Services Location 与 JSON 序列化依赖。
2. 使用 `FusedLocationProviderClient` 实现 `FusedLocationDataSource`。
3. 通过 `LocationPermissionGateway` 实现运行时权限处理。
4. 实现 `RouteRecordingManager` 的记录状态管理和精度过滤。
5. 实现 `JsonRouteStore`，按“一条路线一个 JSON 文件”的方式保存。
6. 增加单元测试，覆盖点过滤、保存读取和记录状态。
