#!/usr/bin/env bash
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends openjdk-21-jdk-headless curl ca-certificates unzip zip python3 espeak-ng

# Downloaded tools stay in the image, never in the source tree.
download() {
    local url="$1" checksum="$2" destination="$3"
    curl --fail --location --retry 2 "$url" --output "$destination"
    printf '%s  %s\n' "$checksum" "$destination" | sha256sum --check --status
}

mkdir -p /opt/android /opt/morphe
download https://github.com/JetBrains/kotlin/releases/download/v2.2.21/kotlin-compiler-2.2.21.zip \
    a623871f1cd9c938946948b70ef9170879f0758043885bbd30c32f024e511714 /tmp/kotlin.zip
unzip -q /tmp/kotlin.zip -d /opt

download https://dl.google.com/android/repository/platform-36_r02.zip \
    37607369a28c5b640b3a7998868d45898ebcb777565a0e85f9acf36f29631d2e /tmp/platform.zip
unzip -q /tmp/platform.zip -d /opt/android

download https://dl.google.com/android/repository/build-tools_r36_linux.zip \
    5d9ac77fb6ff43d9da518a337b4fcf8f9097113df531d99ccefe80ef7ce8250b /tmp/build-tools.zip
unzip -q /tmp/build-tools.zip -d /opt/android

download https://repo.maven.apache.org/maven2/org/json/json/20250517/json-20250517.jar \
    3ea61b2a06e31edf1c91134fe9106b0ebb16628be169f3db75bc7a2b06b45796 /opt/json.jar

download https://github.com/MorpheApp/morphe-desktop/releases/download/v2.2.0/morphe-desktop-2.2.0-all.jar \
    5ca0320ee05481df1ae3afd2f003bba3ff994d11d1f66ae48811ced71355e4fe /opt/morphe/morphe-cli.jar

rm -rf /var/lib/apt/lists/* /tmp/kotlin.zip /tmp/platform.zip /tmp/build-tools.zip
java -version
/opt/kotlinc/bin/kotlinc -version
echo "Container toolchain is ready"
