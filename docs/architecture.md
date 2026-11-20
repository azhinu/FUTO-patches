# Architecture

## Delivery

The Kotlin patches use Morphe's modern `patch { execute { ... } }` API. Each named
patch is opt-in. Shared dependencies inject a Java runtime extension once and
add native settings hooks only where required.

Package compatibility targets the Play Store identifier without restricting
versions. Method signatures, field types and semantic instruction anchors guard
the injection points. There is no whole-method hash whitelist.

## Dynamic action/suggestion row

`Dynamic` keeps weak per-UixManager state for composition, manual selection and
action activation. WordComposer typing/batch state and real replacement candidates
determine whether to show suggestions. Idle predictions alone do not hide actions.

The hook runs after the original `LatinIME.setSuggestions` publication, before
each return. That order matters: a gesture can commit a word while its replacement
candidates still need to stay visible. A new batch candidate object or composing
boundary clears a temporary manual override.

Original ActionBar callbacks and ordering remain in use. A wrapped DataStoreItem
makes the existing layout use a single row; the stored old-style preference is
never rewritten. Disabling restores the original accessor/render behavior.

## Speech

```text
FUTO recorder / FloatBuffer
  → Remote dispatch
  → in-memory PCM WAV
  → provider-specific request adapter
  → cancellable HTTP transport
  → validated transcript
  → original FUTO completion and insertion flow
```

Local FUTO is the default. OpenRouter supports JSON/Base64 and multipart;
Whisper ASR Webservice uses its audio_file upload and query parameters; custom
OpenAI-compatible servers use file multipart uploads. These adapters share
transport, deadlines, size limits and cancellation.

There are no automatic retries or redirects. A generation token prevents late
delivery after cancellation. Fallback to the local model is explicitly opt-in.
The extension clears its own PCM/WAV copies; FUTO owns its original recorder buffer.

Credentials use AES/GCM with a non-exportable Android Keystore key. Only encrypted
credential values enter app-private preferences. Backup rules exclude those
preferences. TLS uses Android's normal validation; plain HTTP requires a separate
opt-in for custom/ASR endpoints.

The remote patch adds INTERNET to the Manifest. Android registers that permission
on standard installation; mounting another APK over an existing installation
does not update PackageManager's permission grant.

## Settings

The extension adds UserSetting rows and a native SettingsNavigator route.
Switches, radio controls, sliders, pickers, navigation and tips are FUTO's own
Compose components. Outlined inputs use its bundled Material 3 library through
AndroidView. Existing secrets are not prefilled; password inputs disable autofill
and state saving.

Activity references are weak. Settings save immediately; invalid timeouts show
an inline error without overwriting the last valid value.

## Swipe backspace

Before `InputLogic.onCodeInput` processes backspace at the end of a gesture word,
the optional hook resumes it through `WordComposer.setComposingWord`, using
`IMEHelper.getCodepointCoordinates`. The original character-delete/combiner path
then runs instead of the batch-mode whole-word rejection path.

A weak per-composer marker makes holding backspace delete the edited gesture word,
including after letters are appended. Ordinary typed words retain their existing
hold preference. Separators, empty composition, selection, cursor recorrection
and disabling clear the marker. The original hold preference is never changed.

## Spacebar timing

The patch finds the unique Handler.sendMessageDelayed call in
PointerTracker.startLongPressTimer and validates the preceding integer-to-long
conversion. A helper changes only the spacebar delay at that boundary:

- Original FUTO returns the original delay.
- Same as keyboard reads the current letter-duration setting.
- Custom reads a separate bounded value from the native millisecond slider.

Other keys, gesture cancellation and the configured hold action stay original.
Preferences are read live; switching modes needs no new APK.

## Android integration

BridgeIme forwards Android's subtype-change callback to SubtypeSync. Enabled
FUTO layouts remain authoritative; stable subtype IDs derive from layout
identities, and duplicate IDs fail before registration. Android and FUTO changes
are synchronized with a recursion guard.

The spell-checker patch changes only the existing IME metadata suppression flag.
It does not implement a checker or inject system corrections into FUTO predictions.
