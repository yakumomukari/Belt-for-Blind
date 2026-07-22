# 腰带磁力计导航改造计划

本文档记录 `agent_belt_magnetometer_navigation.md` 对应改造的审查结果、实施顺序和待确认硬件信息。后续每完成一个阶段，应在此处和 `project-progress.md` 同步状态。

## 目标

正式记录和运动流程统一使用腰带数据：

- 路线记录位置：仅使用腰带 ATGM336H GPS。
- 跑步导航位置：仅使用腰带 ATGM336H GPS。
- 跑步导航朝向：仅使用腰带磁力计。
- 手机定位和手机旋转矢量不再作为正式流程的回退数据源。
- 保留现有 154 度电机圆弧、双电机插值、3 度死区和告警策略。

## 当前数据流

```text
ESP32 ATGM336H
  -> BLE GPS 特征 0003
  -> AndroidBleMotorController
  -> BeltGpsRepository
  -> BeltPreferredLocationDataSource
       -> 有新鲜腰带数据时使用腰带
       -> 腰带数据失效时回退手机高德定位
  -> SportViewModel
       -> 有新鲜腰带数据时使用腰带
       -> 腰带数据失效时接收 SportMap 的手机位置

手机旋转矢量传感器
  -> AndroidPhoneHeadingProvider
  -> 地磁偏角修正
  -> SportViewModel
  -> 路线切线与方向误差
  -> 154 度电机圆弧插值
  -> BLE 电机命令特征 0002
```

## 目标数据流

```text
ESP32 ATGM336H -----------------> BLE GPS 0003 -----> BeltGpsRepository
ESP32 HMC/QMC 磁力计 -----------> BLE Heading 0004 -> BeltHeadingRepository
                                                           |
                                                           v
                                          安装侧变换 + 分侧校准偏移
                                                           |
                    +----------------------+---------------+
                    |                      |
                    v                      v
              路线记录（仅腰带）      跑步导航（仅腰带）
                                           |
                              路线切线 + 腰带真实航向
                                           |
                          154 度圆弧双电机强度插值
                                           |
                                  BLE 电机命令 0002
```

## 审查结论

### Android

- 应用层已经只创建一个 `BleMotorController`，记录、运动和 Debug 页面共享，可以继续作为唯一 BLE 连接所有者。
- 当前控制器只订阅 GPS 的 CCCD。Android GATT 描述符写入不能并发，GPS 和 Heading 两个通知必须按队列逐个启用。
- `BeltPreferredLocationDataSource` 会启动手机高德定位并在腰带 GPS 失效时回退，不符合目标。
- `SportScreen` 仍创建 `AndroidPhoneHeadingProvider`，并使用手机旋转矢量和地磁偏角。
- `SportViewModel` 同时接收腰带位置与地图回调的手机位置，腰带 GPS 超时后会回退手机。
- 当前开始运动条件没有强制要求 BLE、腰带 GPS、新鲜腰带航向和安装侧全部就绪。
- 项目没有 DataStore 依赖。安装侧和左右侧独立校准偏移计划通过封装后的 SharedPreferences 持久化，避免新增依赖。

### ESP32

- 当前固件已有 BLE 电机命令、GPS Notify、看门狗和独立任务。
- 尚未初始化 I2C，也没有 HMC5883L/QMC5883L 驱动。
- GPIO 21、22 已由第 6、7 个电机占用，不能使用 ESP32 常见默认 I2C 引脚。
- GPIO 1、3 用于串口控制台，GPIO 16、17 用于 GPS UART，也不能占用。
- 磁力计缺失、读取失败或数据无效时必须保持 BLE 和电机功能运行，不允许触发 abort 或重启循环。

## 分阶段实施

### 阶段 1：项目审查

状态：已完成。

- 确认 ESP32、BLE、GPS、手机航向、路线记录和跑步导航的数据流。
- 确认手机定位和手机航向的正式回退路径。
- 确认 I2C 引脚冲突和 BLE 双 CCCD 串行化要求。

### 阶段 2：接口与文件规划

状态：已完成。

计划文件：

- 固件：新增 `belt_motor_test/main/magnetometer.c`、`magnetometer.h`，修改 `main.c` 和 `CMakeLists.txt`。
- BLE：修改 `MotorControlGateway.kt`、`MotorBleProtocol` 和 `AndroidBleMotorController.kt`。
- 数据：新增 `BeltHeadingPacketDecoder.kt`、`BeltHeadingRepository.kt` 及单元测试。
- 航向：新增腰带航向 Provider、安装侧变换与校准存储模块及测试。
- 定位：用腰带专用数据源替换 `BeltPreferredLocationDataSource` 的正式调用。
- 运动：移除 `SportScreen` 的手机方向传感器注册，移除 `SportViewModel` 的手机位置回退。
- UI：按规范补充准备条件、安装侧选择、校准、数据来源和错误状态。
- 安全：补充 1 秒航向超时、断连停振和前后台恢复验证。

### 阶段 3：ESP32 磁力计驱动

状态：等待硬件接线确认。

- 扫描 `0x1E`（HMC5883L）和 `0x0D`（QMC5883L）。
- 两者都响应时优先使用 HMC5883L，并记录检测结果。
- 统一输出原始 XYZ、0 到 360 度航向、芯片类型和有效标志。
- 读取频率约 10 Hz，仅使用 `atan2(y, x)`，暂不加入倾斜补偿和融合。

### 阶段 4：BLE Heading Notify

状态：未开始。

- 新增特征 UUID `00000004-0000-1000-8000-00805f9b34fb`。
- 固定 12 字节、小端序、协议版本 1。
- Android 端串行启用 GPS 和 Heading 通知。

### 阶段 5：Android 解码、安装变换与校准

状态：未开始。

- 解码固定 12 字节包并以单调时钟记录接收时间。
- 左右安装使用同一集中公式，不在 UI 或 ViewModel 分散判断。
- 左右侧校准偏移分别持久化。
- 航向超过 1 秒未更新即失效。

### 阶段 6：正式记录与运动数据源切换

状态：未开始。

- 记录和运动位置只接受腰带 GPS。
- 运动方向只接受腰带磁力计。
- 开始运动必须满足路线、BLE、GPS、航向和安装侧条件。
- 地图仅负责显示腰带位置，不再将地图定位回调作为导航输入。

### 阶段 7：安全、后台与验收

状态：未开始。

- 断连、航向超时和定位超时立即停止持续导航震动。
- 验证暂停、恢复、后台、重连和服务恢复。
- 固件编译、Android 单元测试、Debug APK 和真机联调全部通过后再视为完成。

## 固定协议

Heading Notify 采用以下 12 字节布局：

| 字节 | 内容 |
| --- | --- |
| 0 | 协议版本，固定为 1 |
| 1 | flags：bit0 valid，bit1 HMC，bit2 QMC |
| 2-3 | 航向角，百分之一度，无符号小端 |
| 4-5 | X 原始值，有符号小端 |
| 6-7 | Y 原始值，有符号小端 |
| 8-9 | Z 原始值，有符号小端 |
| 10-11 | 序号，无符号小端 |

## 待确认硬件信息

开始阶段 3 前需要确认：

1. 磁力计模块标签或芯片型号，例如 HMC5883L、QMC5883L 或 GY-273。
2. 模块 SDA、SCL 实际连接或计划连接到 ESP32 的 GPIO。建议候选为 SDA GPIO32、SCL GPIO33，但必须以电路板实际可用引脚为准。
3. 模块供电是否为 3.3V，以及模块是否自带 I2C 上拉电阻或电平转换。
4. 模块安装时哪一边朝向人体正前方。初版可以先用单位变换，真机测试后再确定方向符号和固定轴偏移。

## 验收原则

- 不以“代码已接入”作为完成标准。
- 每个阶段至少通过对应编译或单元测试。
- 正式页面不得静默回退到手机定位或手机方向。
- 数据无效必须在 UI 明确显示，且电机输出进入安全状态。
