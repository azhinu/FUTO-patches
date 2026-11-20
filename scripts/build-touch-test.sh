#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/touch/classes build/touch/dex
javac --release 11 -cp /opt/android/android-36/android.jar -d build/touch/classes tests/device/Touch.java
jar cf build/touch/touch.jar -C build/touch/classes .
/opt/android/android-16/d8 --min-api 26 --lib /opt/android/android-36/android.jar --output build/touch/dex build/touch/touch.jar
