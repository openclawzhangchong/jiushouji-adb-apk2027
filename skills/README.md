# 技能与探测方法

本目录收录本项目沉淀的 3 个**可复用技能**，以及一套**真机探测方法速查**。

技能是「可执行的流程」，不是文档——下次遇到同类设备/问题，直接按流程走，不用重新摸索。

---

## 一、三个技能

### 1. [`android-adb-connect`](android-adb-connect/SKILL.md)

**用途**：用 adb 连接实体安卓设备并做连通性诊断与设备画像。

**核心方法论：三层定位法** —— 把「连不上手机」拆成三层，逐层取证，避免一上来就重装驱动：

| 层 | 现象 | 判据 |
|---|---|---|
| ① USB 枚举层 | PnP 里没有该 VID/PID | `Get-PnpDevice` |
| ② ADB 接口层 | 只有 MTP 接口 | `CompatibleIds` 是否有 `USB\Class_ff&SubClass_42&Prot_01` |
| ③ 授权层 | `adb devices` 显示 `unauthorized` | 设备端点「允许」 |

**包含的关键技巧**：

- **零风险权限试探**：用无害包 / 不存在的组件去试，靠报错区分「权限不够」还是「目标不存在」
- **白牌机识别**：靠基带串 + 厂商包时间戳溯源 ODM
- **Kiosk 可行性探测清单**（8 步）
- **负载复测判定多核故障 vs 省电策略**

### 2. [`android-apk-build-no-studio`](android-apk-build-no-studio/SKILL.md)

**用途**：在没有 JDK / Android SDK / Gradle 的 Windows 上，从零下载工具链手工构建 APK。

**核心**：四条命令链

```
aapt2 compile → aapt2 link → javac --release 8 → d8 → jar uf → zipalign → apksigner
```

**包含的三个必踩的坑**：

1. `javac -bootclasspath` 会把 JDK 自己的 `java.lang` 顶掉 → 必须用 `--release 8` + `-classpath`
2. 老 `android.jar`（API 16）没有 API 21 的方法 → 新 API 全部**用反射调用**
3. JDK 下载源：Adoptium 国内太慢、清华镜像拒绝 curl → 用 `aka.ms/download-jdk/...`

### 3. [`github-push-when-blocked`](github-push-when-blocked/SKILL.md)

**用途**：`github.com` 被墙导致 `git push` 失败时，用 GitHub API 通道把改动推上去。

**核心**：识别「**分裂式阻断**」—— `github.com` 不通但 `api.github.com` 通、`gh` 命令正常。

**三个方案**：

| 方案 | 条件 |
|---|---|
| A. GitHub Git Data API 造提交 | `api.github.com` 通即可（**最可靠**） |
| B. SSH over 443 | SSH key 必须属于仓库所有者/协作者 |
| C. 重试等待 | 阻断是间歇性的，经常第 2~5 次就通 |

**附带脚本**：[`../tools/gh_api_push.py`](../tools/gh_api_push.py)

---

## 二、真机探测方法速查

以下方法**全部在本项目的目标设备上实测过**，可直接套用到其他旧安卓设备。

### 2.1 连接与权限探测

| 目的 | 方法 |
|---|---|
| 设备是否被 Windows 认到 | `Get-PnpDevice -PresentOnly \| ? { $_.InstanceId -like 'USB\*' }` |
| ADB 接口是否暴露 | 读 `DEVPKEY_Device_CompatibleIds`，找 `USB\Class_ff&SubClass_42&Prot_01` |
| 缺驱动没有 | `Get-PnpDevice \| ? { $_.Status -ne 'OK' }` |
| **shell 有哪些权限** | `adb shell "dumpsys package com.android.shell"` → 提取 `grantedPermissions` |
| **某操作能不能做（零风险）** | 用无害包 / 不存在组件试探，看报错类型 |
| 命令是否存在 | `adb shell "command -v <cmd>"` |

### 2.2 硬件与系统画像

| 目的 | 方法 |
|---|---|
| 设备类别 / 面板朝向 | `getprop ro.build.characteristics` / `ro.sf.hwrotation` |
| **真实 CPU 核心数** | `cat /sys/devices/system/cpu/present` 与 `online`（**别数 cpuinfo**） |
| **多核是故障还是省电** | 施加负载后重读 `online`（见 §2.3） |
| 外设能力 | `pm list features` |
| **声明的传感器是否真存在** | `dumpsys sensorservice`（**别信 `hasSystemFeature`**） |
| 摄像头数量 | `dumpsys media.camera` → `Number of camera devices` |
| 厂商 / ODM 溯源 | `getprop ro.mediatek.project.path`、`gsm.project.baseband` |
| 是否产线烧录 | `dumpsys package <厂商包> \| grep firstInstallTime` 看时间是否扎堆 |
| 是否有 root | `ro.secure` / `ro.debuggable`（**别用 `ls /sbin/su`**） |
| 屏幕参数 | `wm size` / `wm density` |
| 真实负载 | `top` 或 `/proc/stat` 增量（**别用 `loadavg`**） |

### 2.3 CPU 热插拔：故障 vs 省电策略

**别看到 `online = 0` 就下结论。** 施加负载再读一次：

```bash
# 起 8 个忙循环
for i in 1 2 3 4 5 6 7 8; do sh -c 'while :; do :; done' & done
sleep 4
cat /sys/devices/system/cpu/online     # 核数回升 = 正常省电策略
```

现成脚本：[`../tools/cpu-hotplug-test.sh`](../tools/cpu-hotplug-test.sh)

实测参考：空闲 `0`（1 核 @598MHz）→ 负载 `0-3`（**4 核全开 @1.3GHz**）→ 释放后逐步关核。

### 2.4 WebView 能力探测

用自建特性检测页，从 PC 起 HTTP 服务让设备浏览器访问：

```bash
cd tools && python -m http.server 8000
adb shell "am start -a android.intent.action.VIEW -d 'http://<PC_IP>:8000/webview-test.html'"
```

检测页：[`../tools/webview-test.html`](../tools/webview-test.html)（52 项）

实测结论：**WebView = Chrome 37**，20/52 通过，**ES6 语法全灭**。

### 2.5 稳定性 / 崩溃取证

设备"假死"（服务全无）时的排查路径：

```bash
# ① 内核有没有重启（uptime 连续 = 只是 framework 软重启）
adb shell "cat /proc/uptime"
adb shell "ps | grep -E 'system_server|surfaceflinger'"   # PID 变化 = 重启过

# ② Dropbox 里的崩溃记录
adb shell "dumpsys dropbox --print"
#   看 SYSTEM_RESTART / SYSTEM_TOMBSTONE 条目

# ③ 崩溃调用栈（tombstone 会被复制进 dropbox）
adb shell "dumpsys dropbox --print" | grep -A60 "SFWatchDog"

# ④ 帧率征兆
adb shell "logcat -b main -d" | grep "SurfaceFlinger.*fps"
```

实测发现：**SurfaceFlinger 的看门狗线程 `SFWatchDog` 主动 `abort()`** →
init 重启 SurfaceFlinger → Android framework 软重启（内核不重启，adb 不断线）。

### 2.6 界面与输入自动化

| 目的 | 方法 |
|---|---|
| 截图 | `screencap -p /sdcard/a.png` + `adb pull` |
| 录屏 | `screenrecord --time-limit 5 /sdcard/a.mp4`（**注意编解码器尺寸限制**） |
| 模拟点击/滑动/按键 | `input tap / swipe / keyevent` |
| **取控件精确坐标** | `uiautomator dump /sdcard/ui.xml` 然后解析 `bounds` |
| 当前前台界面 | `dumpsys window \| grep mCurrentFocus` |
| 唤醒/解锁 | `input keyevent 224`（WAKEUP）+ `input swipe` 上滑 |
| 锁定状态判定 | 焦点变为 `android/com.android.internal.app.ResolverActivity` = 弹了选择器 |

### 2.7 写入能力探测

| 目的 | 方法 |
|---|---|
| 写系统设置 | `settings put system/secure/global <key> <value>`（需 `WRITE_SECURE_SETTINGS`） |
| 读回来验证 | `settings get <ns> <key>` |
| 文件传输 | `adb push / pull`（**adb 是 Windows 程序，路径必须用 `D:\...`**） |

---

## 三、技能的使用方式

这些技能是**给 AI 助手看的流程说明**。如果你在用支持技能的助手（如 WorkBuddy AI），
把 `SKILL.md` 放到技能目录即可自动加载：

```
~/.workbuddy-ai/skills/<skill-name>/SKILL.md
```

也可以直接当**操作手册**人工阅读执行——里面的命令都是实测可跑的。

---

## 四、相关文档

| 文档 | 内容 |
|---|---|
| [`../docs/06-Kiosk可行性实测.md`](../docs/06-Kiosk可行性实测.md) | Kiosk 全流程真机验证（8 项通过） |
| [`../docs/04-WebView兼容性实测.md`](../docs/04-WebView兼容性实测.md) | WebView 52 项检测完整表格 |
| [`../docs/05-稳定性事故报告.md`](../docs/05-稳定性事故报告.md) | 崩溃根因与规避 |
| [`../raw/README.md`](../raw/README.md) | 原始证据链索引 |
