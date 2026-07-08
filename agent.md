# Agent A：定位采集与路线保存负责人

## 当前目标

你负责实现盲人跑步辅助腰带 App 第一阶段的底层功能：GPS 定位数据采集、路线数据维护、本地 JSON 保存与读取。

本阶段只做路线记录和保存，不做地图、不做蓝牙、不做震动电机、不做路线切线计算、不做导航。

## 技术范围

平台：Android
语言：Kotlin
定位：FusedLocationProviderClient
保存方式：App 私有目录 JSON 文件

## 你需要完成的功能

1. 申请并检查定位权限。
2. 开始 GPS 定位监听。
3. 停止 GPS 定位监听。
4. 每次收到定位后，记录：

   * latitude
   * longitude
   * timestamp
   * accuracy
5. 维护当前路线的 rawPoints 列表。
6. 停止记录后生成路线对象。
7. 将路线保存为本地 JSON 文件。
8. 支持读取本地已保存路线列表。

## 数据要求

每个定位点至少包含：

```text
latitude
longitude
timestamp
accuracy
```

每条路线至少包含：

```text
id
name
createdAt
points
```

## 对外提供接口

你需要给 UI 层提供这些能力：

```text
startRecord()
stopRecord()
getPointCount()
getLatestAccuracy()
saveRoute(name)
loadRoutes()
```

UI 层不应该直接接触 GPS API，也不应该直接操作文件。

## 保存位置

路线保存到：

```text
filesDir/routes/
```

每条路线保存为一个 JSON 文件，例如：

```text
route_1760000000000.json
```

## 简单过滤规则

第一版只做最小过滤：

```text
如果 accuracy 存在，并且 accuracy > 20m，则丢弃该点。
```

其他复杂过滤后续再做。

## 禁止事项

不要实现：

```text
地图显示
路线切线计算
路线匹配
蓝牙连接
震动电机控制
腰带方向识别
后台长期运行
云同步
```

## 交付标准

完成后应满足：

```text
1. 能开始记录定位点
2. 能停止记录
3. 能返回当前记录点数
4. 能返回最近 accuracy
5. 能保存路线 JSON
6. 能读取已保存路线
7. 路线文件重启 App 后仍然存在
```
