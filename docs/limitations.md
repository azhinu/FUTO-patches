# Limitations

## Compatibility

The real release tested is 0.1.30-playstore (11751). Other Play Store versions can
be attempted, but R8 changes can invalidate reflected APIs or injection structures.
GitHub/F-Droid flavors are not declared compatible.

Morphe Manager 2.5.0 / Patcher 2.3.0 loaded the bundle during development.
The build uses the pinned Desktop CLI for JVM-side patching tests.

## Remote speech

Actual paid OpenRouter transcription and the complete phone recorder-to-insertion
path have not been accepted end to end. Protocol tests and catalog discovery are
separate evidence. Local fallback and late successful provider responses still
need broader phone tests.

A root mount cannot register a newly requested INTERNET permission. Use a standard
APK installation for remote speech. Signing identities and app data must be
preserved across updates.

Provider limits may be lower than the client's limits. Cancellation cannot
guarantee that a server has not already processed or billed an uploaded request.
VAD and timestamps depend on the ASR engine. TLS validation is never disabled.

## Keyboard behavior

Swipe-backspace checks cover the tested Russian gesture, suffix editing, holding,
runtime disabling and helper-level selection/recorrection guards. Other scripts
and unusual combiner behavior are not exhaustively tested.

The switch defaults off. Disabling restores the original behavior for subsequent
gesture words; it does not reconstruct a word already edited as ordinary text.

Spacebar timing defaults to Same as keyboard, retaining the earlier patch's
behavior. Original FUTO opts out at runtime. Custom uses a separate millisecond
value. Locked-user input falls back to the original delay.

## Android integration

Additional input-method subtypes use a deprecated API that worked on the baseline
phone. Explicit enabling uses API 34+. Layouts refresh on IME start or language
switch, not through a permanent settings watcher.

The selected editor, Android text services and checker determine spell-checking
language sessions and corrections. Allowing a checker does not repair it, force
a language change, or make all custom editors participate.

## Build and tests

SDK/tool archives are downloaded into the Docker image, not stored in this repo.
An original APK is intentionally supplied by the developer for integration tests.
CI covers the bundle build and offline tests, not on-device behavior.

D8 can warn about Kotlin metadata on the inline Morphe extendWith supplier.
The tested Manager loaded that bundle; the warning is not treated as a reason
to remove validation or increase the declared compatibility.
