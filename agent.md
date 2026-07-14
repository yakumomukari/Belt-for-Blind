# Agent.md：盲人跑步辅助腰带 App 当前阶段开发说明

## 1. 项目定位

本项目是一个面向盲人跑步辅助腰带的 Android/Kotlin App。

当前阶段重点不是完整导航，也不是腰带硬件控制，而是先把“路线记录能力”做稳定：

- 采集定位点
- 维护路线数据
- 本地 JSON 保存
- 读取已保存路线
- 记录页地图展示
- 已保存路线列表和详情查看

当前已完成路线切线的纯算法计算和保存详情页可视化；后续再继续做路线匹配、腰带方向识别、蓝牙通信和震动电机控制。

---

## 2. 当前技术栈

- 平台：Android
- 语言：Kotlin
- UI：Jetpack Compose
- 地图：高德 2D 地图
- 定位：高德定位 SDK
- 路线保存：App 私有目录 JSON 文件
- 当前包名根目录：`com.beltforblind`

当前定位实现不是 `FusedLocationProviderClient`，而是项目内的 `AMapLocationDataSource`。

---

## 3. 当前已经实现的页面

App 当前包含三个主入口：

```text
记录
运动
已保存
```

### 3.1 记录页

记录页是当前核心页面。

已实现行为：

- 以高德 2D 地图作为主视觉。
- 初始状态显示当前位置定位蓝点、地图定位按钮和缩小后的 `GO` 按钮。
- 首次进入记录页时触发精确位置权限申请，仅授予“大致位置”视为未满足要求；授权后只启用地图定位。
- 点击 `GO` 后开始路线记录；如果此前未授权，会再次请求定位权限。
- 授权后开始记录路线。
- 开始记录后按钮变为 `STOP`。
- 记录中显示 50% 透明度的点数浮层。
- 地图实时绘制当前有效路径点和轨迹。
- 点击 `STOP` 后弹出是否保存确认。
- 选择“是”后进入路线命名保存界面。
- 选择“否”后放弃本次记录并回到初始界面。
- 保存成功后自动回到初始状态。

### 3.2 运动页

当前只是占位页面。

不要在本阶段实现运动导航、震动反馈或腰带通信。

### 3.3 已保存页

已保存页用于查看本地路线列表。

已实现行为：

- 独立查看本地已保存路线。
- 点击路线后进入详情页。
- 长按路线并确认后删除本地保存路线。
- 详情页显示高德 2D 地图预览。
- 详情页显示路线的平均定位精度。
- 地图预览显示起点、终点、路径连线和橙色末端切线箭头。
- 地图视野会尽量放大到路径点范围。

---

## 4. 当前项目结构

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

不要随便重排目录。新增功能优先放进现有分层。

---

## 5. 模块边界

### 5.1 `route.model`

只放数据对象。

当前核心数据对象：

- `RoutePoint`
- `RouteRecord`

不要在 model 层写定位、存储、UI 或地图逻辑。

### 5.2 `route.recorder`

负责路线记录能力对外接口和协调管理。

当前核心文件：

- `RouteRecorder.kt`
- `RouteRecordingManager.kt`

UI 层应通过 `RouteRecorder` 使用路线记录能力，不应该直接接触 GPS API 或文件系统。

### 5.3 `route.location`

负责定位权限和定位数据源边界。

当前核心文件：

- `LocationPermissionGateway.kt`
- `LocationDataSource.kt`
- `AMapLocationDataSource.kt`
- `AMapMapLocationSource.kt`

定位 SDK 相关代码只应该收敛在这一层。

### 5.4 `route.storage`

负责路线持久化。

当前核心文件：

- `RouteStore.kt`
- `JsonRouteStore.kt`

路线保存位置：

```text
filesDir/routes/
```

每条路线保存为 JSON 文件。App 重启后仍应可读取。

### 5.5 `ui.app`

负责 App 级页面入口和底部导航。

当前核心文件：

- `BeltForBlindApp.kt`

### 5.6 `ui.record`

负责记录页、记录状态、ViewModel 和 Mock 记录器。

当前核心文件：

- `RecordingUiState.kt`
- `MockRouteRecorder.kt`
- `RecordViewModel.kt`
- `RecordScreen.kt`

### 5.7 `ui.saved`

负责已保存路线列表和路线详情入口。

当前核心文件：

- `SavedRoutesScreen.kt`
- `SavedRoutesViewModel.kt`

### 5.8 `ui.sport`

当前只是运动页占位入口。

不要在本阶段往这里塞复杂运动逻辑。

---

## 6. 当前数据模型

### 6.1 RoutePoint

定位点必需字段：

```text
latitude
longitude
timestamp
accuracy
```

含义：

- `latitude`：纬度
- `longitude`：经度
- `timestamp`：定位时间
- `accuracy`：定位精度，单位米

### 6.2 RouteRecord

路线必需字段：

```text
id
name
createdAt
points
```

含义：

- `id`：路线唯一 ID
- `name`：路线名称
- `createdAt`：创建时间
- `points`：路线定位点列表

不要在当前阶段强行加入切线、路线匹配、运动状态、电机状态等字段。

---

## 7. RouteRecorder 对外接口

UI 层后续应通过 `RouteRecorder` 使用路线记录能力。

当前对外接口包括：

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
deleteRoute(routeId)
```

接口语义：

- `startRecord()`：开始记录。
- `stopRecord()`：停止记录。
- `getPointCount()`：获取当前有效记录点数量。
- `getLatestAccuracy()`：获取最近一次有效点的精度。
- `getLatestReceivedAccuracy()`：获取最近一次收到的定位精度，不管该点是否被保存。
- `isLatestPointAccepted()`：判断最近一次收到的点是否被接收。
- `getDiscardedPointCount()`：获取被过滤丢弃的点数量。
- `getWarmupRemainingSeconds()`：获取 GPS 预热剩余时间。
- `getCurrentPoints()`：获取当前有效路线点。
- `saveRoute(name)`：保存当前路线。
- `loadRoutes()`：读取已保存路线。
- `deleteRoute(routeId)`：删除指定的本地保存路线。

UI 层不要直接调用高德定位 SDK，也不要直接读写路线 JSON。

---

## 8. 当前定位与过滤策略

当前定位数据来源：

```text
AMapLocationDataSource
```

当前定位模式：

```text
高精度定位
优先 GPS
定位请求间隔 3 秒
```

当前记录策略：

- 点击 `GO` 后开始请求定位。
- 开始记录后先进行 15 秒 GPS 预热。
- 预热阶段收到的点只用于诊断，不保存。
- 定位请求会等待更准确的初始位置，减少刚开始记录时的低精度点。
- 预热结束后才开始保存有效点。

当前过滤规则：

```text
accuracy == null：丢弃
accuracy > 8m：丢弃
```

不要改回旧的 20m 过滤阈值，除非有实测理由。

---

## 9. 当前可以测试的流程

当前可以通过 UI 测试：

```text
1. 打开 App，进入记录页面。
2. 首次使用时授权定位权限。
3. 确认地图显示当前位置蓝点、定位按钮和 GO 按钮。
4. 点击 GO。
5. 等待 GPS 预热。
6. 观察按钮变为 STOP。
7. 观察点数浮层。
8. 观察地图实时绘制轨迹。
9. 点击 STOP。
10. 在保存确认弹窗中选择“是”。
11. 输入路线名称。
12. 点击保存路线。
13. 确认保存成功后回到初始记录界面。
14. 进入底部“已保存”页面。
15. 查看刚保存的路线。
16. 点击路线进入详情页。
17. 确认地图路线、平均定位精度和橙色末端切线箭头正常。
18. 重启 App 后再次进入“已保存”，确认路线仍可读取。
```

测试依赖：

- 设备或模拟器能获取定位。
- 高德 Key 已正确配置。
- 设备网络可用。
- 高德原生 2D `MapView` 可正常加载。

---

## 10. 当前禁止做的内容

本阶段不要实现：

```text
导航
蓝牙连接
震动电机控制
基于切线的导航引导
路线匹配
腰带方向识别
后台长期运行
云同步
复杂运动模式
```

地图显示已经存在，但不要继续扩展成完整导航软件。

当前地图只服务于：

```text
记录时显示当前轨迹
查看已保存路线预览
```

---

## 11. 当前 TODO

优先级从高到低：

### 11.1 增加基础测试

需要覆盖：

```text
记录状态变化
GPS 预热丢弃
accuracy 过滤
保存路线
读取路线
放弃路线
空路线保存保护
```

### 11.2 真机或模拟器验证

需要验证：

```text
定位权限申请
GO / STOP 流程
15 秒预热显示
点数显示
accuracy 显示
地图轨迹绘制
保存 JSON
重启后读取路线
```

### 11.3 轨迹质量控制

根据实测数据决定是否加入：

```text
速度跳变过滤
距离跳变过滤
路线平滑
重复点过滤
最小移动距离过滤
```

暂时不要盲目加复杂算法。先用真实数据看问题。

---

## 12. 开发原则

1. 不要让 UI 直接调用定位 SDK。
2. 不要让 UI 直接读写 JSON 文件。
3. 不要把地图逻辑和路线记录逻辑搅在一起。
4. 不要在 model 层写业务逻辑。
5. 不要为了炫技提前做蓝牙、导航和电机。
6. 新增功能必须优先考虑能不能被测试。
7. 涉及定位过滤的阈值必须能解释，最好来自实测。
8. 保存失败、权限拒绝、定位不可用都必须有 UI 状态反馈。
9. Mock 记录器继续保留，方便没有定位环境时调 UI。
10. 当前阶段只保证路线记录闭环稳定。
