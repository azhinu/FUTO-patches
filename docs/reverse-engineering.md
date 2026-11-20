# Hook reference

The baseline is the optimized Play Store APK, not the public GitHub flavor.
JADX, aapt2 and dexlib2 inspection were used to verify actual signatures.
Downloaded APKs, decompilations and upstream source checkouts are not kept here.

| Boundary | Purpose |
| --- | --- |
| LatinIME.onCreate | Initialize extension context after Android attaches the service |
| LatinIME.onStartInputView | Reset dynamic state and register layouts before each return |
| LatinIME.setSuggestions | Update the shared row after original candidate publication |
| UixManager action callbacks | Preserve manual switching and action activation |
| ActionBarKt / SettingsKt | Reuse original single-row rendering and sizing |
| SettingsActivity / UserSettingsMenu | Attach native preference rows |
| SettingsNavigatorKt static initialization | Register the remote-speech page |
| AudioRecognizer run/reset/constructor/preload coroutine | Dispatch remote recognition, cancellation and optional local loading |
| LatinIME superclass / changeSubtype | Forward Android selection and synchronize FUTO switches |
| PointerTracker.startLongPressTimer | Adjust spacebar timing before the validated timer conversion |
| InputLogic.onCodeInput | Resume gesture text before deletion and adjust its hold-mode read |

The Kotlin patch definitions contain the concrete signatures and field guards.
Recorder loading and action-bar results use semantic references rather than
fixed instruction offsets. Parameter register ranges avoid scratch-register
assumptions in the large input handler.

The resource helper checks the real IME service, BIND_INPUT_METHOD permission and
existing metadata XML. Subtype and spell changes edit the same document instead
of replacing it. The remote patch adds INTERNET and excludes private enhancement
preferences from backup.

There is no frozen method-hash catalog. Inspect a new binary, verify its boundaries,
and add a focused regression when a structural match needs to change.
