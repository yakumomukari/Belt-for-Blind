# Agent.md：盲人跑步辅助腰带 App 六页面统一 UI 重构

## 1. 角色

你是本项目的 Android / Jetpack Compose UI 开发 Agent。

本次任务是在不破坏现有定位、路线记录、本地 JSON 保存、高德地图、Debug 虚拟 GPS 和 BLE 测试功能的前提下，将以下 6 个页面重构为同一套朴素、清晰、不过度装饰的视觉风格：

```text
记录（未开始）
记录（开始）
运动（未开始）
运动（开始）
已保存（主页）
已保存（详细路线）
```

本次重点是：

1. 统一颜色、字号、间距、圆角、图标和底部导航。
2. 按参考 UI 重构页面信息层级。
3. 复用现有业务状态，不伪造定位、路线或运动数据。
4. 保证高德地图生命周期、定位监听和路线覆盖物不被 UI 重构破坏。
5. 保证页面适合户外使用，并满足基础无障碍要求。

---

## 2. 项目背景

这是一个面向视障跑者的辅助跑步 App。

当前技术栈：

```text
平台：Android
语言：Kotlin
UI：Jetpack Compose
地图：高德 2D 地图
定位：高德定位 SDK
定位实现：AMapLocationDataSource
路线保存：App 私有目录 JSON
包名根目录：com.beltforblind
```

当前已经具备：

- 精确位置权限申请。
- 高德地图和当前位置显示。
- 路线记录。
- 15 秒 GPS 预热。
- 定位精度过滤。
- 路线轨迹实时绘制。
- 本地 JSON 保存、读取和删除。
- 已保存路线详情预览。
- 平均定位精度显示。
- 末端切线方向计算与可视化。
- Debug 虚拟 GPS。
- Debug BLE 腰带连接和单电机测试。

本次不重写定位算法、路线算法、电机协议、JSON 格式和底层通信。

---

## 3. 当前项目结构

优先沿用现有结构：

```text
app/src/main/java/com/beltforblind/
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
      LocationSimulationGateway.kt
    storage/
      RouteStore.kt
      JsonRouteStore.kt
  ui/
    app/
      BeltForBlindApp.kt
    record/
      RecordingUiState.kt
      RecordViewModel.kt
      RecordScreen.kt
    sport/
      SportScreen.kt
      DebugGpsScreen.kt
    saved/
      SavedRoutesScreen.kt
      SavedRoutesViewModel.kt
```

允许在 `ui/` 下新增少量共享组件和主题文件，例如：

```text
ui/theme/
  BeltColors.kt
  BeltTypography.kt
  BeltTheme.kt

ui/components/
  AppBottomBar.kt
  AppHeaderCard.kt
  StatusChip.kt
  PrimaryActionButton.kt
  MetricCard.kt
  RouteSummaryCard.kt
  MapOverlayCard.kt
```

不要为了 UI 重构大规模重排目录。

---

## 4. 开始工作前

先读取并确认：

- `BeltForBlindApp.kt`
- `RecordScreen.kt`
- `RecordingUiState.kt`
- `RecordViewModel.kt`
- `SportScreen.kt`
- `SavedRoutesScreen.kt`
- `SavedRoutesViewModel.kt`
- 高德 `MapView` 封装和生命周期代码
- 当前颜色、字体、字符串资源
- `RouteRecord` 和 `RoutePoint`

只处理与这 6 个页面直接相关的文件。

不要扫描或修改：

```text
build/
.gradle/
缓存目录
无关模块
电机协议实现
定位过滤算法
JSON 数据格式
```

---

## 5. 视觉基准

### 5.1 总体风格

关键词：

```text
朴素
清晰
轻量
统一
低装饰
高可读性
```

禁止：

- 大面积玻璃拟态。
- 多层发光。
- 过强渐变。
- 复杂背景纹理。
- 同屏堆叠大量卡片。
- 为了“好看”牺牲地图可视面积。
- 每个页面使用不同风格。

### 5.2 颜色

所有颜色集中定义，不要散落硬编码。

建议初始颜色：

```text
PrimaryPurple       #7144C7
PrimaryPurpleDark   #5932A8
PurpleContainer     #F1EAFB
Background          #FAF8FC
Surface             #FFFFFF
TextPrimary         #202028
TextSecondary       #74717D
Divider             #ECE8F0
Success             #2FA85F
Warning             #D89722
Error               #D64A55
Disabled             #C8C4CC
```

紫色只用于：

- 当前选中导航项。
- 主按钮。
- 页面核心图标。
- 路线线条。
- 可点击强调文字。

绿色只用于：

- GPS 正常。
- 腰带已连接。
- 明确成功状态。

颜色不能作为唯一状态提示，必须配合文字。

### 5.3 间距与圆角

使用统一 8dp 网格：

```text
页面水平边距：20dp
组件间距：8dp / 12dp / 16dp / 24dp
普通卡片圆角：20dp
小型胶囊圆角：50%
主按钮圆角：24dp 或圆形
底部导航圆角：24dp
```

阴影只使用一层轻阴影，避免悬浮感过强。

### 5.4 字体层级

```text
页面标题：28sp，SemiBold
卡片标题：20sp，SemiBold
核心数字：40sp 至 56sp，Bold
普通数字：24sp 至 32sp，Medium
正文：16sp
辅助文字：13sp 至 14sp
底部导航：14sp
```

必须支持系统字体缩放，不要依赖固定高度裁切文字。

### 5.5 图标

- 优先使用 Material Icons 或项目已有矢量图标。
- 同类操作使用同一套线性图标。
- 不允许使用截图作为图标。
- 所有图标按钮必须设置 `contentDescription`。

---

## 6. 共用页面结构

六个页面应共享以下基础组件：

### 6.1 顶部状态栏

使用系统状态栏，不在 Compose 内重复绘制假的系统状态栏。

### 6.2 底部导航

固定三个入口：

```text
记录
运动
已保存
```

要求：

- 当前项使用浅紫背景、紫色图标和紫色文字。
- 未选中项使用灰色。
- 整体为白色圆角容器。
- 最小触控区域 48dp。
- 页面切换后保留各页面必要状态。
- Debug 连点进入测试工具的现有逻辑必须保留。

### 6.3 地图

记录页和运动页使用同一套地图容器样式：

- 地图占据页面主要空间。
- 地图颜色由高德地图本身提供，不叠加强烈滤镜。
- 卡片覆盖地图时保持足够透明区域和地图可读面积。
- 当前定位标记、轨迹和路线使用紫色体系。
- 定位精度圆使用低透明度紫色或蓝紫色。
- 地图控件样式统一。

### 6.4 状态胶囊

统一使用 `StatusChip`：

```text
GPS 待定位
GPS 预热中
GPS 采集中
GPS 已就绪
GPS 不可用
腰带已连接
腰带未连接
```

每个状态包含：

- 图标。
- 文字。
- 必要时使用状态色。

不要只显示一个彩色圆点。

---

## 7. 页面状态管理

UI 只渲染状态并发送事件。

### 7.1 记录页状态

至少整理为：

```text
Idle
RequestingPermission
WarmingUp
Recording
SaveConfirm
NamingRoute
Saving
Error
```

不要同时维护互相冲突的：

```text
isRecording
showSaveDialog
showNameDialog
isWarmingUp
```

优先使用 `sealed interface` 或枚举表达主状态。

记录页独立数据：

```text
pointCount
latestAccuracy
latestReceivedAccuracy
isLatestPointAccepted
discardedPointCount
warmupRemainingSeconds
currentPoints
distanceMeters
gpsState
beltConnectionState
```

### 7.2 运动页状态

本阶段至少支持：

```text
Idle
Active
Unavailable
Error
```

可预留：

```text
Paused
Finished
```

运动页独立数据：

```text
selectedRoute
currentLocation
elapsedTime
distanceMeters
averageSpeed
routeProgress
gpsState
beltConnectionState
```

正式路线匹配、方向识别和自动震动未接入时，不得伪装为已完成。

### 7.3 已保存页状态

至少支持：

```text
Loading
Content
Empty
Error
Detail
```

---

## 8. 页面一：记录（未开始）

对应底部导航：`记录` 选中。

### 8.1 页面结构

从上到下：

1. 顶部标题卡。
2. 地图主体。
3. 未开始状态卡。
4. 开始记录主按钮。
5. 两个简要统计项。
6. 底部导航。

### 8.2 顶部标题卡

内容：

```text
标题：路线记录
副标题：记录采样点与轨迹
GPS 状态
腰带连接状态
```

GPS 状态必须来自真实状态。

### 8.3 地图

- 进入页面后显示当前位置。
- 首次有效定位时自动居中一次。
- 后续定位更新不得反复重置缩放。
- 用户手动移动地图后停止自动跟随。
- 点击定位按钮恢复跟随。
- 未授权时显示权限提示，不隐藏整个地图区域。

### 8.4 未开始状态卡

显示：

```text
路线记录尚未开始
请先获取有效定位
```

如果已经获得可用定位，辅助文字改为：

```text
定位已就绪，可以开始记录
```

### 8.5 开始记录按钮

主按钮：

```text
开始记录
```

启用条件沿用当前业务逻辑：

- 已获得精确定位权限。
- 定位数据源可用。

未满足条件时显示禁用态，并给出明确原因。

### 8.6 简要统计

只显示：

```text
已保存点数 0
距离 0 m
```

不要添加无意义数据。

---

## 9. 页面二：记录（开始）

对应底部导航：`记录` 选中。

### 9.1 顶部标题卡

显示：

```text
路线记录中
GPS 预热中 / GPS 采集中
腰带已连接 / 腰带未连接
```

腰带状态只用于展示，不得改变现有路线记录逻辑，除非项目原本已有相关规则。

### 9.2 地图

显示：

- 当前位置。
- 已接收并通过过滤的路线点。
- 当前轨迹 Polyline。
- 定位精度范围。

预热阶段：

- 地图可更新当前位置。
- 不绘制为已保存路线点。
- UI 明确显示剩余预热时间。

### 9.3 底部数据面板

使用白色圆角面板，不使用复杂深色玻璃效果。

显示：

```text
总距离
定位状态
记录时间
当前精度
已保存点数
已丢弃点数
```

核心数字优先显示总距离。

数据必须来自真实接口：

```text
getPointCount()
getLatestAccuracy()
getLatestReceivedAccuracy()
isLatestPointAccepted()
getDiscardedPointCount()
getWarmupRemainingSeconds()
getCurrentPoints()
```

### 9.4 主操作按钮兼容规则

参考 UI 中按钮文案为“暂停”，但当前项目已确认的接口只有：

```text
startRecord()
stopRecord()
```

因此：

- 如果现有状态层没有真正暂停与恢复能力，按钮必须显示“停止记录”，并继续使用现有保存确认流程。
- 只有确认底层已经支持暂停与恢复后，才可以显示“暂停”。
- 不允许按钮写“暂停”，实际却直接结束记录。

停止记录后继续沿用：

```text
停止记录
询问是否保存
输入路线名称
保存或放弃
返回未开始状态
```

---

## 10. 页面三：运动（未开始）

对应底部导航：`运动` 选中。

### 10.1 页面结构

从上到下：

1. 顶部标题卡。
2. 地图主体。
3. 当前路线卡。
4. 开始按钮。
5. 状态辅助文字。
6. 底部导航。

### 10.2 顶部标题卡

内容：

```text
标题：户外跑步
副标题：安全陪伴 · 科学运动
GPS 状态
腰带连接状态
```

### 10.3 当前路线卡

显示：

```text
路线名称
test2
路线长度
选择路线
```

实际数据要求：

- 路线名称来自 `RouteRecord.name`。
- 路线长度根据真实点计算，未实现时显示 `--`，不得写假值。
- 点击“选择路线”打开已保存路线选择页面。
- 选择后返回运动页并保留路线 ID。
- 地图绘制完整路线。

没有路线时显示：

```text
尚未选择路线
去选择路线
```

### 10.4 开始按钮

显示：

```text
开始
GO
```

启用条件：

- 已选择有效路线。
- 已获得精确定位权限。
- 当前有有效定位。

腰带是否必须连接，严格沿用现有产品逻辑，不擅自决定。

### 10.5 当前阶段边界

本页面可以完成：

- UI。
- 路线选择。
- 路线绘制。
- 当前位置显示。
- 基础计时和距离展示入口。

本页面不得擅自实现：

- 正式路线匹配。
- 腰带方向判断。
- 自动震动导航。
- 偏航纠正。
- 后台导航。

---

## 11. 页面四：运动（开始）

对应底部导航：`运动` 选中。

### 11.1 地图

显示：

- 完整目标路线。
- 当前定位。
- 起点和终点。
- 当前已走轨迹。

如果尚未实现真实路线进度匹配：

- 可以显示目标路线和当前定位。
- 不得伪造“已完成路段”。
- 不得显示虚假的导航方向。

### 11.2 顶部标题卡

显示：

```text
户外跑步
GPS 进行中
腰带连接状态
```

### 11.3 路线状态卡

显示：

```text
路线名称
test2
路线引导中
```

只有真实导航状态接入后才使用“路线引导中”。

如果当前只有路线展示，应改为：

```text
路线已加载
```

### 11.4 底部数据面板

只保留三项核心数据：

```text
距离
用时
平均速度
```

如果某项尚未接入真实数据，显示：

```text
--
```

不得硬编码示例数值进入正式运行页面。

### 11.5 主操作按钮

如果运动流程已经支持暂停：

```text
暂停
```

如果当前只支持结束：

```text
结束运动
```

按钮语义必须与实际行为一致。

危险结束操作应二次确认，避免误触。

---

## 12. 页面五：已保存（主页）

对应底部导航：`已保存` 选中。

### 12.1 顶部区域

显示：

```text
已保存路线
查看、管理你保存的路线
刷新
```

“刷新”调用现有 `loadRoutes()`，不要创建新的存储入口。

### 12.2 路线卡片

每张卡只显示：

```text
路线缩略图
路线名称
路线点数
保存时间
进入详情箭头
```

保留现有交互：

- 点击进入详情。
- 长按后确认删除。

也可以将删除入口放到溢出菜单，但不得直接单击删除。

### 12.3 缩略图性能规则

禁止在列表每一项中创建一个高德 `MapView`。

优先方案：

1. 使用 Compose `Canvas` 将路线坐标归一化后绘制为简化缩略图。
2. 或使用已经缓存的地图截图。
3. 列表滚动时不得反复初始化地图 SDK。

缩略图只表达路线形状，不要求精确地图交互。

### 12.4 空状态

没有路线时显示：

```text
还没有保存路线
先去记录一条路线
```

提供跳转到“记录”页的按钮。

不得展示假路线。

---

## 13. 页面六：已保存（详细路线）

默认保持 `已保存` 为当前业务入口。

### 13.1 顶部区域

显示：

```text
返回
路线名称
```

返回必须回到原路线列表滚动位置。

### 13.2 路线信息卡

显示：

```text
路线 ID
创建时间
定位点数量
平均定位精度
末端切线方向
```

数据来自真实 `RouteRecord` 和现有计算结果。

不要修改现有 JSON 数据格式来适配 UI。

### 13.3 地图预览卡

显示：

- 路线 Polyline。
- 起点。
- 终点。
- 末端方向箭头。
- 合理的路线视野范围。

地图初始化完成后只执行一次视野适配。

用户手动缩放后，不得因状态重组重复重置地图。

### 13.4 底部导航

如果当前导航结构允许详情页保留底部导航，则保持 `已保存` 选中。

如果当前详情页是独立导航层级并默认隐藏底部栏，不要为了截图强行重构整个导航架构。优先保证返回逻辑和页面稳定。

---

## 14. 共享组件要求

### 14.1 `AppHeaderCard`

参数建议：

```text
title
subtitle
leadingIcon
statusChips
```

不得在每个页面复制一套近似代码。

### 14.2 `StatusChip`

参数建议：

```text
icon
text
statusType
contentDescription
```

### 14.3 `AppBottomBar`

参数建议：

```text
selectedTab
onRecordClick
onSportClick
onSavedClick
```

保留现有 Debug 连点入口逻辑。

### 14.4 `PrimaryActionButton`

支持：

```text
圆形或大圆角矩形
启用态
禁用态
加载态
主标题
可选副标题
```

### 14.5 `MetricCard`

参数建议：

```text
label
value
unit
icon
statusColor
```

只承载一个指标，不做多余装饰。

---

## 15. 架构要求

### 15.1 UI 层

UI 只负责：

- 渲染状态。
- 发送事件。
- 管理纯视觉状态。

UI 不得直接：

- 调用高德定位 SDK。
- 读写 JSON。
- 控制 BLE 电机。
- 计算路线切线。
- 执行业务过滤。

### 15.2 ViewModel

ViewModel 负责：

- 聚合状态。
- 调用现有业务接口。
- 暴露不可变 UI State。
- 处理页面事件。

### 15.3 建议事件

记录页：

```text
RequestLocationPermission
RecenterMap
StartRecording
StopRecording
ConfirmSave
DiscardRecording
SubmitRouteName
DismissError
```

运动页：

```text
OpenRoutePicker
SelectRoute
RecenterMap
StartSport
PauseSport
ResumeSport
StopSport
DismissError
```

已保存页：

```text
RefreshRoutes
OpenRouteDetail
RequestDeleteRoute
ConfirmDeleteRoute
CancelDeleteRoute
BackToList
```

---

## 16. 高德地图生命周期与性能

必须遵守：

- `MapView` 使用 `remember` 保持实例。
- 使用 `DisposableEffect` 绑定生命周期。
- 页面销毁时正确执行 `onDestroy()`。
- 页面切后台和恢复时正确执行 `onPause()` / `onResume()`。
- 定位更新只更新当前位置 Marker 和必要 Polyline。
- 路线变更时才重建路线覆盖物。
- 不在每次 Compose 重组时执行 `clear()`。
- 不在每次定位回调时创建完整 Polyline。
- 不在每次定位回调时强制调整缩放。
- 避免重复注册定位监听。
- 避免重复 Marker 和重复 Polyline。

---

## 17. 无障碍与户外可读性

- 主要触控区域至少 48dp。
- 开始、停止、暂停按钮建议高度或直径不少于 72dp。
- 所有图标按钮设置 `contentDescription`。
- GPS 状态必须可被 TalkBack 朗读。
- 腰带连接状态必须可被 TalkBack 朗读。
- 数值与单位组合朗读，例如“当前精度 3 米”。
- 不仅靠颜色传达成功、警告和错误。
- 文字与背景保持足够对比度。
- 户外核心信息不能使用过细字体。
- 系统字体放大后页面不能重叠或裁切。

---

## 18. 错误状态

六个页面都必须处理：

```text
精确定位权限被拒绝
只授予大致位置
GPS 不可用
定位暂未返回
路线为空
路线文件损坏
保存失败
删除失败
高德地图加载失败
腰带断连
```

使用简洁文本提示，不要用满屏弹窗阻塞地图。

危险操作才使用确认弹窗：

```text
停止并放弃路线
删除路线
结束运动
```

---

## 19. 不允许做的事情

- 不替换高德地图。
- 不重写定位 SDK 封装。
- 不修改当前 15 秒 GPS 预热规则。
- 不擅自修改 `accuracy > 8m` 的过滤规则。
- 不修改路线 JSON 字段格式。
- 不删除 Debug 虚拟 GPS。
- 不删除 Debug BLE 测试入口。
- 不在列表项内创建多个地图实例。
- 不使用假 GPS、假距离、假速度和假路线填充正式 UI。
- 不把所有页面写进一个 Composable。
- 不为了 6 张设计图复制 6 套重复组件。
- 不引入大型第三方 UI 框架。
- 不进行无关架构重构。
- 不在按钮文案和真实行为之间制造语义冲突。

---

## 20. 实施顺序

一次只完成一个阶段。完成后先报告结果，不自动扩展下一阶段。

### 阶段 1：主题和共享组件

- 建立统一颜色、字体、圆角、间距。
- 完成顶部标题卡。
- 完成状态胶囊。
- 完成底部导航。
- 完成主按钮和指标卡。

### 阶段 2：记录页

- 重构记录未开始页面。
- 重构记录开始页面。
- 接入现有预热、点数、精度和轨迹状态。
- 保留原保存和放弃流程。

### 阶段 3：运动页

- 重构运动未开始页面。
- 完成路线选择和地图绘制。
- 重构运动开始页面。
- 只接入已有真实状态，不伪造导航能力。

### 阶段 4：已保存页

- 重构路线列表。
- 完成轻量路线缩略图。
- 重构路线详情页。
- 保留删除、返回和地图预览逻辑。

### 阶段 5：回归与收尾

- 真机检查地图生命周期。
- 检查底部导航和返回栈。
- 检查定位权限。
- 检查字体缩放。
- 检查 TalkBack。
- 检查 Debug 虚拟 GPS 和 BLE 入口。
- 检查路线保存、读取、删除和重启恢复。

---

## 21. 验收标准

### 21.1 视觉一致性

1. 六个页面使用同一紫色、背景色和文字层级。
2. 顶部卡片、状态胶囊、主按钮、指标卡和底部导航风格统一。
3. 页面不存在过度渐变、过强阴影或多余装饰。
4. 地图始终保留足够可视区域。
5. 页面在常见 Android 屏幕比例下不溢出。

### 21.2 记录页

1. 未开始页面能显示地图和定位状态。
2. 开始记录后显示真实轨迹。
3. 15 秒预热状态正常显示。
4. 已保存点数和丢弃点数来自真实状态。
5. 停止后仍能保存、命名或放弃。
6. UI 重构不改变定位过滤结果。

### 21.3 运动页

1. 可以选择一条已保存路线。
2. 选择后地图显示真实路线。
3. 开始按钮只有在条件满足时可用。
4. 运动开始页面只展示真实数据。
5. 未实现的指标显示 `--`，不使用假数据。
6. 未接入的导航和震动能力不会被伪装为可用。

### 21.4 已保存页

1. 路线列表可以正常加载。
2. 路线卡显示真实名称、点数和时间。
3. 列表缩略图不会创建多个地图实例。
4. 点击可以进入详情。
5. 长按删除仍可用。
6. 详情页显示真实路线信息和末端方向。
7. 地图视野不会因重组反复重置。

### 21.5 性能和稳定性

1. 无重复定位监听。
2. 无重复 Marker。
3. 无重复 Polyline。
4. 页面切换无明显掉帧。
5. App 切后台和恢复后地图正常。
6. App 重启后保存路线仍可读取。
7. Debug 虚拟 GPS 和 BLE 测试入口仍然可用。

---

## 22. 每阶段输出格式

完成每个阶段后只输出：

```text
本阶段完成内容
修改文件列表
关键实现说明
已验证项目
当前问题
下一阶段建议
```

不要长篇讲解基础 Android 知识。

不要在未经确认时继续扩大修改范围。
