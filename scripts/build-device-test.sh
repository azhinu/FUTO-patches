#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
BT=/opt/android/android-16
ANDROID=/opt/android/android-36/android.jar
rm -rf build/device/classes build/device/dex
mkdir -p build/device/classes build/device/dex build/dist .local/device-test
javac --release 11 -cp "$ANDROID" -d build/device/classes tests/device/MainActivity.java tests/device/SecretsTestReceiver.java extensions/src/main/java/futo/enhancements/Config.java extensions/src/main/java/futo/enhancements/Secrets.java
jar cf build/device/classes.jar -C build/device/classes .
"$BT/d8" --min-api 26 --lib "$ANDROID" --output build/device/dex build/device/classes.jar
"$BT/aapt2" link -I "$ANDROID" --manifest tests/device/AndroidManifest.xml -o build/device/unsigned.apk
(cd build/device/dex && zip -q ../unsigned.apk classes.dex)
if [ ! -e .local/device-test/test.jks ]; then
 keytool -genkeypair -alias test -keyalg RSA -keystore .local/device-test/test.jks -storepass android -keypass android -dname "CN=FUTO Integration Tests" -validity 3650 >/dev/null 2>&1
fi
"$BT/zipalign" -f 4 build/device/unsigned.apk build/device/aligned.apk
"$BT/apksigner" sign --ks .local/device-test/test.jks --ks-key-alias test --ks-pass pass:android --key-pass pass:android --out build/dist/futo-device-tests.apk build/device/aligned.apk
echo "Built build/dist/futo-device-tests.apk"
