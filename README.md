# jiushouji-adb-apk2027

> 一台老旧安卓平板（`XXB` / `b906`）的**开发与探索全过程记录**。
>
> 目标：为这台设备定制一个 **APK，安装后开机自动运行**。
> 本仓库持续更新——所有实测数据、报告、开发指南、踩坑记录都会往这里放。

---

## 一句话结论

设备是一台 **MediaTek MT6582 平台的白色贴牌平板**，跑 **Android 5.1（API 21）**，
无 root、无 Google 服务、无第三方应用，2023 年 6 月产线烧录。

**✅ 已用自建 APK 在真机上完整验证：安装 → 桌面接管 → 真实重启后自动启动 →
Device Owner → LockTask 锁定（按 Home/Back 退不出去）。零人工 Kiosk 方案完全可行。**

最大的约束是：

- **只有 `armeabi-v7a`**（无 64 位）
- **WebView 是 2014 年的 Chrome 37** —— ES6 语法全线不可用
- **图形栈较弱**（UI 负载下 10–21 FPS，重负载可能触发框架软重启）
- CPU 空闲时只 1 核 @598 MHz，**有负载时 4 核全开 @1.3 GHz**（已实测）

详见 [06 · Kiosk 可行性实测](docs/06-Kiosk可行性实测.md)。

---

## 快速开始

```powershell
# 1. 装 adb（若没有）
winget install --id Google.PlatformTools --accept-package-agreements --accept-source-agreements

# 2. 连接设备（设备端需已开启 USB 调试并点过「允许」）
adb devices -l
# → 0123456789ABCDEF  device product:full_b906 model:XXB device:b906

# 3. 装应用
adb install -r app-release.apk

# 4. ★ 首次启动（清除 stopped 状态，不做这步开机自启永远不生效）
adb shell am start -n com.example.jiushouji/.MainActivity
```

---

## 文档索引

| 文档 | 给谁看 | 内容 |
|---|---|---|
| **[07 · 开发任务书](docs/07-开发任务书.md)** | **程序员 / 项目经理** | ★★★ **可直接下发的任务书**：硬约束清单、功能要求、参考代码、13 项验收标准 |
| **[06 · Kiosk 可行性实测](docs/06-Kiosk可行性实测.md)** | **决策者 / 程序员** | ★★ **自建 APK 真机全流程验证**：安装 / 桌面接管 / 开机自启 / Device Owner / LockTask 锁定 |
| **[02 · APK 开发指南](docs/02-APK开发指南.md)** | **程序员** | ★ 硬件/软件规格、编译配置、外设陷阱、**开机自启方案**、性能预算、自检清单 |
| [01 · 设备评估报告](docs/01-设备评估报告.md) | 所有人 | 设备身份、平台识别、完整规格、能力边界 |
| [04 · WebView 兼容性实测](docs/04-WebView兼容性实测.md) | 前端 | **52 项特性检测**：WebView = Chrome 37，ES6 全线不可用 |
| [05 · 稳定性事故报告](docs/05-稳定性事故报告.md) | 所有人 | SurfaceFlinger 看门狗崩溃 → 框架软重启的根因与规避 |
| [03 · 连接与部署环境](docs/03-连接与部署环境.md) | 环境搭建 | Windows 侧 adb 安装、驱动、三层排错法 |
| [`raw/`](raw/) | 需要原始数据 | 全部原始实测输出（getprop / 崩溃 tombstone / 日志…） |
| [`tools/`](tools/) | 复现 | 测试 APK 源码与构建脚本、CPU 测试脚本、WebView 检测页 |
| [`skills/`](skills/) | 自动化 | 「连接 + 诊断 + 设备画像」的可复用流程 |

> 👉 **要开始写代码的人：直接读 [07-开发任务书](docs/07-开发任务书.md)，
> 参考实现看 [`tools/kiosk-test/`](tools/kiosk-test/)。**

> 👉 **程序员请直接从 [02-APK开发指南](docs/02-APK开发指南.md) 的第 0 节开始读。**

---

## 设备速览

| 项目 | 值 |
|---|---|
| 型号 / 代号 | `XXB` / `b906`（`full_b906`） |
| 品牌 | `alps`（MTK 公版方案） |
| Android | **5.1 Lollipop / API 21** |
| SoC | MediaTek **MT6582**（4×Cortex-A7 @ 1.3 GHz） |
| **CPU 实际在线** | ✅ hotplug 正常：空闲 1 核 @598 MHz，**负载时 4 核全开 @1.3 GHz** |
| **ABI** | ⚠️ **仅 `armeabi-v7a`**（无 64 位） |
| RAM / 存储 | 1.93 GB / 16 GB（`/data` 剩余 13.1 G） |
| 屏幕 | 720×1280 @ 280dpi = **411×731 dp**，竖屏 |
| **WebView** | ⚠️ **37**（2014 年 8 月） |
| Google 服务 | ❌ 无 |
| Root | ❌ 无 |
| SELinux | `permissive` |
| 中文输入法 | ❌ 无 |
| 固件 | `XM01X8_V1.0.0_230811`（2023-08-11 编译） |

### 平台溯源

**ODM 厂商：`KEYTAK`** —— 来自编译期写死的项目路径：

```
ro.mediatek.project.path    = device/keytak/b906
ro.mediatek.version.release = ALPS.L0.MP2.V1_KEYTAK6582.WT.L
```

基带串 `LOKYEE82_WET_KK_GPRS_HW` → `LOKYEE82` ≈ **`joya82`** 平台，
与公开固件 `MT6582__alps__K0708__4.4.2__...__joya82_wet_kk.zip` 同源。
ODM 复用 KitKat 时代 BSP 把系统升到了 5.1。

### 网络配置（全量实测）

| 项 | 值 |
|---|---|
| SSID / BSSID | `TP-LINK_2190` / `78:a1:06:49:21:90` |
| MAC | `00:08:22:c6:a2:fb` |
| IP / 网关 | `192.168.191.104/24` / `192.168.191.1` |
| DNS | `58.22.96.66`, `192.168.191.1` |
| 主机名 | `android-3047fa1f7957a489` |

> 系统属性只记录连接结果，**不含 WiFi 密码**。

---

## 给程序员的十条硬约束

| # | 约束 | 后果 |
|---|---|---|
| 1 | `minSdkVersion` ≤ 21 | 大于 21 装不上 |
| 2 | 只有 `armeabi-v7a` | 带 native 库的 SDK 必须提供该 ABI，否则崩溃 |
| 3 | 无任何 Google 服务 | 禁用 Firebase / FCM / Play Services / Maps |
| 4 | WebView 37 | H5 必须写 ES5，禁用 `fetch` / CSS Grid / CSS 变量 |
| 5 | 1 核 @ 598 MHz | 单线程性能极低，主线程零阻塞 |
| 6 | RAM 1.93 GB | 内存预算保守 |
| 7 | 屏幕 411×731 dp | 按此宽度设计 |
| 8 | `targetSdkVersion` 锁 21~22 | 规避后续 API 行为变更 |
| 9 | 装完处于 `stopped` 状态 | **必须手动启动一次**，否则收不到开机广播 |
| 10 | 无 root / 无中文输入法 | 不能改系统分区；中文输入需自带方案 |

详细展开见 [02-APK开发指南 § 0](docs/02-APK开发指南.md)。

---

## 仓库结构

```
jiushouji-adb-apk2027/
├── README.md                       本文件
├── docs/
│   ├── 01-设备评估报告.md            设备身份、规格、能力边界
│   ├── 02-APK开发指南.md            ★ 程序员主文档
│   ├── 03-连接与部署环境.md          adb 安装、驱动、排错
│   ├── 04-WebView兼容性实测.md       52 项特性检测结果
│   ├── 05-稳定性事故报告.md          SurfaceFlinger 崩溃根因
│   └── 06-Kiosk可行性实测.md        ★★ 自建 APK 真机全流程验证
├── raw/                            真机原始实测数据（只读，勿改）
│   ├── getprop.txt / pm-list-*.txt
│   ├── cpuinfo.txt / meminfo.txt / df.txt / mount.txt
│   ├── sensorservice.txt / dumpsys-*.txt
│   ├── cpu-hotplug-test-result.txt       CPU 负载复测输出
│   ├── cpu-hotplug-governor-config.txt   hotplug 全部参数
│   ├── dstate-threads.txt                loadavg 真相
│   ├── shell-permissions.txt             shell 的 66 条权限
│   ├── dropbox-*.txt                     SurfaceFlinger 崩溃取证
│   ├── boot-autostart-evidence.txt       开机自启完整日志
│   └── device-owner-state.txt            Device Owner 状态
├── tools/
│   ├── cpu-hotplug-test.sh          CPU 热插拔测试脚本
│   ├── webview-test.html            WebView 特性检测页
│   └── kiosk-test/                  ★ 测试 APK 全部源码 + 构建脚本 + 已签名 APK
├── skills/
│   └── android-adb-connect/         连接 + 诊断 + 设备画像 流程
└── assets/
    ├── phone-screenshot.png         设备主屏截图
    ├── kiosk-01 ~ 06*.png           Kiosk 全流程关键截图
    ├── webview-01 ~ 03*.png         WebView 兼容性截图
    └── keyguard-01-lock-screen.png  锁屏截图
```

---

## 已发现的关键问题

| 问题 | 状态 |
|---|---|
| CPU 空闲只 1 核 | ✅ **已查清：非故障**。负载下 4 核全开 @1.3 GHz，已复现两次 |
| `/proc/loadavg` 基线恒为 ~7 | ✅ **已查清**：7 个 MTK 内核线程长期 D 状态垫高基线，非计数错误 |
| `pm disable-user` 全部被拒 | ✅ **已查清**：shell 无 `CHANGE_COMPONENT_ENABLED_STATE`，禁用原桌面不可行 |
| `dpm set-device-owner` 是否可用 | ✅ **已实测跑通**，Device Owner + LockTask 均生效 |
| WebView 到底能跑什么 | ✅ **52 项实测**：Chrome 37，ES6 语法全灭，CSS Grid/变量不可用 |
| 设备会突然"假死"（服务全无） | ✅ **已定位**：SurfaceFlinger 看门狗 abort → 框架软重启 |
| 桌面帧率到底多少 | ✅ **实测 10–21 FPS**，单帧卡顿最高 780 ms |
| `am broadcast` 的 `-p` 参数 | ✅ **5.1 不支持**，必须改用 `-n` 指定组件 |
| 声明有指南针但无磁力计硬件 | ✅ 已确认，代码必须判空 |
| 声明有前置摄像头但只有 1 个摄像头 | ✅ 已确认，需按单摄设计 |
| 设备端没有 `sed`（但有 `grep`/`awk`） | ✅ 已确认，脚本别用 `sed` |
| 序列号是 MTK 占位值，不唯一 | ✅ 已确认，禁用作用设备标识 |

---

## 路线图

- [x] 确认 adb 可连接并授权
- [x] 完整设备画像与平台溯源（ODM = `KEYTAK`）
- [x] 产出 APK 开发指南
- [x] 实机验证「4 核只开 1 核」成因 → **hotplug 正常，非故障**
- [x] 实测 `pm disable-user com.android.launcher3` → **权限不足，不可行**
- [x] 实测 `dpm set-device-owner` 权限 → **通过**
- [x] 实测 WebView 兼容性（52 项）→ **Chrome 37，ES6 全灭**
- [x] 排查并定位 SurfaceFlinger 看门狗崩溃 → **框架软重启根因**
- [x] 实测 UI 负载帧率 → **10–21 FPS**
- [x] **自建测试 APK 并真机验证 Kiosk 全流程** → **8 项全部通过**
- [x] 实机 `adb reboot` 验证开机自启 → **36 秒后自动拉起**
- [x] 实测 Device Owner + LockTask 锁定效果 → **Home/Back 均无法退出**
- [x] 验证 Device Owner 清除路径 → **恢复出厂可清除，但会重置 ADB 授权**
- [x] 产出**开发任务书**（含 13 项可验收标准）→ [`docs/07-开发任务书.md`](docs/07-开发任务书.md)
- [x] 测试完毕，设备已完整恢复初始状态（84 包 / 0 第三方 / 无 Device Owner）
- [ ] 按任务书开发真正的 Kiosk 应用（**用原生 View，避开 WebView**）
- [ ] 加入崩溃自恢复（利用 framework 软重启后重新广播 BOOT_COMPLETED 的特性）
- [ ] 批量部署流程验证（多台设备的 ADB 授权 + Device Owner 设置）

---

## 数据说明

本仓库为公开仓库，但**所有设备数据均为全量、未脱敏的原始实测值**，
包括完整网络配置（WiFi SSID / BSSID / MAC / 局域网 IP / DNS / 主机名）。

**为什么不做脱敏**：这些信息对后续开发有实际价值——adb 调试、网络排查、
多设备区分、设备识别都依赖它们。缺数据反而会导致误判。

**为什么不担心**：设备本身是全新旧机，无账号、无第三方应用、无个人数据；
且经核查，`getprop` 输出中**不含任何密码或凭据字段**
（WiFi 密码不在系统属性里，系统属性只记录 IP/DNS/网关等连接结果）。

---

*最后更新：2026-10-13*
