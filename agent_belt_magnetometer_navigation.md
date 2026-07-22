# Agent：腰带磁力计朝向接入与手机传感器替换

## 1. 任务背景

项目是面向视障跑者的 Android App 与 ESP32 辅助腰带。

现有系统已经实现：

- Android / Kotlin / Jetpack Compose；
- 高德地图显示与路线记录；
- 路线本地 JSON 保存、读取和删除；
- 路线线段投影、路径切线、进度和偏航计算；
- Android BLE GATT Client；
- ESP32 NimBLE GATT Server；
- ATGM336H 外置 GPS，经 BLE 向 Android 上报；
- 八路振动电机 PWM 和九字节强度向量协议；
- 正式运动中的方向引导、偏航提醒和到达提醒；
- 当前使用手机旋转矢量传感器判断人体朝向。

本任务将正式运动导航使用的手机位置和手机朝向全部替换为腰带数据：

- 位置只使用腰带 ATGM336H；
- 实际朝向只使用腰带磁力计；
- 路径切线继续表示目标方向；
- Android 继续负责地图、路线匹配、路线进度和振动规划；
- 暂时不考虑磁力计精度、倾斜补偿和多传感器融合。

## 2. 本阶段目标

完成以下链路：

    腰带 ATGM336H -> BLE -> Android 腰带位置
    腰带磁力计    -> BLE -> Android 腰带朝向
    路径切线 - 腰带朝向 -> 相对引导角 -> 八路电机

正式运动过程中不得读取手机定位和手机旋转矢量作为导航输入。

磁力计允许安装在腰带左侧或右侧，用户可以在 App 中选择安装侧。左右安装使用独立的安装变换和校准偏移，不允许在业务代码中散落硬编码角度。

## 3. 核心定义

全项目统一使用以下方位角约定：

    0°   = 北
    90°  = 东
    180° = 南
    270° = 西
    角度顺时针增加

必须明确区分：

- `routeHeading`：路线在当前位置的目标切线方向；
- `beltHeading`：磁力计计算出的腰带正前方方向；
- `relativeHeading`：目标方向相对腰带正前方的方向。

计算方式：

    relativeHeading = normalizeSigned(routeHeading - beltHeading)

`normalizeSigned` 必须将结果归一化到 `[-180°, 180°)`。

禁止把路径切线同时当作目标方向和实际人体朝向。禁止使用 GPS 运动航向替代磁力计朝向。

## 4. 范围限制

本阶段只完成能稳定演示的基础版本。

必须完成：

- ESP32 识别并读取磁力计；
- ESP32 计算基础磁航向；
- BLE 上报磁力计航向和有效状态；
- Android 解码并维护腰带朝向状态；
- App 支持选择磁力计安装在左侧或右侧；
- App 支持一次“面向正前方校准”；
- 正式导航只使用腰带 GPS 和腰带磁力计；
- 腰带朝向失效或超时时立即停止方向振动；
- 保持现有路线、地图、BLE 电机和振动节奏不变。

本阶段禁止实现：

- 手机定位或手机朝向自动回退；
- GPS 航向与磁力计航向融合；
- 陀螺仪、加速度计或九轴姿态融合；
- 动态倾斜补偿；
- 复杂硬铁、软铁校准算法；
- 卡尔曼滤波、Madgwick 或 Mahony；
- 三路测距和障碍物提醒；
- 自动判断磁力计安装在左侧还是右侧；
- UI 全面重做；
- 修改路线 JSON 数据格式；
- 将路线点或路线规划移动到 ESP32。

## 5. ESP32 固件要求

### 5.1 磁力计驱动

启动时扫描磁力计：

- `0x1E`：按 HMC5883L 驱动初始化；
- `0x0D`：按 QMC5883L 驱动初始化；
- 两者均不存在时，磁力计状态为无效，但固件不得崩溃或阻塞 BLE 和电机任务；
- 如果两者同时存在，记录明确日志，并使用代码中统一定义的优先级；
- 不得仅根据设备响应就假设芯片可用，初始化后至少验证一次合理的数据读取。

建立统一接口，业务层不得直接依赖具体芯片：

    bool magnetometerValid();
    bool readMagnetometer(int16_t &x, int16_t &y, int16_t &z);
    float getMagneticHeadingDeg();
    MagnetometerChip getMagnetometerChip();

允许根据项目现有代码风格调整名称，但必须保持驱动层和 BLE/导航层分离。

### 5.2 基础航向计算

本阶段使用水平面基础计算：

    heading = atan2(correctedY, correctedX)

将结果转换为 `[0°, 360°)`。

允许保留简单的 X/Y 零偏修正和圆周平滑，但不得为了追求精度引入新的姿态融合模块。角度平滑必须按圆周处理，不能直接对 `359°` 和 `1°` 做普通算术平均。

固件内部保存以下状态：

- 原始 X、Y、Z；
- 当前基础磁航向；
- 芯片类型；
- 数据有效状态；
- 单调递增序号；
- 最近成功采样时间。

采样目标频率为 10 Hz。读取失败不得复用旧数据并继续标记为有效。

### 5.3 BLE 航向通知

保留现有 BLE Service、写入特征和 GPS Notify 特征，不得破坏旧协议：

    Service UUID:              8f8a0001-8f4b-4f5b-9f2b-5e7a1f000001
    Motor Write UUID:          8f8a0002-8f4b-4f5b-9f2b-5e7a1f000001
    Belt GPS Notify UUID:      8f8a0003-8f4b-4f5b-9f2b-5e7a1f000001
    Belt Heading Notify UUID:  8f8a0004-8f4b-4f5b-9f2b-5e7a1f000001

新增固定 12 字节小端数据包：

    Byte 0      protocolVersion，当前为 1
    Byte 1      flags
                bit 0: headingValid
                bit 1: HMC5883L
                bit 2: QMC5883L
    Byte 2..3   headingCdeg，uint16，0..35999
    Byte 4..5   rawX，int16
    Byte 6..7   rawY，int16
    Byte 8..9   rawZ，int16
    Byte 10..11 sequence，uint16

要求：

- 航向有效时以 10 Hz 通知；
- 航向无效状态发生变化时立即通知一次无效包；
- BLE 客户端断开后继续保证现有电机硬件看门狗有效；
- 包长度、字节序和版本必须在 ESP32 与 Android 两端分别测试；
- 不得把磁力计包塞进现有 18 字节 GPS 包。

## 6. 左右安装与正前方校准

### 6.1 安装侧模型

Android 增加：

    enum class MagnetometerMountSide {
        LEFT,
        RIGHT
    }

增加统一的安装变换配置：

    data class MagnetometerMountTransform(
        val directionSign: Float,
        val baseOffsetDeg: Float
    )

左右侧分别拥有一套 `MagnetometerMountTransform`。所有镜像、轴方向和固定安装角修正只能在这一层完成。

不得假设“装在左边天然减 90°、装在右边天然加 90°”。左右位置本身不会自动决定方向偏移，实际变换取决于模块 PCB 的安装朝向。初始值可以暂设为单位变换，但必须集中配置并能通过实物测试修改。

安装变换后的方向：

    mountedHeading = normalize360(
        directionSign * rawHeading + baseOffsetDeg
    )

### 6.2 正前方校准

运动准备页的腰带状态详情增加：

- 磁力计安装侧：左侧 / 右侧；
- 当前原始航向；
- 当前校准后腰带航向；
- 磁力计芯片类型；
- 有效 / 无效 / 数据超时；
- “面向路线前方并校准”按钮。

校准流程：

1. 用户选择一条路线并站在路线起点附近；
2. App 根据当前位置获取当前路线切线 `referenceRouteHeading`；
3. 用户正确佩戴腰带，并让身体正前方对准路线前进方向；
4. 点击“面向路线前方并校准”；
5. App 计算并保存当前安装侧的 `userCalibrationOffsetDeg`；
6. 最终腰带航向由安装变换与用户校准偏移共同得到。

校准公式：

    userCalibrationOffsetDeg = normalizeSigned(
        referenceRouteHeading - mountedHeading
    )

    beltHeading = normalize360(
        mountedHeading + userCalibrationOffsetDeg
    )

校准时必须同时具备有效路线、有效腰带 GPS 和有效磁力计。条件不足时按钮不可执行，并显示真实原因。

安装侧和用户校准偏移保存在 App 本地。重新连接、切换页面和重启 App 后仍然有效。切换安装侧时必须分别读取该侧自己的偏移，不能共用一个校准值。

本阶段不要求自动指导用户完成完整八字校准，也不要求判断校准质量。

### 6.3 磁偏角

本阶段不单独计算磁偏角。使用路线切线作为校准参考时，`userCalibrationOffsetDeg` 会同时吸收当前地点的安装偏移和磁北/真北差值。

禁止在完成上述路线方向校准后再次叠加现有磁偏角修正，否则会重复补偿。以后改为完整磁力校准时，再单独恢复基于腰带 GPS 坐标的磁偏角修正。

## 7. Android 数据层要求

### 7.1 BLE 接入

在现有应用级腰带 BLE 连接中：

- 发现并订阅 `Belt Heading Notify`；
- 新增独立的固定长度解码器；
- 拒绝错误版本、错误长度和越界航向；
- 使用 Android 单调时钟记录包接收时间；
- 处理 `uint16 sequence` 回绕；
- 连接断开时立即清空腰带朝向有效状态。

建议数据模型：

    data class BeltHeadingSample(
        val magneticHeadingDeg: Float,
        val rawX: Int,
        val rawY: Int,
        val rawZ: Int,
        val chip: MagnetometerChip,
        val sequence: Int,
        val receivedElapsedRealtimeMs: Long,
        val valid: Boolean
    )

### 7.2 朝向 Provider

新增统一朝向接口或接入现有接口：

    interface HeadingProvider {
        val state: StateFlow<HeadingState>
        fun start()
        fun stop()
    }

正式运动只实例化或启用 `BeltHeadingProvider`。

`BeltHeadingProvider` 负责：

- 接收 BLE 原始磁航向；
- 应用左右安装变换；
- 应用该安装侧的用户校准偏移；
- 输出统一的 `[0°, 360°)` 腰带朝向；
- 判断数据是否超过 1 秒未更新；
- 输出数据来源、有效状态和错误原因。

不得让 `SportViewModel` 自己处理 BLE 字节、安装侧或校准偏移。

### 7.3 停用手机传感器

正式记录和运动链路要求：

- `RecordViewModel` 的位置只来自腰带 GPS；
- `SportViewModel` 的位置只来自腰带 GPS；
- `SportViewModel` 的朝向只来自 `BeltHeadingProvider`；
- 不注册手机旋转矢量监听；
- 不把 `Location.bearing` 或手机 GPS 轨迹方向作为人体朝向；
- 不在腰带数据失效时静默切回手机；
- Debug 页面可以保留既有虚拟 GPS 和手动电机测试，但不得进入正式导航数据源。

不要求立即删除旧手机 Provider，可以保留代码用于以后降级设计，但正式运行路径不得调用。

## 8. 导航接入要求

继续复用现有 `RouteTangentCalculator` 和 `NavigationVibrationPlanner`。

输入关系：

    routeHeading = RouteTangentCalculator 输出的路线切线方向
    bodyHeading  = BeltHeadingProvider 输出的腰带正前方方向
    relative     = normalizeSigned(routeHeading - bodyHeading)

不得修改现有前侧 154° 圆弧、八电机编号、相邻双电机插值、3°死区、偏航提醒和到达提醒，除非接入过程中发现明确 bug。

运动开始条件增加：

- 已选择有效路线；
- 腰带 BLE 已连接；
- 腰带 GPS 有效且数据新鲜；
- 腰带磁力计有效且数据新鲜；
- 已选择磁力计安装侧。

以下情况立即停止全部导航振动，并显示明确状态：

- BLE 断开；
- 腰带 GPS 无效或超时；
- 腰带磁力计无效或超过 1 秒未更新；
- 磁力计协议版本错误；
- 暂停或结束运动。

恢复有效数据后可以自动恢复计算，但必须重新经过现有脉冲调度器，禁止直接让电机持续振动。

## 9. UI 要求

只修改与新数据源相关的必要区域：

- 记录页 GPS 状态明确显示“腰带 GPS”；
- 运动准备页显示腰带 GPS 和腰带磁力计状态；
- 磁力计状态可点击进入详情；
- 详情中可以选择左侧或右侧安装；
- 提供“设为正前方”校准；
- 运动中方向卡显示“腰带朝向等待”“腰带朝向失效”等状态；
- 不显示手机朝向来源；
- 不增加新的底部导航页面；
- 保持现有紫色 Material 3 视觉规范和共享组件。

UI 不得展示伪造数据。没有磁力计数据时必须显示无效或等待。

## 10. 状态与并发要求

- BLE 回调不得直接操作 Compose 状态；
- BLE 数据进入 Repository/Provider，再通过 `StateFlow` 暴露；
- 同一时刻只能存在一个正式腰带 BLE 连接；
- 记录页和运动页继续共用应用级 BLE 连接；
- 页面重组不得重复订阅 GATT Notification；
- 页面销毁不得意外关闭仍由前台导航服务使用的连接；
- 使用单调时钟判断超时，不得使用系统墙上时间；
- 固件读取磁力计不得阻塞电机看门狗、BLE 事件和 GPS UART 解析。

## 11. 测试要求

### 11.1 ESP32

- HMC5883L 初始化成功和失败；
- QMC5883L 初始化成功和失败；
- 无磁力计时固件仍能启动 BLE、GPS 和电机；
- 航向归一化到 `[0°, 360°)`；
- 12 字节小端包字段正确；
- `sequence` 正常递增并允许回绕；
- 读取失败时 `headingValid` 立即变为 false；
- 磁力计任务不会导致电机硬件看门狗失效。

### 11.2 Android JVM 单元测试

- 正常包解码；
- 错误长度拒绝；
- 错误协议版本拒绝；
- `headingCdeg` 越界拒绝；
- HMC5883L、QMC5883L 和无芯片标志解析；
- `359°` 与 `1°` 的归一化和角度差；
- 左右安装变换分别生效；
- 左右安装分别保存自己的校准偏移；
- 正前方校准使用当前路线切线作为参考，不读取手机方向；
- 超过 1 秒无数据后朝向失效；
- BLE 断开后朝向失效；
- 磁力计无效时导航输出全零；
- 正式运动不读取手机朝向；
- 正式运动不回退手机定位。

### 11.3 实物验收

不以精度作为本阶段验收指标，只验证方向逻辑：

1. 磁力计装在左侧，选择左侧并完成正前方校准；
2. 人体分别面向北、东、南、西，App 航向变化方向正确；
3. 切换到右侧安装配置并重新校准，四方向逻辑仍正确；
4. 固定一条向北路线，人体面向北时中央电机输出；
5. 同一路线中人体面向东时左侧电机输出；
6. 同一路线中人体面向西时右侧电机输出；
7. 拔掉磁力计或停止通知后，1 秒内停止全部方向振动；
8. 关闭手机定位和手机传感器权限后，腰带导航链路仍可运行；
9. GPS、磁力计、BLE 和八电机可以同时工作且不崩溃。

如果实物电机左右方向与预期相反，优先检查磁力计安装变换、坐标符号和电机编号，不得通过交换导航业务中的 LEFT/RIGHT 文案掩盖错误。

## 12. 实施顺序

严格按以下顺序推进，每完成一步先编译和测试，不要一次性重写：

1. 阅读现有 ESP32、BLE、外置 GPS、朝向 Provider、`SportViewModel` 和导航规划代码；
2. 画出当前真实数据流并列出计划修改的文件；
3. 实现 ESP32 磁力计统一驱动与基础航向；
4. 新增 BLE Heading Notify 特征和数据包；
5. 实现 Android 数据包解码和单元测试；
6. 实现 `BeltHeadingProvider`、左右安装变换和本地配置；
7. 在运动准备页加入状态、安装侧选择和正前方校准；
8. 将正式记录位置切换为仅腰带 GPS；
9. 将正式运动位置和朝向切换为仅腰带数据；
10. 接入失效、超时、断连时的停止振动策略；
11. 运行全部 JVM 测试、Debug/Beta/Release 编译和 Lint；
12. 更新 `project-progress.md`，记录协议、限制、测试结果和剩余真机事项。

## 13. 工程约束

- 优先复用已有 Repository、Provider、ViewModel 和 BLE 连接；
- 不建立第二套 BLE 管理器；
- 不复制路线切线或振动规划算法；
- 不修改现有电机九字节强度向量协议；
- 不破坏 ATGM336H GPS 通知协议；
- 不删除与本任务无关的用户代码；
- 不清理或覆盖无关的未提交改动；
- 不使用阻塞延时处理传感器和 BLE；
- 不为了通过测试写死模拟航向；
- 所有新增阈值和安装变换集中定义；
- 代码命名、目录和依赖注入方式遵循项目现有风格。

## 14. 验证命令

根据项目环境执行现有验证流程：

    ./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin :app:lintDebug
    ./gradlew.bat :app:assembleBeta :app:lintBeta

ESP32 使用项目当前 ESP-IDF v6.0.2 环境完整编译。

如果当前环境不是 Windows，使用等价的 `./gradlew` 命令。不得把“代码看起来正确”当作编译通过。

## 15. 完成标准

只有同时满足以下条件才可以宣布任务完成：

- ESP32 能读取实际磁力计并通过独立 BLE 特征通知；
- Android 能显示实际腰带航向和芯片状态；
- 左右安装可以选择并分别校准；
- 正式导航完全不读取手机定位和手机朝向；
- 路径切线与腰带朝向能够驱动现有八电机规划；
- 磁力计失效、超时或 BLE 断开时电机停止；
- 现有 GPS、路线、地图、偏航、到达和电机功能未回归；
- 新增单元测试与原有测试全部通过；
- Debug、Beta 和 Release 构建通过；
- ESP32 固件编译通过；
- 已更新项目进度文档并明确记录“当前没有倾斜补偿和姿态融合”。

完成报告必须列出：

- 修改文件；
- BLE 协议变化；
- 左右安装变换的实际配置；
- 手机定位和朝向被停用的位置；
- 自动化测试和编译结果；
- 尚未完成的真机测试；
- 当前已知限制。
