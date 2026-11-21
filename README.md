<div align="center">

<img src="app/src/main/res/drawable-nodpi/ic_launcher_art.png" width="108" alt="FunClass Icon" />

<h1>FunClass</h1>

<p>一款基于 <strong>Jetpack Compose + Material 3</strong> 打造的 Android 原生纯本地纯净课表应用</p>

<p>零广告 · 零追踪 · 无云端依赖 · 最小化权限</p>

<p>
  <a href="https://github.com/my-fun-bit/FunClass/releases/latest">
    <img src="https://img.shields.io/github/v/release/my-fun-bit/FunClass?label=Release&color=499AE0" alt="Latest Release" />
  </a>
  <img src="https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-3DDC84?logo=android&logoColor=white" alt="Android 8.0+" />
  <img src="https://img.shields.io/badge/Kotlin-2.1.10-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.1.10" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20M3-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose + M3" />
  <a href="LICENSE">
    <img src="https://img.shields.io/github/license/my-fun-bit/FunClass?color=E06D94" alt="MIT License" />
  </a>
</p>

<p>
  <a href="#下载与安装">下载与安装</a> ·
  <a href="#核心功能">核心功能</a> ·
  <a href="#教务与文件兼容">教务与文件兼容</a> ·
  <a href="#权限说明">权限说明</a> ·
  <a href="#架构与本地构建">架构与构建</a>
</p>

</div>

---

## 下载与安装

前往 [**GitHub Releases**](https://github.com/my-fun-bit/FunClass/releases/latest) 下载最新的签名安装包 `FunClass-v2.0-release.apk`。

- **系统要求**：Android 8.0（API 26）及以上，应用包名 `com.myfun.funclass`，当前版本 `v2.0`（`versionCode = 2`）。
- **完整性校验**：每个 GitHub Release 页面均附带对应 APK 的精确文件大小与 `SHA-256` 校验值，安装前可核对哈希值确认文件未被篡改。
- **实况通知兼容**：支持 Android 16（API 36）及以上系统标准 Live Updates（`POST_PROMOTED_NOTIFICATIONS` 实况通知 / 实时更新），并在 Android 8–15 设备上自动降级为常规高优先级倒计时提醒。

## 核心功能

- **课表视图与个性化外观**
  - 支持多课表管理、自定义作息时间方案、周一/周日作为首日切换、重叠课程自动并列布局与非本周课程半透明展示。
  - 内置 `FunClassColorEngine` 色彩引擎：提供 **7 款清新马卡龙预设**（樱花奶昔、蜜桃气泡、抹茶奶绿、晴空苏打、香芋奶紫、薄荷海盐、奶油柠檬）与 **4 款经典色系**（深海蓝、森林绿、落日橙、薰衣草），并支持自定义种子主题色与跟随系统/浅色/深色模式策略（`DarkModePolicy`）。
  - 支持 `BackgroundColorExtractor` 背景图智能提取主题色，可调节全局背景、背景压暗遮罩及导航/卡片表面透明度，并提供 20+ 项细节外观参数（行高、卡片圆角、透明度、字号、课程名/地点最大行数、虚线网格、教师/地点/备注开关等）。

- **多通道导入与离线分享**
  - 内置 WebView 教务登录抓课、通用 HTML 表格解析与自定义 JS 插件桥接协议（`JsPluginBridgeProtocol`）。
  - 支持 `.funclass` / `.wakeup_schedule`、CSV/Excel 与 RFC 5545 `.ics` 文件的导入导出及系统文件关联直接打开。
  - 支持纯离线 GZIP + Base64 压缩分享口令（`FUNCLASS1:`），内置二维码生成、CameraX 实时扫码与系统相册图片识码。

- **日期、日程与节假日调休**
  - 基于 `WeekSet` 离散周次算法与 `ScheduleDateResolver` 统一日期解析引擎。
  - 内置 2026 / 2027 年中国法定节假日放假与周末补班调休预设（`ChinaHolidayPresets`），以非破坏性叠加层形式生效，支持单表/全局配置、手动增删与随时停用恢复。
  - 提供独立日程待办视图、课程/日程双态编辑器，以及批量调课与多课表切换管理。

- **上课提醒与实况通知**
  - 基于 `AlarmManager` 的精确课前提醒调度，支持 Android 16+ Live Updates 实况通知并在低版本系统平滑降级。
  - 支持上课期间自动勿扰模式切换，以及通过 `WorkManager` + 系统广播（开机启动、应用更新、时间/时区修改、精确闹钟权限变更）自动重排提醒任务。

- **Jetpack Glance 桌面小组件**
  - 提供 3 种桌面小组件：**今日课表**（`TodayGlanceWidget`）、**今日+明日双日课表**（`TodayTomorrowGlanceWidget`）与 **周课表网格**（`WeekGridGlanceWidget`）。
  - 每个小组件均可通过 `WidgetConfigActivity` 独立绑定课表并配置背景色、透明度、文字色与圆角半径。

## 教务与文件兼容

| 导入 / 导出通道 | 支持范围与格式 |
| :--- | :--- |
| **WebView 教务抓课** | 正方 / 新正方、强智、新 URP、金智 / 树维、青果 / 新青果、通用 HTML 表格、自定义 JS 插件；内置 5 类通用入口与 15 所高校快捷入口 |
| **课表文件读写** | `.funclass` / `.wakeup_schedule`（双向读写）、`.csv`（导入/导出）、`.ics`（RFC 5545 导入/导出）、`.html`（导入）；仓库内 `test_samples/` 附带 CSV 与 `.wakeup_schedule` 测试样例 |
| **离线口令与二维码** | `FUNCLASS1:` 离线压缩口令（生成与导入）、二维码生成 / 相机扫码 / 相册识码 |

## 权限说明

FunClass 严格遵循最小权限原则，零广告 SDK、零行为统计、零后台联网上传，全部数据仅存储于本地设备。清单文件（[`AndroidManifest.xml`](app/src/main/AndroidManifest.xml)）仅声明以下 8 项必需权限：

| 权限 | 用途说明 |
| :--- | :--- |
| `INTERNET` | 仅用于内置 WebView 打开高校教务网站抓课（无内置后端或遥测上报） |
| `POST_NOTIFICATIONS` | 发送课前提醒与倒计时通知（Android 13+ 运行时按需申请） |
| `POST_PROMOTED_NOTIFICATIONS` | 在 Android 16+ 设备上展示 Live Updates 实况通知（状态栏实时胶囊）上课提醒 |
| `SCHEDULE_EXACT_ALARM` | 按设置提前量准点触发课程提醒闹钟 |
| `RECEIVE_BOOT_COMPLETED` | 设备重启后自动重新调度后续上课提醒 |
| `VIBRATE` | 上课提醒通知触发震动反馈 |
| `ACCESS_NOTIFICATION_POLICY` | 开启“上课自动勿扰”时自动调节系统勿扰状态 |
| `CAMERA`（`required="false"`） | 仅在使用扫码导入课表口令时申请；未授予或无相机设备不影响其余功能 |

## 架构与本地构建

### 技术栈

- **语言与工具链**：Kotlin 2.1.10 + JVM 17 + Gradle 8.11.1（Version Catalog）+ AGP 8.8.2
- **系统基线**：`minSdk = 26`（Android 8.0）/ `targetSdk = 36` / `compileSdk = 36`
- **UI 框架**：单 Activity + Jetpack Compose + Material 3 + Navigation Compose + Coil
- **数据与依赖注入**：Room 2.7.0 + Preferences DataStore + Hilt 2.55 + KSP
- **小组件与工具**：Jetpack Glance 1.1.1 + Fleeksoft Ksoup + CameraX + ZXing + WorkManager

### 模块划分

| 模块 / 目录 | 职责 |
| :--- | :--- |
| `:core:model` | 纯 Kotlin 领域模型、`WeekSet` 离散周次算法、法定节假日预设与统一日期调休解析引擎（`ScheduleDateResolver`） |
| `:core:database` | Room 数据库（`FunClassDatabase`）、DAO、`ScheduleRepository` 与 DataStore 偏好设置 |
| `:core:parser` | 5 大主流教务解析器、通用 HTML 解析、JS 插件注入协议、`.funclass` / `.wakeup_schedule` 编解码、CSV/Excel 与 `.ics` 导入导出 |
| `:core:sync` | 纯离线课表分享口令编解码（`ShareCodeCodec`）与分享仓库（`ShareCodeRepository`） |
| `:widget` | 3 种 Jetpack Glance 桌面小组件与独立配置页（`WidgetConfigActivity`） |
| `:app` | 主界面、导入中心（WebView/文件/口令/扫码）、提醒与实况通知调度、主题配色引擎与设置中心 |
| `test_samples/` | 测试样例课表文件（`sample_schedule.csv` 与 `sample_schedule.wakeup_schedule`） |

### 本地构建与测试

1. **环境要求**：安装 **JDK 17** 与 **Android SDK**（含 API 36 平台及对应 Build Tools），并在项目根目录创建 `local.properties` 指定 SDK 路径：
   ```properties
   sdk.dir=C\:\\Users\\<User>\\AppData\\Local\\Android\\Sdk
   ```

2. **运行单元测试**：

   Windows PowerShell：
   ```powershell
   .\gradlew.bat :core:model:test :core:parser:test :core:sync:testDebugUnitTest :app:testDebugUnitTest
   ```

   macOS / Linux：
   ```bash
   ./gradlew :core:model:test :core:parser:test :core:sync:testDebugUnitTest :app:testDebugUnitTest
   ```

3. **构建 APK**：
   ```powershell
   # 构建 Debug APK
   .\gradlew.bat assembleDebug
   ```
   如需构建签名 Release APK，可复制 [`keystore.properties.example`](keystore.properties.example) 为 `keystore.properties` 并填写本地签名证书路径与密码（`keystore.properties` 与 `keystore/` 已在 `.gitignore` 中排除，不会提交到版本库），然后执行：
   ```powershell
   .\gradlew.bat assembleRelease
   ```

## 开源协议

本项目基于 [MIT License](LICENSE) 开源，第三方开源组件许可详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
