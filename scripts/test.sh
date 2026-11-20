#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/tests
javac --release 11 --add-modules jdk.httpserver -cp /opt/json.jar -d build/tests extensions/src/main/java/futo/enhancements/SpeechBackend.java tests/SpeechBackendTest.java
java --add-modules jdk.httpserver -cp build/tests:/opt/json.jar futo.enhancements.SpeechBackendTest
bash scripts/test-keyboard.sh
