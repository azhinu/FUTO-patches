#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/dynamic-tests
javac --release 11 -d build/dynamic-tests $(find tests/dynamic-stubs -name '*.java') extensions/src/main/java/futo/enhancements/Reflect.java extensions/src/main/java/futo/enhancements/Dynamic.java tests/DynamicIntegrationTest.java extensions/src/main/java/futo/enhancements/SpacebarDuration.java tests/SpacebarDurationTest.java extensions/src/main/java/futo/enhancements/SwipeBackspace.java tests/SwipeBackspaceTest.java
java -cp build/dynamic-tests futo.enhancements.DynamicIntegrationTest
java -cp build/dynamic-tests futo.enhancements.SpacebarDurationTest
java -cp build/dynamic-tests futo.enhancements.SwipeBackspaceTest
