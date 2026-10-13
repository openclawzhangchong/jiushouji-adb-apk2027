# 原始证据链索引

本目录是**全部真机实测的原始输出**，未经加工。

**用途**：文档里的每条结论都能在这里找到对应的原始数据。
如果对某个结论有疑问，直接看原始文件，不用相信任何转述。

> ⚠️ 这些文件是**只读证据**，请不要修改。重新采集会覆盖，那样就失去了证据价值。

---

## 一、按结论索引

### 结论 1：CPU「4 核只开 1 核」是省电策略，不是故障

| 文件 | 证明了什么 |
|---|---|
| [`cpu-hotplug-test-result.txt`](cpu-hotplug-test-result.txt) | 负载复测完整输出：空闲 `online=0` → 负载 `online=0-3`（4 核全开）→ 释放后逐步关核 |
| [`cpu-hotplug-governor-config.txt`](cpu-hotplug-governor-config.txt) | governor 全部参数：`is_cpu_hotplug_disable=0`、`cpu_num_limit=4`、`cpu_up_threshold=90` |
| [`cpuinfo.txt`](cpuinfo.txt) | CPU 基本信息（MT6582，ARMv7） |

复现脚本：[`../tools/cpu-hotplug-test.sh`](../tools/cpu-hotplug-test.sh)

### 结论 2：`/proc/loadavg` 恒为 ~7 是内核线程垫高的，不是计数错误

| 文件 | 证明了什么 |
|---|---|
| [`dstate-threads.txt`](dstate-threads.txt) | **7 个内核线程长期处于 D 状态**（`wdtk-0~3` / `hang_detect` / `pmic_thread_kth` / `bat_thread_kthr`），正好对应 loadavg ≈7 |

### 结论 3：`pm disable-user` 不可行（shell 权限不足）

| 文件 | 证明了什么 |
|---|---|
| [`pm-disable-user-denied.txt`](pm-disable-user-denied.txt) | 实测报错 `SecurityException: Permission Denial: attempt to change component state ... uid=2000` |
| [`shell-permissions.txt`](shell-permissions.txt) | shell 被授予的 **66 条权限**，其中**没有** `CHANGE_COMPONENT_ENABLED_STATE` |
| [`dumpsys-package-shell.txt`](dumpsys-package-shell.txt) | shell 包完整信息（上表的原始来源） |

### 结论 4：`dpm set-device-owner` 可用

| 文件 | 证明了什么 |
|---|---|
| [`dpm-set-device-owner-probe.txt`](dpm-set-device-owner-probe.txt) | 用不存在的组件试探，返回 `Unknown admin`（而非权限拒绝）→ 权限检查通过 |
| [`dpm-usage.txt`](dpm-usage.txt) | `dpm` 命令的用法 |
| [`device-owner-state.txt`](device-owner-state.txt) | **真的设成 Device Owner 后**的 `dumpsys device_policy` 输出 |

### 结论 5：Kiosk 全流程可用（8 项验证）

| 文件 | 证明了什么 |
|---|---|
| [`kiosk-app-package-info.txt`](kiosk-app-package-info.txt) | 测试 APK 的组件注册（HOME / BOOT_COMPLETED / DEVICE_ADMIN 三件套） |
| [`kiosktest-applog.txt`](kiosktest-applog.txt) | 应用自身日志（Device Owner 状态变化、LockTask 调用） |
| [`boot-autostart-evidence.txt`](boot-autostart-evidence.txt) | **开机自启完整日志链路**：`broadcast BOOT_COMPLETED` → `Start proc` → `onReceive` → `START MainActivity` |
| [`post-factory-reset-verification.txt`](post-factory-reset-verification.txt) | 恢复出厂后核对：包总数 84（与初始一致）、无 Device Owner、`/data` 149.3M |

### 结论 6：SurfaceFlinger 看门狗崩溃导致 framework 软重启

| 文件 | 证明了什么 |
|---|---|
| [`dropbox-print.txt`](dropbox-print.txt) | Dropbox 全部 3 条记录（`SYSTEM_RESTART` + 2 条 `SYSTEM_TOMBSTONE`） |
| [`dropbox-sfwatchdog-tombstone.txt`](dropbox-sfwatchdog-tombstone.txt) | **`SFWatchDog` 主动 `abort()`（SIGABRT）的完整调用栈** |
| [`dropbox-system-restart.txt`](dropbox-system-restart.txt) | `SYSTEM_RESTART` 条目全文（含**编译机主机名 `twd-PowerEdge-R720xd`**） |
| [`surfaceflinger-fps.txt`](surfaceflinger-fps.txt) | 崩溃前后帧率：**单帧耗时 10~22 秒** |
| [`pid-after-restart.txt`](pid-after-restart.txt) | 重启后 `system_server` PID 612→4206、`surfaceflinger` 133→3822 |
| [`boot-completed-after-restart.txt`](boot-completed-after-restart.txt) | **框架软重启后重新广播 `BOOT_COMPLETED`**（Kiosk 自恢复的依据） |

### 结论 7：设备规格与外设

| 文件 | 证明了什么 |
|---|---|
| [`getprop.txt`](getprop.txt) | **完整系统属性**（未脱敏，含网络配置）。设备身份、平台、ABI、屏幕、WiFi 等绝大部分结论的来源 |
| [`pm-list-features.txt`](pm-list-features.txt) | 系统声明的全部 feature |
| [`pm-list-packages.txt`](pm-list-packages.txt) | 84 个已安装包 |
| [`sensorservice.txt`](sensorservice.txt) | **只有 3 个传感器**（加速度/距离/光线）—— 证明"声明有指南针但实际没有" |
| [`dumpsys-media-camera.txt`](dumpsys-media-camera.txt) | **只有 1 个摄像头设备** —— 证明"声明有前置摄像头但实际没有" |
| [`meminfo.txt`](meminfo.txt) | 内存 1.93 GB |
| [`df.txt`](df.txt) | 存储分区与占用 |
| [`mount.txt`](mount.txt) | 挂载表 |
| [`wm-size.txt`](wm-size.txt) / [`wm-density.txt`](wm-density.txt) | 屏幕 720×1280 @ 280dpi |
| [`dumpsys-battery.txt`](dumpsys-battery.txt) | 电池状态 |

---

## 二、采集环境

| 项 | 值 |
|---|---|
| 采集日期 | 2026-10-13 |
| 电脑侧 | Windows 10.0.19045，adb = platform-tools 37.0.1 |
| 设备 | `XXB` / `b906`，Android 5.1 (API 21) |
| 连接方式 | USB + adb（已授权） |

---

## 三、如何复现

```bash
ADB="$LOCALAPPDATA/Microsoft/WinGet/Packages/Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe/platform-tools/adb.exe"

# 系统属性
"$ADB" shell getprop > getprop.txt

# 外设能力
"$ADB" shell "pm list features" > pm-list-features.txt
"$ADB" shell "dumpsys sensorservice" > sensorservice.txt
"$ADB" shell "dumpsys media.camera" > dumpsys-media-camera.txt

# shell 权限
"$ADB" shell "dumpsys package com.android.shell" > dumpsys-package-shell.txt

# 崩溃记录
"$ADB" shell "dumpsys dropbox --print" > dropbox-print.txt

# CPU 热插拔（用脚本，别手敲）
"$ADB" push ../tools/cpu-hotplug-test.sh /data/local/tmp/
"$ADB" shell sh /data/local/tmp/cpu-hotplug-test.sh > cpu-hotplug-test-result.txt
```

> ⚠️ 设备端**没有 `sed` / `head`**，管道处理请用 `grep` / `awk`。

---

## 四、相关文档

| 文档 | 内容 |
|---|---|
| [`../docs/01-设备评估报告.md`](../docs/01-设备评估报告.md) | 结论汇总（含本目录所有证据的解读） |
| [`../docs/04-WebView兼容性实测.md`](../docs/04-WebView兼容性实测.md) | WebView 检测（原始截图在 `../assets/`） |
| [`../docs/05-稳定性事故报告.md`](../docs/05-稳定性事故报告.md) | 崩溃分析 |
| [`../docs/06-Kiosk可行性实测.md`](../docs/06-Kiosk可行性实测.md) | Kiosk 验证 |
| [`../skills/README.md`](../skills/README.md) | 探测方法速查 |
