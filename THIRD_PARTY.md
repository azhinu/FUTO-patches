# Third-party tools and projects

This repository stores patch/runtime sources, tests and original screenshots.
It does not vendor the projects below, FUTO APKs or their source trees.

| Project | Use | Version used by the build |
| --- | --- | --- |
| [FUTO Keyboard](https://github.com/futo-org/android-keyboard) | Target application and architecture reference | Tested Play Store baseline 0.1.30 |
| [Morphe Desktop](https://github.com/MorpheApp/morphe-desktop) | Compiler classpath and patching CLI | 2.2.0 |
| [Morphe Patcher](https://github.com/MorpheApp/morphe-patcher) | Patch API provided by the CLI/Manager | Bundle minimum 2.3.0 |
| [Morphe Manager](https://github.com/MorpheApp/morphe-manager) | Phone-side bundle import and patching | Tested 2.5.0 |
| [Kotlin](https://github.com/JetBrains/kotlin) | Patch compiler | 2.2.21 |
| [Android SDK](https://developer.android.com/tools) | Android stubs, D8, aapt2 and signing tools | Platform 36 r02 / build tools 36 |
| [JSON-java](https://github.com/stleary/JSON-java) | JVM speech tests | 20250517 |
| [eSpeak NG](https://github.com/espeak-ng/espeak-ng) | Locally generated synthetic speech fixtures | Ubuntu package |

Downloads and SHA-256 checks live in `scripts/setup-container.sh`. They are fetched
from official release, Android SDK or Maven Central locations into the Docker image.

[APKEditor](https://github.com/REAndroid/APKEditor) and
[JADX](https://github.com/skylot/jadx) were used during reverse engineering and are
optional inspection tools, not build dependencies.

The screenshots in `docs/images/` were captured during this project's device
checks. Audio fixtures are synthesized locally; no user recording is included.

Each upstream project retains its own license and attribution requirements.
No license grant for FUTO or third-party tools is implied by this repository.
