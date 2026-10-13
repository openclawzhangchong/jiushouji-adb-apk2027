# Kiosk 测试 APK · 源码与使用说明

> 一个**最小化的 Kiosk 能力验证应用**，用来在真机上逐项验证：
> 安装 / 桌面接管 / 开机自启 / Device Owner / LockTask 锁定 / 卸载保护 / 恢复出厂。
>
> 验证结果见 [`../../docs/06-Kiosk可行性实测.md`](../../docs/06-Kiosk可行性实测.md)（**8 项全部通过**）。

---

## ⚠️ 先读这一段：风险提示

| 风险 | 说明 |
|---|---|
| **`dpm set-device-owner` 不可逆** | 一旦设置，**只能恢复出厂设置才能清除**。`pm uninstall` 会被拒绝 |
| **`WIPE DATA` 按钮会擦除设备** | 点击后立即恢复出厂，**所有数据丢失**，且会清除 ADB 授权密钥 |
| **恢复出厂后需重新授权 ADB** | 设备重启后 `adb devices` 会变回 `unauthorized`，需要在设备屏幕上点一次「允许」 |
| **建议只在测试机上使用** | 不要在有数据的设备上设置 Device Owner |

**推荐做法**：拿一台**全新/可清空的测试机**跑这个 APK。测完用 `WIPE DATA` 按钮自毁，
设备会自动恢复到初始状态（实测恢复后包总数与初始完全一致）。

---

## 一、这个 APK 验证了什么

| # | 验证项 | 对应实现 | 结果 |
|---|---|---|---|
| 1 | 能否安装 | 标准 APK | ✅ |
| 2 | 装完处于 `stopped` 状态 | — | ✅ 实测证实 |
| 3 | 启动一次后清除 `stopped` | `MainActivity` | ✅ |
| 4 | 声明 HOME 接管桌面 | manifest 的 `CATEGORY_HOME` | ✅ 弹出选择器 |
| 5 | 真实重启后自动启动 | `BootReceiver` | ✅ 36 秒后自动前台 |
| 6 | `dpm set-device-owner` | `AdminReceiver` | ✅ |
| 7 | LockTask 锁定 | `startLockTask()` | ✅ 按 Home/Back 退不出去 |
| 8 | 卸载保护 + 清除路径 | `wipeData()` | ✅ |

---

## 二、文件结构

```
kiosk-test/
├── AndroidManifest.xml                    三件套声明（HOME / BOOT_COMPLETED / DeviceAdmin）
├── src/com/jiushouji/kiosktest/
│   ├── MainActivity.java                  主界面 + 4 个测试按钮
│   ├── BootReceiver.java                  开机自启接收器
│   └── AdminReceiver.java                 设备管理员（Device Owner 前提）
├── res/xml/device_admin.xml               管理员策略声明
├── build.sh                               ★ 一键构建脚本（无需 Android Studio）
├── kiosk-test-signed.apk                  ★ 已签名可安装 APK
└── README.md                              本文件
```

---

## 三、安装与使用

### 3.1 直接装现成的 APK

```bash
adb install -r kiosk-test-signed.apk
```

> 签名密钥口令是 `android`，仅为测试用，**不要用于正式产品**。

### 3.2 首次启动（必须）

```bash
adb shell am start -n com.jiushouji.kiosktest/.MainActivity
```

> ⚠️ **这一步不能省**。应用装完后处于 `stopped` 状态，**收不到开机广播**。
> 启动一次后才会变成 `stopped=false`。

### 3.3 界面上的 4 个按钮

| 按钮 | 作用 | 前置条件 |
|---|---|---|
| **1. SET LOCKTASK PACKAGES** | 调 `dpm.setLockTaskPackages(admin, {本包})`，把本包加入锁定白名单 | 必须是 Device Owner |
| **2. START LOCK TASK** | 调 `startLockTask()` 进入锁定模式 | 需先点按钮 1 |
| **3. STOP LOCK TASK** | 调 `stopLockTask()` 退出锁定模式 | 处于锁定状态时 |
| **9. WIPE DATA** | 调 `dpm.wipeData(0)` **恢复出厂设置** | 必须是 Device Owner |

界面上会实时显示两个状态：

- `Device Owner: YES/NO`
- `LockTask 可用: YES/NO`

### 3.4 完整验证流程

```bash
PKG=com.jiushouji.kiosktest

# ① 安装
adb install -r kiosk-test-signed.apk

# ② 首次启动（清除 stopped 状态）
adb shell am start -n $PKG/.MainActivity

# ③ 验证开机自启：重启设备
adb reboot
#    等约 40 秒，界面应自动出现（无需任何操作）

# ④ 设为 Device Owner（设备必须无已登录账号）
adb shell dpm set-device-owner $PKG/.AdminReceiver
#    → Success: Device owner set to package com.jiushouji.kiosktest

# ⑤ 重启应用，确认 UI 显示 Device Owner: YES
adb shell am force-stop $PKG
adb shell am start -n $PKG/.MainActivity

# ⑥ 点「1. SET LOCKTASK PACKAGES」，再重启应用，确认 LockTask 可用: YES
# ⑦ 点「2. START LOCK TASK」，然后按设备上的 Home / Back 键 —— 应无法退出

# ⑧ 恢复：点「3. STOP LOCK TASK」解锁，再点「9. WIPE DATA」恢复出厂
```

**手动点按钮的方式**（如果不想手动）：

```bash
# 按钮坐标（720x1280 屏幕，可用 uiautomator dump 复核）
adb shell "input tap 360 631"   # 1. SET LOCKTASK PACKAGES
adb shell "input tap 360 715"   # 2. START LOCK TASK
adb shell "input tap 360 799"   # 3. STOP LOCK TASK
adb shell "input tap 360 883"   # 9. WIPE DATA

# 复核按钮位置（推荐，不同分辨率会变）
adb shell "uiautomator dump /sdcard/ui.xml"
adb shell "cat /sdcard/ui.xml" | grep -o 'text="[^"]*"[^/]*bounds="[^"]*"'
```

---

## 四、自己重新构建

### 4.1 依赖

构建**不需要 Android Studio / Gradle**，只需要 3 个东西（详见
[`../../skills/android-apk-build-no-studio/SKILL.md`](../../skills/android-apk-build-no-studio/SKILL.md)）：

| 依赖 | 下载地址 | 放置位置 |
|---|---|---|
| OpenJDK 17 | `https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip` | `D:\android-build\jdk17\` |
| build-tools r34 | `https://dl.google.com/android/repository/build-tools_r34-windows.zip` | `D:\android-build\android-14\` |
| android.jar | `https://repo1.maven.org/maven2/com/google/android/android/4.1.1.4/android-4.1.1.4.jar` | `D:\android-build\` |

### 4.2 构建

```bash
bash build.sh
# 产物: kiosk-test-signed.apk
```

构建链：

```
aapt2 compile → aapt2 link → javac --release 8 → d8 → jar uf → zipalign → apksigner
```

### 4.3 改包名 / 改应用名

1. 改 `AndroidManifest.xml` 里的 `package="..."` 和 `android:label`
2. 把 `src/com/jiushouji/kiosktest/` 目录按新包名重命名
3. 改三个 `.java` 文件顶部的 `package` 声明
4. 重新 `bash build.sh`

---

## 五、实现要点（给改代码的人）

### 5.1 API 21 专有方法全部用反射

编译用的 `android.jar` 是 **API 16**，没有 `startLockTask()` / `setLockTaskPackages()` /
`wipeData()` / `isDeviceOwnerApp()`。所以全部走反射：

```java
private void startLock() {
    try {
        Method m = Activity.class.getMethod("startLockTask");   // API 21
        m.invoke(this);
    } catch (Throwable e) {
        Log.e(TAG, "startLockTask 失败: " + e);
    }
}
```

这样编译能过，运行在 Android 5.1 上正常。

### 5.2 三件套声明（缺一不可）

```xml
<!-- ① 主界面：LAUNCHER + HOME -->
<intent-filter>
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.LAUNCHER" />
    <category android:name="android.intent.category.HOME" />
    <category android:name="android.intent.category.DEFAULT" />
</intent-filter>

<!-- ② 开机自启 + 崩溃自恢复 -->
<receiver android:name=".BootReceiver">
    <intent-filter android:priority="1000">
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.QUICKBOOT_POWERON" />
    </intent-filter>
</receiver>

<!-- ③ 设备管理员（Device Owner 前提）-->
<receiver android:name=".AdminReceiver"
    android:permission="android.permission.BIND_DEVICE_ADMIN">
    <meta-data android:name="android.app.device_admin"
               android:resource="@xml/device_admin" />
    <intent-filter>
        <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
    </intent-filter>
</receiver>
```

### 5.3 屏幕常亮 + 解除锁屏

```java
getWindow().addFlags(
        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
      | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
      | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
      | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
```

> 实测 `settings put secure lockscreen.disabled 1` **在 Android 5.1 上无效**，
> `setKeyguardDisabled()` 是 API 23+。**只能用上面这组 flag。**

---

## 六、注意事项

- **序列号是占位值**：设备 `ro.serialno = 0123456789ABCDEF`，多台设备会重复，别当唯一标识
- **只有 `armeabi-v7a`**：这个 APK 是纯 Java，没有 ABI 限制；但如果你加带 `.so` 的库，
  必须提供 `armeabi-v7a`
- **`am broadcast` 不支持 `-p`**：Android 5.1 上要用 `-n` 指定组件
  ```bash
  adb shell am broadcast -a android.intent.action.BOOT_COMPLETED \
      -n com.jiushouji.kiosktest/.BootReceiver
  ```
- **测试完记得恢复**：用 `WIPE DATA` 按钮，否则设备会一直带着 Device Owner
