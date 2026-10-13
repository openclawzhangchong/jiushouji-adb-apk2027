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
| `settings` `input` `am` `pm` `screencap` `screenrecord` `dumpsys` `logcat` `monkey` `dpm` `getevent` `sendevent` `setprop` `ps` `top` `ip` `ifconfig` `ping` `netstat` `cp` `mv` `rm` `ls` `date` `reboot` `service` `grep` `awk` | `cmd`（6.0+ 才有）`su` **`sed`** `head` `nproc` `tar` `curl` `wget` `toybox` `busybox` |

⚠️ **注意 `grep` 有、`sed` 没有** —— 这个组合最容易踩坑，
常见写法 `dumpsys package | sed -n '/x/,/y/p'` 会直接失败。用 `awk` 或 `grep` 替代。

**写脚本前先 `command -v <cmd>` 探一遍**，否则 `dumpsys meminfo | head` 会直接
`/system/bin/sh: head: not found`。

### 别踩的 shell 坑

- Android 的 `sh` 是 mksh/toolbox，**不支持 `(a || b)` 这种括号分组**：
  `cat f 2>/dev/null || echo (always-on)` 会报 `syntax error: 'always-on' unexpected`。
  用 `if` 语句或分开两条命令。
- **`/proc/loadavg` 基线被内核线程垫高**（MTK 平台常见）：实测恒显示 ≈7，
  一度误判为「内核计数坏了」。**真相是 7 个内核线程长期处于 `D` 状态**
  （`wdtk-0~3` / `hang_detect` / `pmic_thread_kth` / `bat_thread_kthr`），
  它们会被计入 loadavg。
  → **别读绝对值判断负载**；可以做增量对比，或直接看 `top`。
  查证方法：
  ```powershell
  & $adb shell 'top -n 1 -m 400' | Select-String ' D '   # 看哪些进程长期 D 状态
  ```

### 「只有 1 核在跑」是故障还是省电策略？—— 必须跑负载才能定性

**别看到 `online = 0` 就说性能差或硬件有问题。** 先读 sysfs：

```
cat /sys/devices/system/cpu/present        # 硬件有几个，如 0-3
cat /sys/devices/system/cpu/online         # 实际在线的，如 0
cat /sys/devices/system/cpu/cpu1/online    # 0 = 该核被关
```

然后**施加负载再读一次**。有负载时核数回升 = hotplug 正常，纯省电策略。

```powershell
# 1) 推测试脚本到设备（多行脚本走文件，避免引号地狱）
& $adb push tools/cpu-hotplug-test.sh /data/local/tmp/
# 2) 跑
& $adb shell sh /data/local/tmp/cpu-hotplug-test.sh
```

脚本逻辑：起 8 个忙循环 → `sleep 4` 读 online → `sleep 6` 再读 → kill 后再读。

**实测参考（MT6582 / Android 5.1）**：

| 状态 | online | 频率 |
|---|---|---|
| 空闲 | `0` | 598 MHz |
| 负载 4 s 后 | `0-3` ← **4 核全开** | 1300 MHz |
| 释放负载后 | `0-2` ← 逐步关核 | 598 MHz |

再读 governor 参数定性：

```powershell
& $adb shell 'HP=/sys/devices/system/cpu/cpufreq/hotplug; for p in is_cpu_hotplug_disable cpu_num_limit cpu_num_base cpu_up_threshold cpu_rush_boost_num cpu_input_boost_num; do echo "$p = $(cat $HP/$p 2>/dev/null)"; done'
```

- `is_cpu_hotplug_disable = 0` → 热插拔开着
- `cpu_num_limit = 4` → 上限 4 核
- `cpu_num_base` 为 1 → 空闲最少留 1 核（正常）

**判定**：核数随负载变化 = 正常省电策略；负载下仍不回升 = 真有问题（内核禁用或 ODM 锁死）。

**给开发的提醒**：即使 4 核可用，**冷启动阶段通常仍是 1 核**（还没达到 `cpu_up_threshold`），
所以启动路径要按单核设计。

> `cpuinfo` 的 processor 行数会因输出截断而数不准，**别用它下结论**。

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

# ★ 关键：shell 到底被授予了哪些权限（决定哪些操作可行）
& $adb shell "dumpsys package com.android.shell" |
    Select-String 'android.permission' | Sort-Object -Unique

# 屏幕超时（做常亮要改）
& $adb shell "settings get system screen_off_timeout"
```

**Android 5.1 上的关键结论（全部实测）**：

| 结论 | 影响 |
|---|---|
| **没有 `cmd` 命令**（6.0 才引入） | `cmd package set-home-activity` 用不了，只能用 `pm` / `am` |
| **`pm` 没有 `set-home-activity`** | 无法用命令行直接指定默认桌面 |
| **`pm disable-user` 全部失败** | shell **没有** `CHANGE_COMPONENT_ENABLED_STATE` → **无法禁用原桌面** |
| **`dpm` 存在且权限可用** | shell **有** `MANAGE_DEVICE_ADMINS` → `dpm set-device-owner` 可行 |
| `dpm set-device-owner` 要求设备无已登录账号 | **趁设备还是全新的时候做，登录账号后就设不了了** |
| **应用装完处于 `stopped` 状态** | 收不到 `BOOT_COMPLETED`，**必须 `am start` 启动一次**才能自启 |

#### 两个「零风险试探权限」的技巧（很实用，值得记）

想知道某操作**能不能做**，又不想真的改动设备，用不存在的目标去试探——
错误信息会告诉你卡在权限还是卡在目标：

```powershell
# 试探 pm disable-user：包不存在 → 报 Unknown package（说明先查包再查权限）
& $adb shell pm disable-user --user 0 com.example.fake
#   Error: java.lang.IllegalArgumentException: Unknown package: com.example.fake

# 用真实存在但无害的包试探（推荐！比拿目标包试安全）
& $adb shell pm disable-user --user 0 com.android.galaxy4     # 动态壁纸，无副作用
#   Error: java.lang.SecurityException: Permission Denial:
#          attempt to change component state from pid=..., uid=2000   ← 权限不足，实锤

# 试探 dpm set-device-owner：组件不存在 → 报 Unknown admin（说明权限检查已通过）
& $adb shell dpm set-device-owner com.example.fake/.FakeReceiver
#   Error: Unknown admin: ComponentInfo{com.example.fake/...}          ← 权限 OK，只是组件不存在
```

**判读规则**：
- 报 `SecurityException: Permission Denial` → **权限不够**，此路不通
- 报 `Unknown package` / `Unknown admin` / `Unknown component` → **权限够了**，只是目标不存在 → 换真目标就能成

**优先用无害包试探**（动态壁纸、演示用包），别拿真正要动的目标去试。
试探完记得把无害包恢复原状（本例中它本来就没被改成功）。

#### Kiosk 全流程验证清单（Android 5.1 实测跑通）

用户要往设备上装"开机自启 + 防退出"的应用时，按这 8 步逐项验证：

| # | 验证项 | 命令 / 判定 |
|---|---|---|
| 1 | 能否安装 | `adb install -r app.apk` → `Success`；检查 `abi=armeabi-v7a` |
| 2 | 是否处于 stopped 状态 | `dumpsys package <pkg> \| grep 'User 0'` → `stopped=true notLaunched=true` |
| 3 | 启动一次清除 stopped | `am start -n <pkg>/.MainActivity` → 再查应为 `stopped=false` |
| 4 | 能否接管桌面 | 按 Home → 焦点变成 `android/com.android.internal.app.ResolverActivity` 即弹出了选择器 |
| 5 | 开机自启 | `adb reboot` → 等 boot_completed → 查 `logcat -d \| grep BOOT_COMPLETED` |
| 6 | Device Owner | `dpm set-device-owner <pkg>/.AdminReceiver` → `Success: Device owner set to package ...` |
| 7 | LockTask 锁定 | 应用调 `startLockTask()` 后，按 Home/Back 应**无法离开** |
| 8 | 卸载保护 | `pm uninstall <pkg>` → `Failure [DELETE_FAILED_DEVICE_POLICY_MANAGER]` |

**⚠️ 三个必踩的坑**：

1. **`am broadcast` 在 5.1 不支持 `-p`**，报 `Error: Unknown option: -p`。必须用 `-n` 指定组件：
   ```bash
   am broadcast -a android.intent.action.BOOT_COMPLETED -n <pkg>/.BootReceiver
   ```
2. **Device Owner 不可逆**。只能恢复出厂设置清除，且**恢复出厂会清掉 ADB 授权密钥**
   → 重启后 `adb devices` 变回 `unauthorized`，需要人工在设备上点一次「允许」。
   如果只是想验证，**先准备好自毁手段**（让测试 APK 带一个调 `wipeData()` 的按钮），测完自己恢复。
3. **`pm disable-user` 不可用**（见上），所以**没法自动干掉原桌面** →
   装完会有 2 个 HOME，第一次按 Home 必须人工点「始终」。
   要零人工，只能走 Device Owner + `addPersistentPreferredActivity`。

#### 锁屏（Keyguard）在 5.1 上关不掉

`settings put secure lockscreen.disabled 1` **实测无效**（写进去了但锁屏照旧）。
`DevicePolicyManager.setKeyguardDisabled()` 是 API 23+，5.1 用不了。

→ 应用侧只能靠 Window flags：
```java
getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
```
无密码锁屏（滑动解锁）时 `FLAG_DISMISS_KEYGUARD` 有效。

#### framework 软重启是天然的容错机制

实测：SurfaceFlinger 看门狗 abort 会导致 Android framework 软重启
（`system_server` PID 变化，内核不重启，adb 不断线）。
**重启后系统会重新广播 `BOOT_COMPLETED`** → 注册了该广播的应用会被自动拉起。

→ 给 Kiosk 应用的建议：**即使已经是默认桌面，也务必保留 `BOOT_COMPLETED` 接收器**，
这是免费的崩溃自恢复。

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
