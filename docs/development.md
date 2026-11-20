# Development

All compilation and tests run in Docker. There is no host Java/Android SDK setup
to maintain. Docker MCP can perform the same operations without the Docker CLI.

## Toolchain

The Dockerfile installs Java 21, Kotlin 2.2.21, Android platform 36 (r02), Android
build tools 36, Morphe Desktop 2.2.0, org.json 20250517, Python 3 and eSpeak NG.
The image targets linux/amd64 because the Android Linux build tools require it.
Download URLs and verified SHA-256 sums are in `scripts/setup-container.sh`.

`MORPHE_CLI` can override the CLI JAR path; its default is
`/opt/morphe/morphe-cli.jar`. Android paths are image-internal, not host paths.

## With Docker MCP

1. Build this repository root with `docker_build`, using a tag such as
   `futo-keyboard-enhancements:dev`.
2. Start a container with the repository bound to `/project` and working
   directory `/project`. A persistent development container can use
   `["sleep", "infinity"]` as its command.
3. For signed desktop updates, also bind the host's `.local/morphe-data` directory
   to `/opt/morphe/morphe-data`.
4. Use `docker_container_exec` to run:

```sh
bash scripts/test.sh
bash scripts/build.sh
python3 scripts/test-matrix.py .local/apks/futo-playstore.apk
python3 scripts/package-release.py
```

The first two commands work without a FUTO APK. The matrix needs your unpatched
Play Store build. A phone and ADB are needed only for runtime checks.

## Local files

Use `.local/apks/` for APK inputs and `build/` for generated output. Do not commit
original/patched APKs, signing keys, credentials, decompilations or upstream
repositories. Put temporary reverse-engineering work outside the public source
tree, or under ignored `.local/`.

Morphe's JAR-adjacent data directory is bound to `.local/morphe-data` by Compose.
Back it up privately if its signing key is used by an installed app. The Android
test harness has its own unrelated key in `.local/device-test/`.

The default container user is root. On Linux, to create files as your host user:

```sh
LOCAL_UID=$(id -u) LOCAL_GID=$(id -g) make build
```

## Working on patches

Use the APK's actual optimized DEX to verify target names and signatures. Source
names may disappear or change under R8. `scripts/Inspect.kt` is a small dexlib2
inspection helper; the integration matrix compiles it to `build/inspect.jar`.

Keep hooks structural and narrow. Do not restore a version whitelist or a frozen
whole-method hash gate. Missing or ambiguous injection points should fail before
an APK is published.

Code and comments are English. Log and error messages begin with a capital letter.
Do not log credentials, recorded audio or editor contents outside the synthetic
test harness.

## Releases

Update `VERSION` and `CHANGELOG.md`, then run `make test` and `make release`.
When an original APK is available, also run `make integration`.

Release packaging uses an explicit source allowlist and includes the MPP and
checksums. It excludes `.local`, signing material, APKs and generated test output.
A patched APK is a local installation artifact, not part of the source release.

The GitHub workflow runs the image build, offline tests and release packaging.
Device tests, APK matrix checks and paid provider requests are separate.
