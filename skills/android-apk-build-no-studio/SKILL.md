---
name: android-apk-build-no-studio
description: 在一台没有 JDK、没有 Android SDK、没有 Gradle 的 Windows 机器上，从零下载工具链并手工构建出可安装的 APK。当用户说「写个 apk 装到手机上」「没有 Android Studio 怎么打包」「帮我编译个 apk」「aapt2/d8 怎么用」或需要为旧安卓设备（API 16~22）做一个极简测试/验证用 APK 时使用。核心是四条命令链 aapt2 → javac → d8 → apksigner，以及三个必踩的坑（javac 的 bootclasspath、老 android.jar 缺 API、JDK 国内下载源）。
agent_created: true
---

# 无 Android Studio 手工构建 APK

## 为什么需要这个 skill

需求常见于：给旧安卓设备做一个极简验证用 APK（测 Kiosk、测自启、测兼容性），
但机器上只有 `python`/`node`，**没有 JDK、没有 SDK、没有 Gradle**。

装 Android Studio 要 1GB+ 且要管理员权限。**手工构建只要 ~250MB 下载量、零安装**，
全部产物解压即用。

---

## 一、下载工具链（3 个文件）

放到同一个工作目录（例如 `D:\android-build`）。

### 1. JDK 17（Windows zip，免安装）

```bash
# ✅ 微软 OpenJDK：国内速度好，走 aka.ms 重定向
curl -sL -o msjdk17.zip "https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip"
unzip -q msjdk17.zip     # → jdk-17.x.x+x/
```

> ⚠️ **别用 `api.adoptium.net`**：实测国内只有 ~400 KB/s，186MB 要下 8 分钟以上。
> ⚠️ **别用清华镜像**：`mirrors.tuna.tsinghua.edu.cn` 会以"非常用软件特征"**拒绝 curl**，
> 返回一个"抱歉，您目前无法访问此页面"的 HTML。
> ⚠️ 下完先 `ls -la` 核对大小，`curl` 中途断掉会留下半截文件。

### 2. Android build-tools（含 aapt2 / d8 / zipalign / apksigner）

```bash
curl -sL -o build-tools.zip "https://dl.google.com/android/repository/build-tools_r34-windows.zip"
unzip -q build-tools.zip     # → android-14/  （注意：目录名是 android-14，不是 34）
```

解压后应能看到：`aapt2.exe`、`d8.bat`、`zipalign.exe`、`apksigner.bat`、`dexdump.exe`。

### 3. android.jar（编译期的 Android API 存根）

```bash
# Maven Central 上的官方存根，约 13MB，含 resources.arsc 和 1698 个 class
curl -sL -o android-4.1.1.4.jar \
  "https://repo1.maven.org/maven2/com/google/android/android/4.1.1.4/android-4.1.1.4.jar"
```

- 这是 **API 16（Android 4.1.1）** 的 jar，体积小、下载稳
- **含 `resources.arsc`**，所以 `aapt2 link -I` 能用它解析 `@android:` 资源 ✅
- ⚠️ **它没有 API 17+ 的任何方法**。要调用新 API（如 API 21 的 `startLockTask()`），
  **必须用反射**（见下文 §四）
- 只在**编译期**用，不会进 APK，无需担心信任问题

---

## 二、构建四条命令链

### 目录结构

```
project/
├── AndroidManifest.xml
├── res/xml/xxx.xml          （可选）
└── src/com/example/app/*.java
```

### 完整脚本（可直接复制使用）

```bash
#!/bin/bash
set -e
ROOT=/d/android-build
JDK=$ROOT/jdk17
BT=$ROOT/android-14
AJ="D:/android-build/android-4.1.1.4.jar"      # 注意：给原生 exe 要用 Windows 路径

export JAVA_HOME="D:\\android-build\\jdk17"
export PATH="$JDK/bin:$PATH"
cd "$(dirname "$0")"

echo "[1/6] aapt2 compile 资源"
"$BT/aapt2.exe" compile --dir res -o compiled.zip

echo "[2/6] aapt2 link 生成基础 APK"
"$BT/aapt2.exe" link -o base.apk \
    -I "$AJ" --manifest AndroidManifest.xml -R compiled.zip \
    --java gen --min-sdk-version 16 --target-sdk-version 22

echo "[3/6] javac 编译 Java"
mkdir -p classes
javac --release 8 -nowarn -encoding UTF-8 \
    -classpath "$AJ" -d classes $(find src gen -name "*.java")

echo "[4/6] d8 转 classes.dex"
mkdir -p dexout
"$BT/d8.bat" --lib "$AJ" --min-api 16 --output dexout $(find classes -name "*.class")

echo "[5/6] 打包 + zipalign"
cp base.apk unsigned.apk
( cd dexout && "$JDK/bin/jar.exe" uf ../unsigned.apk classes.dex )
"$BT/zipalign.exe" -p -f 4 unsigned.apk aligned.apk

echo "[6/6] 签名"
if [ ! -f kiosk.keystore ]; then
  "$JDK/bin/keytool.exe" -genkeypair -keystore kiosk.keystore -alias kiosk \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass android -keypass android \
    -dname "CN=Test, OU=Dev, O=Org, L=City, C=CN"
fi
"$BT/apksigner.bat" sign --ks kiosk.keystore --ks-pass pass:android \
    --key-pass pass:android --out app-signed.apk aligned.apk
"$BT/apksigner.bat" verify --verbose app-signed.apk
```

### 安装

```bash
adb install -r app-signed.apk
```

`apksigner verify` 应输出 `Verifies` + `Verified using v1 scheme: true`。
**Android 5.1 只要求 v1 签名**，所以 v1 通过即可安装。

---

## 三、三个必踩的坑

### 坑 1：`javac -bootclasspath` 会把 JDK 自己的 `java.lang` 顶掉

```
$ javac -bootclasspath android.jar ...
致命错误: 在类路径或引导类路径中找不到程序包 java.lang
```

**原因**：`-bootclasspath` 会**完全替换**引导类路径，而 android.jar 里没有 `java.lang`。

**正确做法**：用 `--release 8` + `-classpath`：

```bash
javac --release 8 -nowarn -encoding UTF-8 -classpath android.jar -d classes src/*.java
```

- `--release 8` 让 `java.*` 走 JDK 自带的 JDK8 API 签名
- `-classpath android.jar` 提供 `android.*`
- 产物是 Java 8 字节码，d8 能吃

### 坑 2：老 android.jar 缺新 API → 用反射

`android-4.1.1.4.jar` 是 API 16，没有 API 17~22 的方法。
直接写 `startLockTask()`、`setLockTaskPackages()`、`wipeData()`、`isDeviceOwnerApp()`
**编译不过**。

**做法**：全部用反射，编译期不碰符号：

```java
private void startLock() {
    try {
        Method m = Activity.class.getMethod("startLockTask");   // API 21
        m.invoke(this);
    } catch (Throwable e) {
        Log.e(TAG, "startLockTask 失败: " + e);
    }
}

private void setLockTaskPackages() {
    DevicePolicyManager dpm = (DevicePolicyManager)
            getSystemService(Context.DEVICE_POLICY_SERVICE);
    Method m = DevicePolicyManager.class.getMethod(
            "setLockTaskPackages", ComponentName.class, String[].class);   // API 21
    m.invoke(dpm, admin(), new String[]{ getPackageName() });
}
```

这样编译能过，运行在支持该 API 的设备上就正常。

> 如果一定要直接调用新 API，就得搞一份对应版本的 android.jar（要装 SDK Manager，麻烦得多）。
> 反射是"零额外下载"的解法。

### 坑 3：路径格式混用

- 原生 exe（aapt2/d8/zipalign/apksigner）在 Git Bash 里用 `/d/xxx` 能跑，
  但**参数里的文件路径建议给 Windows 格式**（`D:/android-build/...`），最稳
- `apksigner.bat` / `d8.bat` 是批处理，**必须能找到 java** →
  先 `export JAVA_HOME` 并把 `$JDK/bin` 加进 PATH
- `jdk-17.0.20.1+1` 这种带 `+` 的目录名容易出问题，**先 `mv` 成 `jdk17`**

---

## 四、需要 DeviceAdminReceiver 时的 Manifest 片段

`dpm set-device-owner` 要求接收器带 `android.app.device_admin` meta-data，
**这个 meta-data 指向一个编译过的 XML 资源**，所以**必须用 aapt2 处理 res/**，
不能只打包 manifest。

`AndroidManifest.xml`：

```xml
<receiver android:name=".AdminReceiver"
    android:permission="android.permission.BIND_DEVICE_ADMIN"
    android:exported="true">
    <meta-data android:name="android.app.device_admin"
        android:resource="@xml/device_admin" />
    <intent-filter>
        <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
    </intent-filter>
</receiver>
```

`res/xml/device_admin.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<device-admin xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-policies>
        <force-lock />
        <wipe-data />
    </uses-policies>
</device-admin>
```

`AdminReceiver.java` 可以是个空壳：

```java
public class AdminReceiver extends DeviceAdminReceiver { }
```

---

## 五、验证构建结果

```bash
# APK 内容（应有 AndroidManifest.xml / resources.arsc / classes.dex）
"$JDK/bin/jar.exe" tf app-signed.apk

# dex 里确认字符串进去了（验证改动生效）
"$BT/dexdump.exe" -d dexout/classes.dex | grep "你的特征字符串"

# 签名
"$BT/apksigner.bat" verify --verbose app-signed.apk

# 装到设备后确认 ABI
adb shell "dumpsys package <pkg> | grep -E 'codePath|versionName'"
adb shell "logcat -d | grep 'abi='"     # 应看到 abi=armeabi-v7a 之类
```

> 💡 **"构建产物大小完全没变"** 是可疑信号 —— 说明改动可能没进包。
> 用 `dexdump | grep` 确认新字符串真的在 dex 里。

---

## 六、给旧设备（API 16~22）的额外注意

- `minSdkVersion` 要 ≤ 设备 API。给 Android 5.1 做就设 16~21
- `targetSdkVersion` 建议 22 以内，规避后续 API 的行为变更
- 只出 `armeabi-v7a`（如果设备只支持这个）：`d8 --min-api` 不控制 ABI，
  ABI 取决于**打进 APK 的 .so**。纯 Java 项目无此问题
- 用 `@android:style/Theme.NoTitleBar.Fullscreen` 这类**框架资源**没问题，
  aapt2 用 `-I android.jar` 就能解析
