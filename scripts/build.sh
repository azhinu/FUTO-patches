#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
CLI="${MORPHE_CLI:-/opt/morphe/morphe-cli.jar}"
ANDROID=/opt/android/android-36/android.jar
BT=/opt/android/android-16
VERSION=$(cat VERSION)
rm -rf build/classes build/extension build/resources build/patchdex
mkdir -p build/classes build/extension build/resources build/dist
/opt/kotlinc/bin/kotlinc -Xskip-metadata-version-check -jvm-target 17 -cp "$CLI" patches/src/main/kotlin -d build/classes
if find extensions/src/main/java -name '*.java' -print -quit | read -r _; then
  javac --release 11 -cp "$ANDROID" -d build/extension $(find extensions/src/main/java -name '*.java')
  jar cf build/extension.jar -C build/extension .
  "$BT/d8" --min-api 26 --lib "$ANDROID" --output build/resources build/extension.jar
  mv build/resources/classes.dex build/resources/enhancements.mpe
fi
python3 scripts/generate-audio.py
cp build/test-audio/en.wav build/resources/test-en.wav
cp build/test-audio/ru.wav build/resources/test-ru.wav
jar cf build/patches.jar -C build/classes .
mkdir -p build/patchdex
"$BT/d8" --min-api 26 --lib "$ANDROID" --classpath "$CLI" --output build/patchdex build/patches.jar 2>build/d8.log
cp build/patchdex/classes.dex build/resources/classes.dex
cat >build/bundle.mf <<EOF
Manifest-Version: 1.0
Name: FUTO Enhancements
Version: $VERSION
Patcher-Version: 2.3.0
Description: Independent FUTO keyboard enhancements
EOF
jar cfm build/dist/futo-enhancements-$VERSION.mpp build/bundle.mf -C build/classes . -C build/resources .
echo "Built build/dist/futo-enhancements-$VERSION.mpp"
