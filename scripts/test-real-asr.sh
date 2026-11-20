#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/generate-audio.py
mkdir -p build/tests
javac --release 11 -cp /opt/json.jar -d build/tests extensions/src/main/java/futo/enhancements/SpeechBackend.java tests/RealSpeechIntegration.java
java -cp build/tests:/opt/json.jar futo.enhancements.RealSpeechIntegration "$@"
