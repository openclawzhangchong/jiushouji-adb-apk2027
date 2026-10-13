#!/bin/bash
# ============================================================
# Kiosk 测试 APK 构建脚本（不依赖 Android Studio / Gradle）
#
# 依赖（放在 /d/android-build/）：
#   jdk17/                      OpenJDK 17
#   android-14/                 Android build-tools 34（aapt2 / d8 / zipalign / apksigner）
#   android-4.1.1.4.jar         android.jar（Maven Central，仅编译期使用）
#
# 用法：bash build.sh
# 产物：kiosk-test-signed.apk
# ============================================================
set -e

ROOT=/d/android-build
JDK=$ROOT/jdk17
BT=$ROOT/android-14
AJ="D:/android-build/android-4.1.1.4.jar"

export JAVA_HOME="D:\\android-build\\jdk17"
export PATH="$JDK/bin:$PATH"

cd "$(dirname "$0")"

echo "[1/6] aapt2 compile 资源"
rm -f compiled.zip
"$BT/aapt2.exe" compile --dir res -o compiled.zip

echo "[2/6] aapt2 link 生成基础 APK"
rm -rf gen base.apk
"$BT/aapt2.exe" link -o base.apk \
    -I "$AJ" \
    --manifest AndroidManifest.xml \
    -R compiled.zip \
    --java gen \
    --min-sdk-version 16 --target-sdk-version 22

echo "[3/6] javac 编译 Java"
rm -rf classes
mkdir -p classes
javac --release 8 -nowarn -encoding UTF-8 \
    -classpath "$AJ" \
    -d classes \
    $(find src gen -name "*.java")

echo "[4/6] d8 转 classes.dex"
rm -rf dexout
mkdir -p dexout
"$BT/d8.bat" --lib "$AJ" --min-api 16 --output dexout \
    $(find classes -name "*.class")

echo "[5/6] 打包 + zipalign"
rm -f unsigned.apk aligned.apk
cp base.apk unsigned.apk
( cd dexout && "$JDK/bin/jar.exe" uf ../unsigned.apk classes.dex )
"$BT/zipalign.exe" -p -f 4 unsigned.apk aligned.apk

echo "[6/6] 签名"
if [ ! -f kiosk.keystore ]; then
    "$JDK/bin/keytool.exe" -genkeypair -keystore kiosk.keystore -alias kiosk \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass android -keypass android \
        -dname "CN=KioskTest, OU=Dev, O=Jiushouji, L=Shenzhen, C=CN"
fi
rm -f kiosk-test-signed.apk
"$BT/apksigner.bat" sign --ks kiosk.keystore --ks-pass pass:android \
    --key-pass pass:android --out kiosk-test-signed.apk aligned.apk
"$BT/apksigner.bat" verify --verbose kiosk-test-signed.apk

echo ""
echo "构建完成: $(pwd)/kiosk-test-signed.apk"
ls -la kiosk-test-signed.apk
