# Testing

## Offline tests

```sh
make test
```

The JVM suites exercise actual runtime helpers against controlled HTTP servers
and small Android/object-shape stubs:

| Suite | Scope |
| --- | --- |
| Speech protocols | 66 assertions: WAV encoding, provider adapters, model filtering, validation, HTTP failures, limits, cancellation and deadlines |
| Dynamic row | 8 transitions: idle, gestures, replacements, manual mode, typing and secure input |
| Spacebar timing | 12 cases: stock, synchronized and custom delays; limits; other keys; live changes |
| Swipe backspace | 8 cases: conversion, holding, suffix input, disabled mode, selection and recorrection |

No live provider credentials or microphone input are used.

## Real Morphe matrix

Provide an original FUTO Play Store or RUTO unstable APK (merge splits first):

```sh
make integration APK=.local/apks/futo-playstore.apk
```

The fourteen cases cover all six patches individually, the two keyboard controls
together, both resource patches in both selection orders, all patches together,
an unsupported package, two synthetic version-name changes and a missing timer
method. Reports, permissions, compiled metadata, package preservation and
return-site hooks are checked. Every original native library and asset is hashed
and compared with the patched output, including RUTO's models and swipe libraries.

Invalid targets must produce no APK. Synthetic fixtures are derived from the
supplied APK at runtime; a second downloaded APK is not required. Version fixtures
retain the same DEX and therefore do not establish another release's compatibility.

Results go to `build/matrix-results.json`. The wrapper in `scripts/apply.py`
also validates the complete patch report before publishing its output.

The RUTO adaptation run on 2026-11-21 passed all fourteen cases and all offline
JVM suites. Its internal version is 1.0.1-ru-unstable (101), despite the download
filename containing 1.0.2. Device results are tracked separately in
[RUTO adaptation](ruto.md); historical FUTO checks below do not certify RUTO.

The connected Pixel 8 subsequently exercised RUTO's native settings, backend
forms, English input, row switching, system layout selection and effective spell
suppression. A Russian SIGSEGV was reproduced on both patched and unpatched RUTO;
the same replay passed after the model metadata repair. The cold-start replay
asserts the actual text, process stability and absence of the native crash:

```sh
python3 scripts/test-ruto-native.py --serial YOUR_ADB_SERIAL --letters 6 --cold-start
```

This coordinate replay requires the documented 1080x2400 Pixel layout and the
synthetic editor harness. See [Native crash diagnosis](ruto-native-crash.md).

## Device harness

Build the separate synthetic editor:

```sh
make device-tests
adb install -r build/dist/futo-device-tests.apk
adb shell am start -n futo.enhancements.test/.MainActivity
```

Its controls select normal, password and no-suggestions fields, switch layouts
and inspect Android spell-checker spans. It logs only synthetic test text.
The harness has its own signing key, separate from FUTO's.

`scripts/build-touch-test.sh` builds an optional ADB-shell gesture runner.
It uses InputManagerGlobal and is not included in the production extension.
Its coordinates are specific to the tested Pixel layout; inspect the screen
before replaying them on another device.

## Recorded device checks

The baseline was FUTO 0.1.30-playstore (11751), Pixel 8 / API 37, with custom
English/Russian layouts. These checks were observed during development:

- Swipe alternatives remain visible; choosing an alternative replaces the word.
- Swipe Уж → backspace У → tapped suffix Уже works without an intervening space.
  Holding removes the edited word; disabling restores stock swipe-word deletion.
- A separate spacebar value survives reopening settings. At custom 125 ms, a
  controlled 220 ms hold followed by movement moves the cursor; Original FUTO,
  with its stock 300 ms delay at a letter setting of 150 ms, leaves it in place.
- Dynamic mode returns to stock rows when disabled, then to one row when enabled.
- System layout selection and FUTO's internal language switching synchronize.
- Spell suppression is false; the installed checker did not return correction
  spans in the tested editors. That is not a successful LanguageTool acceptance.
- Standard installation retains app data and grants INTERNET.
- An isolated test UID verified Keystore encryption, fresh IVs, non-exportability,
  malformed ciphertext handling and credential removal.

Historical rotation, original local input and failure/cancel checks were made
on earlier bundles. They are not a claim that every flow is re-tested per release.

## Optional live ASR check

```sh
docker compose run --rm dev bash scripts/test-real-asr.sh \
  http://your-asr-host:9000/asr openai_whisper
```

This generates synthetic audio locally and makes real requests to a configured
Whisper ASR Webservice. It is not part of offline tests or CI.

Earlier checks used the official service's tiny CPU models. English requests
completed with recognition errors; the Russian fixture was not recognized
reliably. Those runs used previous fixtures. Generated fixtures in the current
build have not been newly validated for recognition quality.

## Open acceptance work

Successful paid OpenRouter transcription, end-to-end remote insertion and local
fallback on the phone, reliable Russian recognition, successful checker
corrections, and broad editor/script/device coverage remain unverified.
