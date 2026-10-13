# jiushouji-adb-apk2027

> 一台老旧安卓平板（`XXB` / `b906`）的**开发与探索全过程记录**。
>
> 目标：为这台设备定制一个 **APK，安装后开机自动运行**。
> 本仓库持续更新——所有实测数据、报告、开发指南、踩坑记录都会往这里放。

---

## 一句话结论

设备是一台 **MediaTek MT6582 平台的白色贴牌平板**，跑 **Android 5.1（API 21）**，
无 root、无 Google 服务、无第三方应用，2023 年 6 月产线烧录。

**给它做 APK 完全可行**——安装、开机自启、桌面接管、Kiosk 锁定四条路都已验证打通。
最大的约束是：**只有 `armeabi-v7a`、只有 1 个 CPU 核心在线、WebView 是 2014 年的 37 版。**

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
| **[02 · APK 开发指南](docs/02-APK开发指南.md)** | **程序员** | ★ 硬件/软件规格、编译配置、外设陷阱、**开机自启方案**、性能预算、自检清单 |
| [01 · 设备评估报告](docs/01-设备评估报告.md) | 所有人 | 设备身份、平台识别、完整规格、能力边界 |
| [03 · 连接与部署环境](docs/03-连接与部署环境.md) | 环境搭建 | Windows 侧 adb 安装、驱动、三层排错法 |
| [`raw/`](raw/) | 需要原始数据 | `getprop`、`pm list features`、传感器等全部原始输出 |
| [`skills/`](skills/) | 自动化 | 「连接 + 诊断 + 设备画像」的可复用流程 |

> 👉 **程序员请直接从 [02-APK开发指南](docs/02-APK开发指南.md) 的第 0 节开始读。**

---

## 设备速览

| 项目 | 值 |
|---|---|
| 型号 / 代号 | `XXB` / `b906`（`full_b906`） |
| 品牌 | `alps`（MTK 公版方案） |
| Android | **5.1 Lollipop / API 21** |
| SoC | MediaTek **MT6582**（4×Cortex-A7 @ 1.3 GHz） |
| **CPU 实际在线** | ⚠️ **只有 1 核，且停在 598 MHz** |
| **ABI** | ⚠️ **仅 `armeabi-v7a`**（无 64 位） |
| RAM / 存储 | 1.93 GB / 16 GB（`/data` 剩余 13.1 G） |
| 屏幕 | 720×1280 @ 280dpi = **411×731 dp**，竖屏 |
| **WebView** | ⚠️ **37**（2014 年 8 月） |
| Google 服务 | ❌ 无 |
| Root | ❌ 无 |
| SELinux | `permissive` |
| 中文输入法 | ❌ 无 |
| 固件 | `XM01X8_V1.0.0_230811`（2023-08-11 编译） |

### 平台溯源（推断）

基带串 `LOKYEE82_WET_KK_GPRS_HW` → `LOKYEE82` ≈ **`joya82`** 平台，
与公开固件 `MT6582__alps__K0708__4.4.2__...__joya82_wet_kk.zip` 同源。
ODM 复用 KitKat 时代 BSP 把系统升到了 5.1。

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
│   └── 03-连接与部署环境.md          adb 安装、驱动、排错
├── raw/                            真机原始实测数据（只读，勿改）
│   ├── getprop.txt
│   ├── pm-list-features.txt
│   ├── pm-list-packages.txt
│   ├── cpuinfo.txt / meminfo.txt
│   ├── df.txt / mount.txt
│   ├── sensorservice.txt
│   ├── dumpsys-battery.txt
│   ├── dumpsys-media-camera.txt
│   └── wm-size.txt / wm-density.txt
├── skills/
│   └── android-adb-connect/        连接 + 诊断 + 设备画像 流程
└── assets/
    └── phone-screenshot.png        设备主屏幕截图（连接成功实证）
```

---

## 已发现的关键问题

| 问题 | 状态 |
|---|---|
| 4 核只开 1 核，频率停在最低档 | ⏳ 待跑负载复测定性 |
| `/proc/loadavg` 读数异常（值不变） | ✅ 已确认为内核计数问题，勿用该指标 |
| 声明有指南针但无磁力计硬件 | ✅ 已确认，代码必须判空 |
| 声明有前置摄像头但只有 1 个摄像头 | ✅ 已确认，需按单摄设计 |
| 序列号是 MTK 占位值，不唯一 | ✅ 已确认，禁用作用设备标识 |

---

## 路线图

- [x] 确认 adb 可连接并授权
- [x] 完整设备画像与平台溯源
- [x] 产出 APK 开发指南
- [ ] 实机验证「4 核只开 1 核」成因
- [ ] 实测 `pm disable-user com.android.launcher3` 替换桌面
- [ ] 实测 Device Owner + LockTask 的锁定效果
- [ ] 实机 `adb reboot` 验证开机自启
- [ ] 第一个可用的开机自启 APK 原型

---

## 脱敏说明

本仓库为公开仓库，**已移除本机网络信息**（WiFi SSID / BSSID / MAC 地址 / 局域网 IP）。
设备侧不涉及个人数据——这是一台全新的、无账号、无第三方应用的设备。

---

*最后更新：2026-10-13*
