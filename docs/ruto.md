# RUTO adaptation

Inspected on 2026-11-21 using the source repository and the APK linked by the
author's [README](https://github.com/MisterFenek/ruto-keyboard):
[futo-keyboard-ru-1.0.2.apk](https://shared.mrfenek.eu.cc/futo-keyboard-ru-1.0.2.apk).
The APK was downloaded for local inspection, not added to the source release.

| Property | Actual APK value |
| --- | --- |
| Application ID | `org.futo.inputmethod.latin.unstable` |
| Version name | `1.0.1-ru-unstable` |
| Version code | `101` |
| Target SDK | `35` |
| Java namespace | `org.futo.inputmethod.*` / `org.futo.voiceinput.*` |
| IME service | `org.futo.inputmethod.latin.LatinIME` |
| IME metadata resource | `xml/method`, ID `0x7f150001` |
| Original compiled XML path | `res/aY.xml` |
| Original spell suppression | `true` |

SHA-256 of the original downloaded APK:

```text
9025067e01bcc4cac9ed16bd5124c3b669f3ca7c2821633d61a674ddc71b3ce3
```

## Compatibility changes

All six public patches now declare the original FUTO Play Store application ID
and the inspected RUTO application ID. No package renaming is performed. The
unstable ID is also used by FUTO builds: the brand is not inferred from it, and
each patch still validates its required classes, methods, fields and instructions.
No release whitelist or broad constructor guess was added.

The following differences were confirmed with dexlib2 and aapt2:

- `AudioRecognizer.<init>` has the same five parameters except for parameter
  three: FUTO's `kotlinx.coroutines.flow.SharingConfig` becomes RUTO's
  `org.acra.scheduler.SchedulerStarter` after R8 merging. The patch accepts these
  two complete signatures and requires exactly one match. The `floatSamples`,
  model-verification block, `runnerCallback` and scratch-register guards remain.
- RUTO retains two eight-parameter `UserSetting` constructors. The runtime now
  selects `(int, Integer, Integer, List, Function2, boolean, boolean, Function2)`
  explicitly, avoiding the synthetic overload ending in `(Function2, int)`.
- Morphe resolves `xml/method` for editing. The compiled-APK inspection tools now
  resolve its file path from the resource table instead of assuming
  `res/xml/method.xml`. Subtype and spell-checker patches preserve each other's edits.
- Other hooked signatures and the reflected Compose/coroutine entry points were
  checked against the actual DEX. `org.futo.*` runtime references stay valid;
  substituting `org.ruto.*` would be incorrect.

## Build and apply

```sh
make test
make build
make integration APK=.local/ruto/ruto-1.0.2.apk
make patch APK=.local/ruto/ruto-1.0.2.apk OUTPUT=build/dist/ruto-patched.apk
```

Use `build/dist/futo-enhancements-0.3.1.mpp` in Morphe Manager, select the original
RUTO APK and choose any subset of the six patches. The bundle also retains FUTO
Play Store compatibility. Standard APK installation is required to register the
remote speech patch's INTERNET permission; root mounting alone is insufficient.

## Verified and pending

- Offline JVM suites passed: 66 speech assertions, 8 dynamic-row transitions,
  12 spacebar cases and 8 swipe-backspace cases.
- The real Morphe matrix passed all 14 cases on this APK: six individual patches,
  keyboard controls together, both resource patch orders, all six together,
  unsupported-package rejection, two synthetic versions and missing-method rejection.
- Original libraries and assets were SHA-256 compared in every positive
  combination. Native RUTO fixes, swipe weights, dictionaries and model assets
  were unchanged. New synthetic speech fixtures are added only by the remote patch.
- A signed APK containing all six patches was produced using the apply wrapper.
  The result report confirms all six patches, unchanged package and no failures;
  compiled spell suppression is false and INTERNET/RECORD_AUDIO are present.
- Pixel 8 / Android 17 / API 37: the data-preserving update succeeded after the
  user enabled signature bypass. INTERNET is granted; no app data was cleared.
- Native settings rows, all four backend forms, the spacebar radio control,
  English input, idle/typing/manual row switching and separate English/Russian
  picker entries were observed. Android reports spell suppression false. The
  picker changes keys and exposes en-US / ru-RU. Keyboard height remained 877 px
  during the tested row switches. Remote transmission consent stayed disabled.
- Russian typing initially hit an upstream native SIGSEGV on `Пр`. The same crash
  occurred in unpatched RUTO. Adding the missing GGUF embedding-mixing feature
  flag, without changing weights, eliminated the crash in the same replay and
  after a cold start. The repaired copy was imported into RUTO; the original
  downloaded file remains untouched. See [Diagnosis and reproduction](ruto-native-crash.md).
- This is not full RUTO acceptance: swipe behavior, detailed spacebar timing,
  local/remote dictation, provider billing and successful LanguageTool corrections
  still need separate checks. The native fix does not establish prediction quality.

The original FUTO APK matrix was not rerun during this adaptation because an
unpatched Play Store APK was not available locally. Its original recorder signature
and compatibility declaration remain supported.
