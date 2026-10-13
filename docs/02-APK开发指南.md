# APK 开发指南 · 目标设备 `XXB` / `b906`

> 面向程序员。**先读第 0 节，那是能直接决定项目成败的硬约束。**
> 所有数据来自真机 `adb` 实测（原始数据见 `raw/` 目录），推断内容均已标注。

---

## 0. TL;DR —— 十一条硬约束

如果只记十条，记这些：

| # | 约束 | 后果 |
|---|---|---|
| 1 | **`minSdkVersion` 必须 ≤ 21** | 大于 21 装不上 |
| 2 | **只有 `armeabi-v7a`**，无 arm64 / 无 x86 | 带 native 库的三方 SDK 必须提供 armeabi-v7a，否则 `UnsatisfiedLinkError` |
| 3 | **没有任何 Google 服务** | 禁用 Firebase / FCM / Play Services / Google Maps / SafetyNet / Play 内更新 |
| 4 | **WebView 版本 37（2014 年 8 月）** | H5 必须写 ES5，禁用 ES6+、`fetch`、CSS Grid、CSS 变量 |
| 5 | **CPU 空闲 1 核 @598 MHz，有负载时 4 核全开 @1.3 GHz** | 冷启动阶段只有 1 核，别把重活放在启动路径上 |
| 6 | **RAM 1.93 GB** | 内存预算保守，大图要压缩 |
| 7 | **屏幕 720×1280 @ 280dpi = 411×731 dp** | 按 411dp 宽设计 |
| 8 | **`targetSdkVersion` 建议锁 21~22** | 规避后续 API 的行为变更（权限、后台限制等） |
| 9 | **应用装完后处于 `stopped` 状态，收不到开机广播** | 安装后**必须手动启动一次**，否则开机自启永远不生效 |
| 10 | **无 root、SELinux permissive、无中文输入法** | 不能改系统分区；中文输入需自带方案 |
| 11 | 系统是 `test-keys` 签名、`user` 版 | 应用用普通 debug/release 签名即可安装 |

---

## 1. 目标设备规格

### 1.1 系统

| 项目 | 值 |
|---|---|
| Android 版本 | **5.1 Lollipop** |
| API Level | **21** |
| 运行时 | ART（`persist.sys.dalvik.vm.lib.2 = libart.so`） |
| 版本类型 | `user`（非 userdebug/eng） |
| 系统签名 | `test-keys` |
| Root | ❌ 无（`ro.secure = 1`，`ro.debuggable = 0`，无 `su`） |
| SELinux | `permissive`（宽容模式） |
| 语言 / 时区 | `zh-CN` / `Asia/Shanghai` |
| 输入法 | **仅 AOSP 英文键盘，无中文输入法** |
| 桌面 | `com.android.launcher3`（原生） |
| Google 服务 | ❌ 完全没有 |
| WebView | **37**（`versionName=37`） |

### 1.2 硬件

| 项目 | 值 |
|---|---|
| SoC | MediaTek **MT6582**（ARMv7，4×Cortex-A7，最高 1.3 GHz） |
| CPU 在线核心 | ✅ **hotplug 工作正常**：空闲 1 核，**有负载时 4 核全开**（已实测复现） |
| 调频 | governor = `hotplug`；档位 `1300 / 1196 / 1040 / 747.5 / 598 MHz`；空闲 598 MHz，负载升到 1300 MHz |
| 热插拔配置 | `is_cpu_hotplug_disable=0`、`cpu_num_limit=4`、`cpu_up_threshold=90`、`cpu_rush_boost_num=4`、`cpu_input_boost_num=2` |
| ABI | `armeabi-v7a`, `armeabi`（**`abilist64` 为空**） |
| RAM | **2027880 kB ≈ 1.93 GB**（空闲约 1.5 GB） |
| 存储 | eMMC 16 GB；`/data` 13.2 G（剩余 13.1 G） |
| 系统分区 | `/system` 871.8 MB（剩余 408 MB）；`/cache` 120 MB |
| 屏幕 | **720 × 1280**，density **280**（→ 411.4 × 731.4 dp） |
| 屏幕朝向 | `ro.sf.hwrotation = 180`（面板在机壳内倒装，系统已软件校正，**应用无需处理**） |
| GPU | OpenGL ES 2.0（`reqGlEsVersion=0x20000`） |
| 电池 | Li-ion，100%，25 ℃ |
| 屏幕超时 | 30000 ms（30 秒，做常亮要自己改） |
| 设备类别 | `ro.build.characteristics = tablet`（但 smallestWidth 411dp < 600dp，**实际按手机尺寸类处理**） |

### 1.3 设备身份

| 项目 | 值 |
|---|---|
| 品牌 / 制造商 | `alps`（联发科 MTK 公版方案标志） |
| 型号 / 代号 | `XXB` / `b906`（`full_b906`） |
| 固件版本 | `XM01X8_V1.0.0_230811` |
| 编译时间 | 2023-08-11 11:52:43 CST |
| Build Fingerprint | `alps/full_b906/b906:5.1/LRX21M/1691725749:user/test-keys` |
| **ODM 厂商** | **`KEYTAK`**（`ro.mediatek.project.path = device/keytak/b906`） |
| 平台线索 | 基带串 `LOKYEE82_WET_KK_GPRS_HW` → 推断为 **`joya82` 平台**（详见 `01-设备评估报告.md`） |
| 序列号 | `0123456789ABCDEF` ⚠️ MTK 默认占位值，**不唯一，禁止用作设备标识** |

> ⚠️ **重要**：`Settings.Secure.ANDROID_ID` 在这类设备上也可能重复（同批产线烧录）。
> 需要唯一标识时，建议首启生成 UUID 存本地，或结合 MAC + 序列号做哈希。

---

## 2. 编译配置

### 2.1 推荐 `build.gradle`

```gradle
android {
    compileSdk 34                    // 编译用新版，随意

    defaultConfig {
        applicationId "com.example.jiushouji"
        minSdk 21                    // ★ 必须 ≤ 21
        targetSdk 22                 // ★ 锁 21~22，规避后续 API 行为变更
        versionCode 1
        versionName "1.0"

        // ★ 如果项目含 native 代码，只出 armeabi-v7a
        ndk { abiFilters 'armeabi-v7a' }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }

    buildTypes {
        release {
            minifyEnabled true
            // 无 Play Store，不需要上架签名，debug 签名即可安装
        }
    }
}
```

### 2.2 依赖选择原则

- ✅ **纯 Java/Kotlin 的库**（AndroidX 大部分、OkHttp、Gson、Room）→ 可用
- ⚠️ **含 native `.so` 的库** → **必须确认提供 `armeabi-v7a`**。
  近年很多 SDK 已只出 `arm64-v8a`，装上去会直接 `UnsatisfiedLinkError` 崩溃。
  接入前先解压 APK 看 `lib/` 目录有没有 `armeabi-v7a`。
- ❌ **任何 `com.google.android.gms:*` / `com.google.firebase:*`** → 设备上根本没有 GMS，必崩
- ❌ 要求 API 23+ 的库 → 直接排除

> 遇到 AndroidX 的 minSdk 冲突（报 `uses-sdk:minSdkVersion 21 cannot be smaller than version N`），
> 就把该库**降级到最后一个支持 minSdk 21 的版本**。别升 minSdk。

### 2.3 Kotlin

Kotlin 可用。但注意标准库会增大方法数——虽然 ART 原生支持 multidex，APK 体积在
13 GB 空间上不是问题，但**冷启动解析时间**会变长。1 核机器上建议开 R8 混淆压缩。

---

## 3. 外设与传感器可用性

### 3.1 ✅ 可用

| 能力 | Feature / 证据 |
|---|---|
| 触摸屏（**≥5 点多点触控**） | `touchscreen.multitouch.jazzhand` |
| WiFi + WiFi Direct | `wifi`, `wifi.direct` |
| 蓝牙 + BLE | `bluetooth`, `bluetooth_le` |
| GPS + 网络定位 | `location.gps`, `location.network` |
| 麦克风 / 扬声器 | `microphone`, `audio.output` |
| 摄像头 | `camera`, `camera.any`（详见下方陷阱） |
| USB Host（OTG） | `usb.host` |
| USB Accessory | `usb.accessory` |
| 加速度计 / 距离 / 光线 | 实测 3 个硬件传感器 |
| 设备管理（Device Admin） | `software.device_admin` |
| 可作为桌面（Home） | `software.home_screen` |
| 打印 / 语音识别 / SIP | `software.print`, `software.voice_recognizers`, `software.sip` |

### 3.2 ⚠️ 三个「声明了但实际没有」的陷阱

这三个是**真机上实测出来的矛盾**，不处理会踩坑：

#### 陷阱 1：声明了指南针，但没有磁力计

```
pm list features  →  android.hardware.sensor.compass   ✅ 声明存在
dumpsys sensorservice  →  只有 3 个传感器：
    ACCELEROMETER / PROXIMITY / LIGHT                   ❌ 没有磁力计
```

**后果**：`PackageManager.hasSystemFeature(FEATURE_SENSOR_COMPASS)` 返回 `true`，
但 `SensorManager.getDefaultSensor(TYPE_MAGNETIC_FIELD)` 返回 **`null`**。

**对策**：**永远不要用 `hasSystemFeature` 判断传感器**，一律用 `getDefaultSensor()` 判空：

```java
Sensor mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
if (mag == null) {
    // 必须走降级逻辑
}
```

#### 陷阱 2：声明了前置摄像头，但只有 1 个摄像头

```
pm list features  →  camera.front / camera.autofocus / camera.flash   ✅ 都声明了
dumpsys media.camera  →  Number of camera devices: 1                  ❌ 只有 1 个
```

**对策**：不要按 `FEATURE_CAMERA_FRONT` 判断前后摄，
用 `Camera.getNumberOfCameras()` + `CameraInfo.CAMERA_FACING` 实际遍历。只按单摄设计。

#### 陷阱 3：没有陀螺仪

未声明 `sensor.gyroscope`，也没有对应硬件。
**对策**：不要依赖 `TYPE_GYROSCOPE`；`TYPE_ROTATION_VECTOR` 也不可靠。
屏幕方向只能靠加速度计，或干脆**锁死竖屏**（推荐，见 3.4）。

### 3.3 实测传感器读数（坐标系正常）

```
ACCELEROMETER  last = < 0.1, -0.2, 10.0 >   # 平放时 z≈+10（重力），符合标准坐标系
```

**结论**：加速度计坐标系正常，**没有因 `hwrotation=180` 出现轴向翻转**。
（这点特意验证过，因为 MTK 倒装屏有时会翻。）

### 3.4 屏幕方向建议

**强烈建议在 `AndroidManifest.xml` 里锁死竖屏**：

```xml
<activity
    android:name=".MainActivity"
    android:screenOrientation="portrait"
    android:configChanges="orientation|screenSize|keyboardHidden">
```

理由：
- 面板是倒装的（`hwrotation=180`），自动旋转在个别场景下可能出怪问题
- 没有陀螺仪，自动旋转依赖加速度计，体验差且耗电
- 加 `configChanges` 避免旋转时 Activity 重建，在 1 核机器上重建一次要好几秒

---

## 4. 开机自启动方案 ★

这是本项目的核心需求。Android 5.1 上**有 4 条路**，我按可靠性排序。

### ⚠️ 先说最重要的坑

> **Android 3.1 起，应用安装后若从未被启动过，会处于 `stopped` 状态。
> 处于 `stopped` 状态的应用收不到任何广播，包括 `BOOT_COMPLETED`。**

也就是说：**只写 `BOOT_COMPLETED` 广播是不够的**。
APK 装完之后，**必须手动启动一次**，应用才会退出 `stopped` 状态。

**安装脚本必须包含一次启动**（见第 6 节）。

---

### 方案 A：`BOOT_COMPLETED` 广播（标准做法）

**`AndroidManifest.xml`**：

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<receiver
    android:name=".BootReceiver"
    android:enabled="true"
    android:exported="true">
    <intent-filter android:priority="1000">
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <!-- MTK 快启动/快速开机场景，加上更保险 -->
        <action android:name="android.intent.action.QUICKBOOT_POWERON" />
        <action android:name="com.htc.intent.action.QUICKBOOT_POWERON" />
    </intent-filter>
</receiver>
```

**`BootReceiver.java`**：

```java
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            Intent i = new Intent(ctx, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        }
    }
}
```

**注意**：Android 5.1 上从广播里 `startActivity` **不需要**额外权限（`SYSTEM_ALERT_WINDOW`
的限制是后来才有的），所以这里可以直接拉起界面。

**✅ 优点**：标准、通用、不改变系统桌面。
**❌ 缺点**：依赖 `stopped` 状态被清除；被用户强停后失效。

---

### 方案 B：把自己做成桌面（**最可靠，推荐用于本机**）

让 App 声明为 Home，开机直接进你的界面，**完全不依赖广播**。

**`AndroidManifest.xml`**：

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:launchMode="singleTask"
    android:screenOrientation="portrait"
    android:configChanges="orientation|screenSize|keyboardHidden"
    android:stateNotNeeded="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.HOME" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

安装后按 Home 键会弹出「选择桌面」；用下面的方式固定下来：

**设置默认桌面的可用手段（本机实测）**：

| 手段 | 本机实测结论 |
|---|---|
| `cmd package set-home-activity` | ❌ **不可用** —— 本机 Android 5.1 **没有 `cmd` 命令**（6.0 才引入） |
| `pm set-home-activity` | ❌ **不存在** —— `pm` 子命令列表里没有这一项 |
| `pm disable-user --user 0 com.android.launcher3` | ❌ **实测不可行** —— `SecurityException: Permission Denial: attempt to change component state from pid=…, uid=2000`。shell 用户**没有** `CHANGE_COMPONENT_ENABLED_STATE` 权限（详见下方） |
| `am start -a android.intent.action.MAIN -c android.intent.category.HOME` | ⚠️ **会弹出桌面选择器，必须人工点一次「始终」**。当前系统只有 Launcher3 一个 HOME，所以现在调用它只是回桌面；等你的 APK 装上、变成第 2 个 HOME 后才会弹选择器 |
| 方案 C 的 `dpm set-device-owner` | ✅✅ **已完整实测跑通** —— 真的设成了 Device Owner，LockTask 生效（按 Home/Back 退不出去）。详见 [`06-Kiosk可行性实测.md`](06-Kiosk可行性实测.md) |

#### ⚠️ 关于 `pm disable-user` 的实测细节（重要）

用真实存在的包试探，**两次都失败**：

```bash
$ adb shell pm disable-user --user 0 com.android.galaxy4      # 无害的动态壁纸包
Error: java.lang.SecurityException: Permission Denial:
       attempt to change component state from pid=2814, uid=2000, package uid=10027

$ adb shell pm disable-user --user 0 com.android.launcher3    # 目标包
Error: java.lang.SecurityException: Permission Denial:
       attempt to change component state from pid=2912, uid=2000, package uid=10005
```

报错是**权限检查先于包特定逻辑**触发的，所以结论对**任何包**都成立。

**根因**：shell 包（uid 2000）被授予的 66 条权限里**没有** `CHANGE_COMPONENT_ENABLED_STATE`。
（完整清单见 [`../raw/shell-permissions.txt`](../raw/shell-permissions.txt)）

**但 shell 有这些有用的权限**：

| 权限 | 用途 |
|---|---|
| ✅ `MANAGE_DEVICE_ADMINS` | → `dpm set-device-owner` 可用 |
| ✅ `SET_PREFERRED_APPLICATIONS` | 有设置默认应用的权限，**但 5.1 上没有命令行接口能调用它** |
| ✅ `WRITE_SECURE_SETTINGS` | → `settings put secure …` 可用 |
| ✅ `INSTALL_PACKAGES` / `DELETE_PACKAGES` | 装/卸载 |
| ✅ `INJECT_EVENTS` | 模拟输入 |
| ✅ `FORCE_STOP_PACKAGES` | 强停应用 |
| ✅ `DUMP` | `dumpsys` |
| ❌ `CHANGE_COMPONENT_ENABLED_STATE` | **没有** → 禁用组件不可行 |
| ❌ `REBOOT` | 没有（但 `adb reboot` 走 adb 协议，不受影响） |

> 💡 **产线批量部署的关键结论**：因为无法禁用原桌面，装完 APK 后系统里会有**两个 HOME**，
> 第一次按 Home 会弹选择器，**必须人工点一次「始终」**。
> 想做到**全自动、零人工**，只能走**方案 C（Device Owner）**。

**✅ 优点**：不依赖广播，开机必进自己界面。
**⚠️ 注意**：`stateNotNeeded="true"` 让系统在崩溃重启时能重新拉起；
但**一旦设为默认桌面，按 Home 键就回不到原桌面了**——这是预期行为。

---

### 方案 C：Device Owner + Kiosk 锁定（最强，可防退出）

**本机实测支持**：

```
$ adb shell dpm
usage: dpm set-device-owner <COMPONENT>

$ adb shell dumpsys device_policy
Current Device Policy Manager state:
  Enabled Device Admins (User 0):        ← 空，说明当前没有 Device Owner
```

**关键前提已满足**：设备上**没有任何已登录账号**，所以可以设为 Device Owner。

**步骤**：

1. 先写一个 `DeviceAdminReceiver` 并在 manifest 里声明
   （`android.app.action.DEVICE_ADMIN_ENABLED` + `res/xml/device_admin.xml`）
2. 安装后执行：

```bash
adb shell dpm set-device-owner com.example.jiushouji/.MyDeviceAdminReceiver
```

3. 在代码里开启 LockTask（锁定任务模式）：

```java
DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
ComponentName admin = new ComponentName(this, MyDeviceAdminReceiver.class);

// 允许自己进入锁定任务
dpm.setLockTaskPackages(admin, new String[]{ getPackageName() });

// 在 Activity 里启动锁定
startLockTask();
```

**✅ 优点**：用户**无法退出**（Home/Back/Recent 全被接管），真正的 kiosk。
**⚠️ 注意（均已实测验证）**：
- 一旦设为 Device Owner，**必须恢复出厂设置才能清除**（`dpm remove-active-admin` 对 Device Owner 无效）。
  实测 `pm uninstall` 会被拒绝：`Failure [DELETE_FAILED_DEVICE_POLICY_MANAGER]`
- **恢复出厂设置会清除 ADB 授权密钥** → 设备重启后 `adb devices` 会变回 `unauthorized`，
  需要在设备屏幕上重新点一次「允许」。批量部署时要把这一步算进去
- `dpm set-device-owner` 要求设备上**没有已登录账号**——现在满足，但**以后登录了账号就设不了了**，建议先做这一步
- ✅ 实测锁定效果：LockTask 生效后**按 Home / Back 键都无法退出应用**，
  系统提示「屏幕处于固定状态。您所属的单位不允许取消固定。」

> 完整实测过程与证据见 [`06-Kiosk可行性实测.md`](06-Kiosk可行性实测.md)。

---

### 方案 D：无 root 做不了的（明确排除，别浪费时间）

| 方式 | 为什么不行 |
|---|---|
| 把 APK 内置到 `/system/app` | 需要 root + 重挂 `/system` 为可写 |
| `init.d` 开机脚本 | 需要 root，且内核需支持 |
| 改 `build.prop` 加 `persist.sys.*` | 需要 root |
| 用 `com.fw.upgrade` 走 OTA 推 | 该通道是 ODM 私有的，无法复用 |

---

### ✅ 推荐组合（本机最优解）

```
应用侧：声明 HOME + LAUNCHER + BOOT_COMPLETED 三件套
        + 锁竖屏 + singleTask

部署侧（安装脚本）：
    1. adb install -r app.apk
    2. adb shell am start -n <包名>/.MainActivity     ← ★ 清除 stopped 状态，必须做
    3. adb shell dpm set-device-owner <包名>/.MyDeviceAdminReceiver
                                                     ← ★ 实测权限可用；设完可免人工固定桌面
    4. 应用内调 addPersistentPreferredActivity() 把桌面钉死
       （若跳过第 3 步，则退回人工：am start -c HOME 让用户点一次「始终」）
```

这套组合下：开机 → 系统启动 → 你的 App 作为 Home 被拉起 → 界面直接出现。
即使 `BOOT_COMPLETED` 因为 `stopped` 状态没生效，方案 B 也能兜住。

> ⚠️ **批量部署注意**：因为 `pm disable-user` 不可用，**没法自动干掉原桌面**。
> 不做 Device Owner 的话，每台机器都要人工点一次「始终」。
> 要零人工，**必须走 Device Owner**——而且要在设备**还没登录任何账号之前**做。

---

## 5. 常亮 / 保活 / 抗崩溃

### 5.1 屏幕常亮

设备当前 `screen_off_timeout = 30000`（30 秒）。

```java
// 方式一：只在本 Activity 有效（推荐，干净）
getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

// 方式二：全局改系统设置（需要 WRITE_SETTINGS 权限，API 21 上可直接申请）
Settings.System.putInt(getContentResolver(),
        Settings.System.SCREEN_OFF_TIMEOUT, Integer.MAX_VALUE);
```

### 5.2 保活

Android 5.1 的后台限制很宽松（Doze 是 6.0 引入的），所以保活相对容易：

```xml
<service android:name=".KeepAliveService" android:exported="false" />
```

```java
@Override
public int onStartCommand(Intent intent, int flags, int startId) {
    return START_STICKY;   // 被杀后自动重启
}
```

配合 `AlarmManager.setRepeating()` 定期自检重启，在 5.1 上很有效。

### 5.3 崩溃自恢复

声明为 Home 时加上 `android:stateNotNeeded="true"`，
系统在 Activity 崩溃后会尝试重新创建它。

---

## 6. 安装与部署

### 6.1 单条命令

```bash
adb install -r app-release.apk
```

> 无 Play Store，只能用 adb 或文件管理器侧载。debug 签名即可，不需要上架签名。

### 6.2 一键部署脚本（Windows `.bat`）

```bat
@echo off
set ADB=adb
set PKG=com.example.jiushouji
set APK=app-release.apk

echo [1/5] 检查设备...
%ADB% wait-for-device
%ADB% devices

echo [2/5] 安装...
%ADB% install -r %APK%
if errorlevel 1 goto :fail

echo [3/5] 首次启动（清除 stopped 状态，这一步不能省）...
%ADB% shell am start -n %PKG%/.MainActivity

echo [4/5] 设置默认桌面（手机上点"始终"）...
%ADB% shell am start -a android.intent.action.MAIN -c android.intent.category.HOME

echo [5/5] 可选：设为 Device Owner
rem %ADB% shell dpm set-device-owner %PKG%/.MyDeviceAdminReceiver

echo 完成。
goto :eof

:fail
echo 安装失败，检查 APK 的 minSdk 是否为 21、是否含 armeabi-v7a。
```

### 6.3 卸载 / 重置

```bash
adb uninstall com.example.jiushouji
# 若已设为 Device Owner，必须先恢复出厂设置：
#   设置 → 备份和重置 → 恢复出厂设置
```

---

## 7. 性能预算

### 7.1 实测：CPU 热插拔行为（已复现两次）

| 状态 | `online` | 当前频率 |
|---|---|---|
| 空闲 | `0`（**1 核**） | 598 MHz |
| 施加 8 个忙循环 4 秒后 | `0-3`（**4 核全开**） | 1300 MHz |
| 施加负载 10 秒后 | `0-3`（4 核） | 1300 MHz |
| 释放负载 4 秒后 | `0-2`（**正在逐步关核**） | 598 MHz |

**结论：hotplug 完全正常，不是故障。**
`is_cpu_hotplug_disable = 0`、`cpu_num_limit = 4`。
空闲关到 1 核是 MTK hotplug governor 的标准省电行为。

**对开发的真实影响**：

- ✅ **重负载下 4 核可用**，不必按单核设计
- ⚠️ **但冷启动阶段往往只有 1 核**（应用刚起来时还没达到 `cpu_up_threshold = 90`）
  → **别把重活放在启动路径上**，先出界面再异步加载
- ⚠️ **多核唤醒有延迟**：`cpu_up_avg_times = 10`、`sampling_rate = 30000`（30 ms 采样）
  → 突发计算会先在 1 核上跑一小段，达到 `cpu_rush_threshold = 98` 才冲到 4 核
- ℹ️ 触摸输入会触发 `cpu_input_boost_num = 2`（临时升到 2 核）
- ✅ **常规界面的实际帧率可达 43–56 FPS**（launcher 连续滑动实测，单帧最小 15 ms），
  偶发 82–313 ms 卡顿帧 —— **不必为了让"跑得动"而把界面做得很简陋**

### 7.2 参考基线

| 指标 | 建议目标 |
|---|---|
| 冷启动 | < 3 秒（**启动路径按单核预算**） |
| 界面帧率 | **43–56 FPS 可达**（launcher 实测），偶发 82–313 ms 卡顿帧 |
| 单次网络请求超时 | ≥ 15 秒 |
| 图片解码 | 单张 ≤ 1080p，且必须采样压缩 |

### 7.3 优化建议

- **启动路径（`Application.onCreate` / 首屏）按单核设计**，重活丢到后台线程延后做
- **主线程绝对不要做 IO / 网络 / 大量计算**
- **图片**：用 `BitmapFactory.Options.inSampleSize` 压缩；避免加载超过屏幕分辨率的图
- **列表**：用 `RecyclerView` 但别做复杂 item 动画；列表项数量大时考虑分页
- **避免**：`ObjectAnimator` 复杂动画、`SurfaceView` 高帧率绘制、大量 `findViewById`
- **R8/ProGuard 必须开**，减少方法数能显著改善冷启动
- **多线程可以用**（负载下 4 核会全开），但**并行度别设太高**——唤醒核心本身有开销

### 7.4 内存

- 总 RAM 1.93 GB，但系统本身占一部分，应用实际可用约 1 GB
- 避免 `android:largeHeap="true"`（在这台机器上没多少余量可借）
- 大图、视频帧要主动 `recycle()`

---

## 8. 网络与安全

### 8.1 网络

- WiFi 正常（`wlan0`），已能联网
- 蜂窝网络**只有 GPRS**，没有 3G/4G，**不要指望蜂窝数据**

### 8.2 TLS / HTTPS

- Android 5.1 支持 **TLS 1.2**（需要在代码里显式启用，别依赖默认协商）
- **不支持 TLS 1.3**（需要 Android 10+）
- 服务端如果只开 TLS 1.3，这台设备连不上

```java
// 显式启用 TLS 1.2
SSLContext ctx = SSLContext.getInstance("TLSv1.2");
ctx.init(null, null, null);
```

### 8.3 调试便利：HTTPS 抓包很容易

Android 5.1（targetSdk < 24）**默认信任用户安装的 CA 证书**，
所以直接往设备里装 Charles / Fiddler 的根证书就能抓 HTTPS，
不需要像 Android 7+ 那样改 `network_security_config`。这对调试很友好。

### 8.4 安全提醒

- Android 5.1 已无安全更新，**不要在这台设备上处理敏感账号 / 支付**
- 应用如果要存敏感数据，自己加密（`EncryptedSharedPreferences` 用不了，
  用 `javax.crypto` + `KeyStore` 手写）
- 设备 SELinux 是 permissive，别误以为很安全——只是便于调试

---

## 9. 程序员自检清单

交付前逐条打勾：

**编译配置**
- [ ] `minSdkVersion == 21`（或更低）
- [ ] `targetSdkVersion` 在 21~22
- [ ] 若含 native 代码：`abiFilters 'armeabi-v7a'`
- [ ] 解压 APK 检查 `lib/` 下**没有** `arm64-v8a` 或 `x86` 目录
- [ ] 依赖里**没有** `com.google.android.gms` / `firebase`

**功能适配**
- [ ] 所有 `getDefaultSensor()` 结果都判了 `null`
- [ ] 摄像头按 `Camera.getNumberOfCameras()` 遍历，没假设有前置
- [ ] 锁竖屏（`screenOrientation="portrait"`）
- [ ] 界面按 411 × 731 dp 验证过不溢出
- [ ] 若用 WebView：H5 是 ES5 语法，无 `fetch` / CSS Grid / CSS 变量
- [ ] 中文输入不依赖系统输入法（自己画键盘或允许英文）

**开机自启**
- [ ] 声明了 `RECEIVE_BOOT_COMPLETED`
- [ ] `BootReceiver` 注册了 `BOOT_COMPLETED` + `QUICKBOOT_POWERON`
- [ ] 声明了 `HOME` category（方案 B）
- [ ] 部署脚本里有**安装后启动一次**这一步
- [ ] **实测过一次 `adb reboot`**，确认开机后界面自动出现

**性能**
- [ ] 主线程无 IO / 网络
- [ ] 图片做了采样压缩
- [ ] 开了 R8 / ProGuard
- [ ] 冷启动实测 < 3 秒

---

## 10. 调试命令速查（真机实测可用）

设备端 **有** 这些命令：

```
am  pm  settings  input  screencap  screenrecord  dumpsys  logcat
monkey  dpm  getevent  sendevent  setprop  ps  top  ip  ifconfig
ping  netstat  cp  mv  rm  ls  date  reboot  grep
```

设备端 **没有** 这些命令（写了会失败）：

```
cmd（Android 6+ 才有）  su  sed  head  nproc  tar  curl  wget  toybox  busybox
```

> 注意 `grep` **有**、但 `sed` **没有**——这个组合容易踩坑（常见写法 `... | sed -n '/x/,/y/p'` 会直接失败）。

### 常用操作

```bash
# 看日志
adb logcat -v time | findstr "你的TAG"

# 截图 / 录屏
adb shell screencap -p /sdcard/a.png && adb pull /sdcard/a.png
adb shell screenrecord --time-limit 20 /sdcard/a.mp4

# 模拟点击/滑动/输入（可做自动化测试）
adb shell input tap 360 640
adb shell input swipe 360 1000 360 200
adb shell input keyevent 3          # Home
adb shell input keyevent 4          # Back
adb shell input text "hello"

# 启动 / 强停应用
adb shell am start -n com.example.jiushouji/.MainActivity
adb shell am force-stop com.example.jiushouji

# 手动触发开机广播测试（注意：5.1 的 am broadcast 【不支持 -p】，必须用 -n 指定组件）
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED \
    -n com.example.jiushouji/.BootReceiver

# 真实重启测试（最终验证必须用这个）
adb reboot

# 查看当前前台界面
adb shell "dumpsys window | grep mCurrentFocus"

# 改系统设置
adb shell settings get system screen_off_timeout
adb shell settings put system screen_off_timeout 2147483647
```

> ⚠️ 设备端**没有 `head`**，所以 `dumpsys meminfo | head` 会报
> `/system/bin/sh: head: not found`。要用 `grep` 或 `sed`。

---

## 11. 已知坑汇总

| 坑 | 现象 | 对策 |
|---|---|---|
| 应用 `stopped` 状态 | 开机自启不生效 | 装完必须启动一次 |
| 只有 `armeabi-v7a` | 三方 SDK 崩溃 | 接 SDK 前检查 `lib/` 目录 |
| 声明有指南针但无磁力计 | `getDefaultSensor` 返回 null | 一律判空，别信 `hasSystemFeature` |
| 声明有前置摄像头但只有 1 个 | 前后摄逻辑错乱 | 用 `getNumberOfCameras()` 遍历 |
| 无 GMS | Firebase / Maps 崩溃 | 彻底不用 Google 依赖 |
| WebView 37 | H5 白屏 / 语法报错 | 写 ES5 + polyfill |
| CPU 空闲只有 1 核 | 误以为性能极差 | **负载下 4 核全开**（已实测复现）；启动路径仍按单核预算 |
| `pm disable-user` 全部失败 | 无法禁用原桌面 | shell 无 `CHANGE_COMPONENT_ENABLED_STATE`；改走 Device Owner |
| 设备端没有 `sed` | 管道脚本报错 | 用 `grep` / `awk` 替代 |
| `hwrotation=180` | 理论上可能导致传感器坐标翻转 | **实测加速度计正常**，无需特殊处理；但锁竖屏更稳 |
| 无中文输入法 | 中文输不进去 | 自带软键盘或允许英文 |
| 无 `cmd` 命令 | `cmd package ...` 全失败 | 用 `pm` / `am` 替代 |
| 无 `head` 命令 | 管道取前几行失败 | 用 `grep` 替代 |
| `/proc/loadavg` 基线恒为 ~7 | 误判为负载高 | 由 7 个 MTK 内核线程的 D 状态垫高，属平台固有（见 §11.1） |
| 序列号是占位值 | 多台设备 ID 冲突 | 首启生成 UUID 存本地 |

### 11.1 关于 `/proc/loadavg` 恒为 ~7（已查清）

早期观察：负载平均恒显示 `7.13 / 7.17 / 7.19`，而 `top` 显示 CPU 几乎全空闲，
一度被误判为「内核计数异常」。

**实测查清真相**：系统里有 **7 个长期处于 `D`（不可中断睡眠）状态的内核线程**：

```
pmic_thread_kth    电源管理线程
bat_thread_kthr    电池线程
hang_detect        死机检测
wdtk-0  wdtk-1  wdtk-2  wdtk-3    看门狗线程（4 个）
```

这些都是 MTK 平台的标准内核线程，**D 状态是正常现象**（阻塞在等待硬件中断/信号量），
但它们会被计入 loadavg，把基线永久垫高到 ≈7。

**所以 loadavg 不是坏的，而是基线被污染了**：

- ❌ 不能直接读绝对值判断应用负载（7.13 是正常的空闲值）
- ✅ 可以做**增量对比**：施加负载后确实从 7.13 涨到 8.34
- ✅ 要精确看 CPU 利用率，用 `top` 或 `/proc/stat` 两次采样求差

原始证据：[`../raw/dstate-threads.txt`](../raw/dstate-threads.txt)

---

## 12. 相关文档

| 文档 | 内容 |
|---|---|
| `01-设备评估报告.md` | 完整设备评估、平台识别、连接过程 |
| `03-连接与部署环境.md` | 电脑侧 adb 环境、驱动、连接排错 |
| `../raw/` | 全部原始实测数据（`getprop`、`pm list features` 等） |
| `../skills/android-adb-connect/` | 连接与诊断流程技能 |

---

*本指南基于 2026-10-13 的真机实测。设备端任何改动后请重新核对 `raw/` 目录数据。*
