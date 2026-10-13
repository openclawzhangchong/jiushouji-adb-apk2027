---
name: android-adb-connect
description: 在这台中文 Windows 上，用 adb 连接实体安卓设备（尤其是旧机/MTK 公版机）并做连通性诊断与设备画像。当用户说「插了个手机/平板」「打开了 USB 调试」「连不上 adb」「adb devices 是 unauthorized」「看下能不能连上这台安卓设备」「抓张手机截图」「这台设备是什么型号/什么来头」时使用。核心是把问题分成三层——USB 枚举层（PnP）、ADB 接口层（FF/42/01 签名）、授权层（unauthorized）——逐层定位，避免一上来就重装驱动。
agent_created: true
---

# 在 Windows 上用 adb 连接安卓设备

## 为什么需要这个 skill

「连不上手机」有五种完全不同的原因，用户和 AI 通常混为一谈，然后开始乱装驱动：

1. 手机 USB 调试根本没开
2. 电脑没有 adb 客户端
3. 手机没把 ADB 接口暴露到 USB 描述符里（只给了 MTP）
4. ADB 接口存在但缺驱动
5. 接口和驱动都正常，但手机端**没点授权** → `unauthorized`

这五种的修法完全不同。本 skill 的核心是**分层取证**，先确定卡在哪一层。

## 关键事实（实测，本机 Windows 10.0.19045）

- `adb` 不在任何默认路径。本机通过 `winget install Google.PlatformTools` 安装，
  实际二进制在：
  `%LOCALAPPDATA%\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe`
  `%LOCALAPPDATA%\Microsoft\WinGet\Links\` 下有 adb / fastboot 的软链，但**当前已打开的 shell 读不到新 PATH**，
  必须用完整路径调用，或新开 shell。
- **ADB 接口的判定签名**：`USB\Class_ff&SubClass_42&Prot_01`。
  在 `Get-PnpDeviceProperty -KeyName DEVPKEY_Device_CompatibleIds` 里看到它，就说明手机把 ADB 口暴露出来了。
- 现代 adb（platform-tools 24+，本机 37.0.1）**不需要** Google/OEM 专用驱动。
  接口被系统 WinUSB 驱动加载（`Service = WINUSB`）就够用。
- MTK 公版机（`ro.product.brand = alps`）常把 MI_00 / MI_01 的 BusReportedDeviceDesc
  都写成 "MTP"，**别信这个名字，只信 CompatibleIds**。

## 执行流程

### 第 0 步：先看 Windows 认不认

```powershell
Get-PnpDevice -PresentOnly | Where-Object { $_.InstanceId -like 'USB\*' } |
  Select-Object Status,Class,FriendlyName,InstanceId | Format-Table -AutoSize | Out-String -Width 200
Get-PnpDevice -PresentOnly | Where-Object { $_.Status -ne 'OK' } |   # 缺驱动的设备会在这里
  Select-Object Status,Class,FriendlyName,InstanceId | Format-Table -AutoSize
```

对可疑的 VID/PID 逐个读描述符：

```powershell
foreach ($p in @('DEVPKEY_Device_HardwareIds','DEVPKEY_Device_CompatibleIds','DEVPKEY_Device_Service','DEVPKEY_Device_BusReportedDeviceDesc')) {
  (Get-PnpDeviceProperty -InstanceId '<InstanceId>' -KeyName $p).Data
}
```

判读：
- 只有 `USB\MS_COMP_MTP` / `Class_ff&SubClass_ff` → 手机只给了 MTP，**ADB 口没开**
  → 让用户去手机开发者选项确认 USB 调试已开，并在 USB 模式里选「传输文件/MTP」而非「仅充电」，
  然后拔插重试。
- 有 `USB\Class_ff&SubClass_42&Prot_01` → ADB 口在，继续。
- 设备 `Status = Error` 且无 Class → 缺驱动，才需要装 Google USB Driver。

### 第 1 步：装 adb（若本机没有）

先查：`find /d /c/Users -iname adb.exe`；`winget --version` 是否可用。

⚠️ 安装软件属于系统改动，**先问用户再动手**。推荐：

```powershell
winget install --id Google.PlatformTools --accept-package-agreements --accept-source-agreements --disable-interactivity
```

### 第 2 步：握手

```powershell
$adb = "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe"
& $adb kill-server; & $adb start-server; & $adb devices -l
```

`kill-server` 在 daemon 未启动时会报 `cannot connect to daemon at tcp:5037`，
**这是正常的，不是错误**。

### 第 3 步：处理 unauthorized

出现 `unauthorized` → 让用户看**手机屏幕**点「允许 USB 调试」（勾「一律允许」）。
旧机器上这个弹窗可能藏在通知栏里。

轮询等待（避免用户点了之后还要再喊一次）：

```powershell
for ($i=0; $i -lt 50; $i++) {
  $line = (& $adb devices 2>&1 | Select-Object -Skip 1 | Where-Object { $_ -match '\S' }) -join ' '
  if ($line -match '\sdevice\b') { break }
  Start-Sleep -Seconds 2
}
& $adb devices -l
```

始终点不动？让用户执行：开发者选项 →「撤销 USB 调试授权」→ 拔插重来。
老设备（Android < 4.2.2）没有 RSA 弹窗，直接就应该是 `device`。

### 第 4 步：取证与交付

```powershell
# 设备档案
foreach ($p in @('ro.product.brand','ro.product.model','ro.product.device','ro.build.version.release','ro.build.version.sdk','ro.board.platform','ro.serialno','ro.build.fingerprint','ro.secure','ro.debuggable')) {
  "$p = " + ((& $adb shell getprop $p 2>&1) -join '').Trim()
}
& $adb shell wm size; & $adb shell wm density
& $adb shell df -h /data /sdcard
& $adb shell dumpsys battery | Select-String 'level|status|temperature|voltage'
& $adb shell pm list packages | Measure-Object   # 包总数
& $adb shell pm list packages -3                  # 第三方包，空=纯原生

# 截图取证（最强的「连上了」证明）
& $adb shell screencap -p /sdcard/_shot.png
& $adb pull /sdcard/_shot.png <本地路径>\phone-screenshot.png
& $adb shell rm /sdcard/_shot.png
```

拿到截图后用 Read 工具**看图**，才能判断这是什么设备（公版 AOSP？定制机？）。

## 坑

- **禁止从 Bash 里调 PowerShell**（本机安全策略会直接拒绝），PowerShell 走 PowerShell 工具。
- PowerShell 里 `& $adb ... 2>&1` 会把 adb 写到 stderr 的正常信息（如 `1 file pulled`）
  包装成 `NativeCommandError` 红字。**这是噪音不是失败**，看 stdout 里的真实结果。
- adb 输出与 PowerShell 中文混排时会 GBK/UTF-8 乱码（`2023骞?08鏈?`）。
  只信任 ASCII 字段（getprop 值、型号、版本号）；中文描述信息别直接引用，用截图确认。
- `ro.serialno = 0123456789ABCDEF` 是 MTK 默认占位值，**不唯一**，别当设备唯一标识用。
- `adb shell which` 在 Android 5.x 上不存在，用 `ls` 或 `command -v` 代替。
- `ls /sbin/su` 返回 `Permission denied` 说明的是目录不可读，**不能据此判断有无 root**，
  要看 `ro.secure` / `ro.debuggable`。

### 旧 Android（4.x / 5.x）设备端命令可用性

实测 Android 5.1（API 21）：

| 有 | 无 |
|---|---|
| `settings` `input` `am` `pm` `screencap` `screenrecord` `dumpsys` `grep` | `cmd`（6.0+ 才有）`su` `head` `nproc` `tar` `curl` `wget` `toybox` `busybox` |

**写脚本前先 `command -v <cmd>` 探一遍**，否则 `dumpsys meminfo | head` 会直接
`/system/bin/sh: head: not found`。

### 别踩的 shell 坑

- Android 的 `sh` 是 mksh/toolbox，**不支持 `(a || b)` 这种括号分组**：
  `cat f 2>/dev/null || echo (always-on)` 会报 `syntax error: 'always-on' unexpected`。
  用 `if` 语句或分开两条命令。
- `/proc/loadavg` 在旧 MTK 内核（tickless idle + hotplug）上**可能是坏的**：
  实测连续采样 3 次数值完全不变（7.11/7.15/7.19），而 `top` 显示全进程 0% CPU。
  **不要用 loadavg 判断负载**，用 `top` 或实际响应速度。

### 多核不等于多核在跑

判定真实可用核心要读 sysfs，不要数 `cpuinfo`：

```
cat /sys/devices/system/cpu/present   # 硬件有几个，如 0-3
cat /sys/devices/system/cpu/online    # 实际在线的，如 0  <- 可能只剩 1 个
cat /sys/devices/system/cpu/cpu1/online   # 0 = 该核被关
```

MTK 的 `hotplug` governor 空闲时会关核，**某些 ODM 固件只留 1 核**。
`cpuinfo` 的 processor 行数还会因输出截断而数不准，别用它下结论。

### 第 5 步（可选）：设备画像 / 识别白牌机

用户问「这是什么设备」时，按这个顺序挖。**白牌机的身份藏在基带串里**。

```powershell
# 平台代号线索：基带项目串
& $adb shell getprop gsm.project.baseband
& $adb shell getprop gsm.project.baseband.2

# 设备类别与面板朝向
& $adb shell getprop ro.build.characteristics   # tablet / phone / default
& $adb shell getprop ro.sf.hwrotation           # 180 = 面板在机壳内倒装

# 是否产线统一烧录（看厂商包安装时间是否扎堆）
& $adb shell "dumpsys package com.fw.upgrade | grep firstInstallTime"

# 真实核心数
& $adb shell cat /sys/devices/system/cpu/present
& $adb shell cat /sys/devices/system/cpu/online

# 厂商定制包/产线测试程序（白牌机特征）
& $adb shell pm list packages | Select-String 'factory|pcba|cust|upgrade|mediatek'
```

**识别套路**：拿到 `gsm.project.baseband`（如 `LOKYEE82_WET_KK_GPRS_HW`）后，
把里面的项目代号（`LOKYEE82` ≈ `joya82`）丢去搜 ROM 库
（romdevelopers.com、XDA、needrom），常能直接搜到**同平台的公开固件**，从而确定硬件平台。
`_WET_KK` 里的 `KK` 指 KitKat——**系统版本比基带代号新是常态**，说明 ODM 复用老 BSP 升级了系统。

白牌机常见特征（命中 2 条以上基本可定性）：
- `ro.product.brand = alps`（MTK 公版）
- 有 `PcbaTestApp` / `RgkFactory` / `com.ty.*` 等产线或客户定制包
- 厂商包 `firstInstallTime` 集中在同一分钟内（产线烧录）
- 无任何 `com.google.*` 包
- `ro.serialno` 是 `0123456789ABCDEF` 之类的占位值

### 判断这台设备能不能做「开机自启 / Kiosk 终端」

用户要往设备上装自启 APK 时，先探这几项，直接决定方案选哪条：

```powershell
# 命令是否存在（Android 5.1 没有 cmd！）
& $adb shell "command -v dpm"          # 有 → Device Owner / Kiosk 可行
& $adb shell "command -v screenrecord"

# 当前有没有 Device Owner / Device Admin
& $adb shell "dumpsys device_policy"
#   Enabled Device Admins (User 0): 后面为空 → 还能设 Device Owner

# pm 支持哪些子命令（5.1 上【没有】 set-home-activity）
& $adb shell pm

# 屏幕超时（做常亮要改）
& $adb shell "settings get system screen_off_timeout"
```

**Android 5.1 上的关键结论（实测）**：

| 结论 | 影响 |
|---|---|
| **没有 `cmd` 命令**（6.0 才引入） | `cmd package set-home-activity` 用不了，只能用 `pm` / `am` |
| **`pm` 没有 `set-home-activity`** | 换桌面得靠声明 HOME + `am start` 触发选择器，或 `pm disable-user` 原桌面 |
| **`dpm` 存在** | 可 `dpm set-device-owner <组件>` 做真正的 Kiosk 锁定 |
| `dpm set-device-owner` 要求设备无已登录账号 | **趁设备还是全新的时候做，登录账号后就设不了了** |
| **应用装完处于 `stopped` 状态** | 收不到 `BOOT_COMPLETED`，**必须 `am start` 启动一次**才能自启 |

### 屏幕朝向与传感器坐标系

`ro.sf.hwrotation = 180`（面板在机壳内倒装）**不一定**导致传感器轴向翻转。
实测方法：让设备平放，读加速度计：

```powershell
& $adb shell "dumpsys sensorservice" | Select-String 'ACCELEROMETER'
# z ≈ +9.8~10 且 x/y ≈ 0 → 坐标系正常，没有翻转
```

本机实测 `(0.1, -0.2, 10.0)` → 正常。**别想当然认为倒装屏一定翻轴，实测为准。**

## 三层判读速查

| 现象 | 层 | 处理 |
|---|---|---|
| PnP 里根本没有该 VID/PID | USB 枚举 | 换线/换口，手机选 MTP 模式 |
| 只有 MTP 接口，无 FF/42/01 | ADB 接口 | 手机上确认 USB 调试已开 + 重新拔插 |
| 有 FF/42/01，但 Status=Error | 驱动 | 装 Google USB Driver |
| `adb devices` 空但接口正常 | 客户端 | 装 platform-tools |
| `unauthorized` | 授权 | 手机端点允许 |
| `offline` | 连接 | `adb kill-server` + 拔插 |
