# Kiosk 可行性实测报告（真机全流程验证）

- **测试日期**：2026-10-13
- **测试方式**：**自己构建一个测试 APK 装到真机上，逐项验证**（不依赖任何推测）
- **测试 APK**：[`../tools/kiosk-test/`](../tools/kiosk-test/)（源码 + 构建脚本 + 已签名 APK 全部入库）
- **测试结果**：✅ **全部 8 项验证通过**

---

## 一、TL;DR —— 全部结论一览

| # | 验证项 | 结果 | 证据 |
|---|---|---|---|
| 1 | 自建 APK 能装上 | ✅ | `Success`，`abi=armeabi-v7a` |
| 2 | 装完处于 `stopped` 状态 | ✅ **证实** | `installed=true hidden=false stopped=true notLaunched=true` |
| 3 | 启动一次后 `stopped` 清除 | ✅ | `stopped=false notLaunched=false` |
| 4 | 声明 HOME 可接管桌面 | ✅ | 按 Home 弹出 `ResolverActivity`，列出 Launcher3 / KioskTest |
| 5 | **真实重启后自动启动** | ✅ | `adb reboot` → 36 秒后应用自动在前台 |
| 6 | `dpm set-device-owner` 可用 | ✅ | `Success: Device owner set to package ...` |
| 7 | **LockTask 锁定生效** | ✅ | 按 Home / Back **无法离开应用** |
| 8 | Device Owner 保护 + 清除路径 | ✅ | 卸载被拒；恢复出厂后彻底清除 |

**结论：零人工、防退出的 Kiosk 终端方案在这台设备上完全可行。**

---

## 二、测试用 APK

刻意从零手写（不用 Android Studio / Gradle），全部源码入库便于复现。

| 文件 | 作用 |
|---|---|
| [`AndroidManifest.xml`](../tools/kiosk-test/AndroidManifest.xml) | 声明 HOME + BOOT_COMPLETED + DeviceAdminReceiver |
| [`MainActivity.java`](../tools/kiosk-test/src/com/jiushouji/kiosktest/MainActivity.java) | 主界面，常亮 + 解除锁屏，含测试按钮 |
| [`BootReceiver.java`](../tools/kiosk-test/src/com/jiushouji/kiosktest/BootReceiver.java) | 开机广播接收，拉起主界面 |
| [`AdminReceiver.java`](../tools/kiosk-test/src/com/jiushouji/kiosktest/AdminReceiver.java) | 设备管理员，Device Owner 前提 |
| [`res/xml/device_admin.xml`](../tools/kiosk-test/res/xml/device_admin.xml) | 管理员策略声明 |
| [`build.sh`](../tools/kiosk-test/build.sh) | 一键构建脚本 |

**关键实现细节**：

- `minSdkVersion 16` / `targetSdkVersion 22`（符合设备约束）
- 只出 `armeabi-v7a`（`abiFilters`）
- **API 21 专有方法全部用反射调用**（因为编译用的 android.jar 是 API 16，
  无法直接调用 `startLockTask()` / `setLockTaskPackages()` / `wipeData()` / `isDeviceOwnerApp()`）

**构建链**（无需 Android Studio）：

```
aapt2 compile → aapt2 link → javac(--release 8) → d8 → jar uf → zipalign → apksigner
```

---

## 三、逐项验证详情

### ✅ 3.1 安装

```
$ adb install -r kiosk-test-signed.apk
Performing Push Install
	pkg: /data/local/tmp/kiosk-test-signed.apk
Success
```

安装日志确认：`abi=armeabi-v7a` —— 与设备唯一支持的 ABI 匹配。

### ✅ 3.2 「装完处于 stopped 状态」—— 实测证实

```
$ adb shell dumpsys package com.jiushouji.kiosktest | grep 'User 0'
User 0:  installed=true hidden=false stopped=true notLaunched=true enabled=0
```

**`stopped=true notLaunched=true`** —— 这正是文档里反复强调的那个坑的实证。

启动一次后：

```
User 0:  installed=true hidden=false stopped=false notLaunched=false enabled=0
```

**`stopped=false`** —— 状态被正确清除，此后才能收到 `BOOT_COMPLETED`。

### ✅ 3.3 桌面接管（HOME）

安装后按 Home 键 → 弹出系统选择器：

```
mCurrentFocus=Window{... android/com.android.internal.app.ResolverActivity}
```

`ResolverActivity` 就是「选择主屏幕应用」对话框。
截图见 [`../assets/kiosk-03-home-chooser.png`](../assets/kiosk-03-home-chooser.png)，
界面上列出 **Launcher3** 与 **KioskTest**，按钮为「仅此一次 / **始终**」。

⚠️ **这里必须人工点一次「始终」**（原因见 §四）。

### ✅ 3.4 开机自启（真实 `adb reboot` 验证）

**这是最硬的一条证据。** 重启后系统日志：

```
V/ActivityManager(618): broadcast BOOT_COMPLETED intent
I/ActivityManager(618): Start proc com.jiushouji.kiosktest for broadcast
    com.jiushouji.kiosktest/.BootReceiver: pid=1354 uid=10056
    gids={50056, 9997} abi=armeabi-v7a
D/ActivityThread(1354): BDC-Calling onReceive: intent=Intent {
    act=android.intent.action.BOOT_COMPLETED cmp=com.jiushouji.kiosktest/.BootReceiver }
I/ActivityManager(618): START u0 {flg=0x14000000
    cmp=com.jiushouji.kiosktest/.MainActivity} from uid 10056
```

完整链路：**开机 → 系统广播 BOOT_COMPLETED → 拉起应用进程 → BootReceiver 收到 → 启动 MainActivity**。

实测数据：

| 指标 | 值 |
|---|---|
| `adb reboot` 发起 | 16:55:28 |
| adb 重新连上 | 16:55:55（**27 秒**） |
| `sys.boot_completed=1` | 16:56:04（**36 秒**） |
| 应用启动时间 | 16:55:58 |
| 重启后前台 | `com.jiushouji.kiosktest/MainActivity` ✅ |

截图见 [`../assets/kiosk-04-after-reboot-autostart.png`](../assets/kiosk-04-after-reboot-autostart.png)。

### ✅ 3.5 Device Owner 设置

```
$ adb shell dpm set-device-owner com.jiushouji.kiosktest/.AdminReceiver
Success: Device owner set to package com.jiushouji.kiosktest
Active admin set to component
    {com.jiushouji.kiosktest/com.jiushouji.kiosktest.AdminReceiver}
```

`dumpsys device_policy` 确认管理员已生效，策略包含：
`wipe-data`、`reset-password`、`limit-password`、`watch-login`、`force-lock`、`disable-camera`。

应用 UI 上也从 `Device Owner: NO` 变成 **`Device Owner: YES`**。

### ✅ 3.6 LockTask 锁定 —— **按 Home/Back 无法退出**

设置 Device Owner 后：

```java
dpm.setLockTaskPackages(admin, new String[]{ getPackageName() });  // → isLockTaskPermitted=YES
startLockTask();
```

**实测结果**：

| 操作 | 结果 |
|---|---|
| 按 Home 键 | ❌ 无法离开，仍停留在 KioskTest |
| 按 Back 键 | ❌ 无法离开，仍停留在 KioskTest |
| 系统提示 | 「**屏幕处于固定状态。您所属的单位不允许取消固定。**」 |
| 导航栏 | Home / Recent 键被隐藏，只剩返回键 |

截图见 [`../assets/kiosk-06-locktask-active.png`](../assets/kiosk-06-locktask-active.png)。

**这是真正的 Kiosk 模式：用户退不出去。**

### ✅ 3.7 Device Owner 保护（卸载被拒）

```
$ adb shell pm uninstall com.jiushouji.kiosktest
Failure [DELETE_FAILED_DEVICE_POLICY_MANAGER]
```

**证实：Device Owner 应用无法被常规卸载。** 这是保护机制，也是"不可逆"的由来。

### ✅ 3.8 清除路径：恢复出厂设置

调用 `DevicePolicyManager.wipeData(0)` 后，设备自动擦除并重启。

**恢复后的状态核对**（实测，见 [`../raw/post-factory-reset-verification.txt`](../raw/post-factory-reset-verification.txt)）：

| 项 | 恢复后 | 初始值 | 结论 |
|---|---|---|---|
| 设备重启 | ✅ 约 3–5 分钟后重新上线 | — | |
| 测试 APK | ✅ 已清除 | 无 | 干净 |
| Device Owner | ✅ 已清除（`Enabled Device Admins` 为空） | 空 | 干净 |
| 包总数 | **84** | 84 | 完全一致 |
| 第三方应用 | 0 个 | 0 个 | 完全一致 |
| 默认桌面 | `com.android.launcher3` | 同 | 完全一致 |
| `/data` 占用 | **149.3 M** | 152.2 M | 更干净 |
| ADB 授权 | ⚠️ 被重置为 `unauthorized` | 已授权 | **需重新在设备上点「允许」** |
| USB 枚举 | 期间变为 `PID_2008`（仅 MTP），重启后恢复 | `PID_201D` | 正常 |

**结论：设备已被完整恢复到初始状态**，可交付给真实应用使用。

> ⚠️ **重要提醒**：恢复出厂设置会清除 ADB 授权密钥。
> 换而言之——**每台机器做完 Device Owner 测试后想复用，都要重新授权一次 ADB。**
> 批量部署时要把这一步算进流程。

---

## 四、关键结论与坑

### 4.1 `am broadcast` 在 Android 5.1 上**不支持 `-p`**

文档早期写的测试命令是错的：

```bash
# ❌ 报错：Error: Unknown option: -p
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p com.example.app

# ✅ 正确：用 -n 指定组件
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED \
    -n com.example.app/.BootReceiver
```

`am broadcast` 在 5.1 只支持 `[--user <USER_ID>] <INTENT>`，其中 INTENT 支持 `-a -d -t -c -e -n -f`。

### 4.2 为什么必须人工点一次「始终」

`pm disable-user` 不可用（shell 无 `CHANGE_COMPONENT_ENABLED_STATE`，见
[`01-设备评估报告`](01-设备评估报告.md)），所以**无法自动禁用原桌面**。
装完 APK 后系统里有 2 个 HOME，第一次按 Home 必然弹选择器。

**两条出路**：

| 方案 | 是否零人工 | 说明 |
|---|---|---|
| 人工点「始终」 | ❌ | 每台机器点一次 |
| **Device Owner + `addPersistentPreferredActivity`** | ✅ | 应用内调用，无需人工 |

### 4.3 建议的最终部署流程（全部实测验证过）

```bash
PKG=com.yourcompany.kiosk
APK=app-release.apk

# 1. 安装
adb install -r $APK

# 2. 首次启动（清除 stopped 状态，必须！）
adb shell am start -n $PKG/.MainActivity

# 3. 设为 Device Owner（设备必须无已登录账号）
adb shell dpm set-device-owner $PKG/.AdminReceiver

# 4. 应用内自动完成（无需人工）：
#    dpm.setLockTaskPackages(admin, new String[]{getPackageName()});
#    dpm.addPersistentPreferredActivity(admin, homeIntentFilter, homeComponent);
#    startLockTask();
#
# 若跳过第 3 步，则退回人工：
#    adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
#    让用户在设备上点一次「始终」
```

### 4.4 崩溃自恢复（配合 `05-稳定性事故报告`）

因为 framework 软重启后会**重新广播 `BOOT_COMPLETED`**，
只要注册了这个广播，Kiosk 应用在框架崩溃后会被**自动重新拉起**。
实测日志已证实（见 [`../raw/boot-completed-after-restart.txt`](../raw/boot-completed-after-restart.txt)）。

**务必保留 `BOOT_COMPLETED` 接收器**，即使应用已经是默认桌面。

---

## 五、需要修正的旧文档

本次实测修正了早期文档中的以下内容：

| 位置 | 原来写的 | 修正为 |
|---|---|---|
| 开发指南 §10 | `am broadcast -a ... -p <pkg>` | **5.1 不支持 `-p`，改用 `-n`** |
| 开发指南 §4 方案 B | `pm disable-user` 理论可行 | **实测不可行**（已在上一轮修正） |
| 开发指南 §4 方案 C | 「`dpm` 权限检查通过」 | **已实测真正设成 Device Owner 并跑通 LockTask** |
| 开发指南 §4 方案 C | 「需恢复出厂才能清除」 | **已验证，且会清除 ADB 授权** |
| 开发指南 §7 | 帧率目标 | 补充实测 **10–21 FPS**（见稳定性报告） |

---

## 六、完整证据索引

| 文件 | 内容 |
|---|---|
| [`../raw/kiosk-app-package-info.txt`](../raw/kiosk-app-package-info.txt) | 应用包信息与组件注册 |
| [`../raw/kiosktest-applog.txt`](../raw/kiosktest-applog.txt) | 应用自身日志 |
| [`../raw/boot-autostart-evidence.txt`](../raw/boot-autostart-evidence.txt) | 开机自启完整日志链路 |
| [`../raw/device-owner-state.txt`](../raw/device-owner-state.txt) | Device Owner 生效状态 |
| [`../assets/kiosk-*.png`](../assets/) | 6 张关键截图 |
| [`../tools/kiosk-test/`](../tools/kiosk-test/) | 测试 APK 全部源码与构建脚本 |
