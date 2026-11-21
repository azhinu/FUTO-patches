# RUTO Russian native crash: diagnosis and repair

Investigated on 2026-11-21, Pixel 8, Android 17 / API 37. The target is the RUTO
download labeled 1.0.2, internally 1.0.1-ru-unstable / 101. This issue is separate
from the six Morphe enhancements.

## Result

The inspected GGUF advertises `xbu_char_autocorrect_v1` but omits
`char_embed_mixing_v1`. RUTO constructs embedding mixes for Cyrillic characters,
but its loader populates the float embedding buffer only when the latter flag
is present. Correction then dereferences the empty buffer.

The original FUTO Cyrillic filtering skips this mixing path. Its lack of a crash
with the same model is therefore consistent with the bug, rather than evidence
that RUTO's native path can safely use the model unchanged.

The remedy tested here adds the missing flag to a separate model copy and imports
it through RUTO's normal model importer. It does not change tokenizer data or any
tensor weights. No Android framework, native APK library or Morphe hook change
is involved in this remedy. The original downloaded model remains unchanged.

## Evidence

| Case | Synthetic input | Observed result |
| --- | --- | --- |
| Patched RUTO, original model | `П` | No native crash |
| Patched RUTO, original model | `Пр` | SIGSEGV in DecodePromptAndMixes |
| RUTO without Morphe patches, original model | `Пр` | Same native SIGSEGV |
| Installed FUTO Play Store, original model | `Пр` | Correct text, process unchanged |
| Patched RUTO, repaired model | `Пр` | Correct text, process unchanged |
| Patched RUTO, repaired model | `Привет` | Correct text, process unchanged |
| Patched RUTO after cold start, repaired model | `Привет` | Correct text, process unchanged |

The unpatched control APK was re-signed with the same local test key to allow
installation over the patched build while retaining app data. Every non-signature
ZIP entry was compared with the original download and was identical. The patched
APK was restored after that control. No application uninstall or data clear was used.

The RUTO tombstone identifies `libjni_latinime.so`, Build ID
`0aee737c5f8e33ee9e2a7b3f92f88fda4667d943`, relative PC `0x16b44c`:

```text
LanguageModelState::DecodePromptAndMixes(...) + 1904
ldp q3, q4, [x10, #-0x10]
x10 = 0x70010
fault address = 0x70000
```

Disassembly shows the load inside the loop combining float token embeddings.
The fault address equals `224 * 512 * sizeof(float)`: a character token's offset
from a null buffer. The model's embedding tensor is present, FP16, shape
`[512, 24000]`; the failure is the skipped population of the runtime buffer.

Relevant source boundaries:

- [Loader](https://github.com/MisterFenek/ruto-keyboard/blob/master/app/native/jni/src/ggml/LanguageModel.cpp):
  `createLanguageModel()` loads `embeddings` under `HasFeature(FEATURE_EMBED_MIXING)`.
- [Feature constant](https://github.com/MisterFenek/ruto-keyboard/blob/master/app/native/jni/src/ggml/LanguageModel.h):
  `FEATURE_EMBED_MIXING` is `char_embed_mixing_v1`.
- [RUTO JNI prediction](https://github.com/MisterFenek/ruto-keyboard/blob/master/app/native/jni/org_futo_inputmethod_latin_xlm_LanguageModel.cpp):
  Unicode `CHAR_TO_TOKEN` mixing reaches `PredictCorrection()` and
  `DecodePromptAndMixes()`, which reads `embeddings.data()` without a size guard.
- [FUTO 0.1.30 JNI prediction](https://github.com/futo-org/android-keyboard/blob/0.1.30/native/jni/org_futo_inputmethod_latin_xlm_LanguageModel.cpp):
  the old character filter accepts ASCII Latin letters/digits and skips Cyrillic.
- [RUTO exporter](https://github.com/MisterFenek/ruto-keyboard/blob/master/tools/export_to_futo_gguf.py):
  writes only `xbu_char_autocorrect_v1` to `keyboardlm.features`.

## Reproduce the repair

The utility is deliberately restricted to this exact inspected model:

```text
Original model SHA-256:
f0d3766d918e42d1eff29c81e3617737b9ed59400c94c0b7c2bfb93e00a1efac

Repaired model SHA-256:
aabc83c5215df48ecdcd4c22b1e5c03f26e6d93eb788f0bb1bc8ad42d3cabcda

Unchanged tensor payload SHA-256:
83850e3df5ba66f7abf0f24e4ea58499dbb782049c530503567900ff5028c973
```

```sh
python3 scripts/repair-ruto-model.py \
  /path/to/futo_ru_65m_v2_Q8_0.gguf \
  /path/to/futo_ru_65m_v2_Q8_0-mixing.gguf
```

The tool rejects a different model or an existing output. It rewrites the feature
string and GGUF alignment padding, then copies the entire tensor payload unchanged.
The source model is never overwritten. The generated file is 82,589,984 bytes.

Copy the repaired file to the phone. In RUTO, open Languages & Models → Russian
Transformer Model → Import from file, choose the repaired GGUF and assign Russian.
On the tested phone this has already been done. The external original remains
in Downloads; the repaired copy is `futo_ru_65m_v2_Q8_0-mixing.gguf` there.

The repair utility was run against the original and matched the exact file imported
on the phone. Rejection of an unrelated input without creating an output was also
checked, as was refusal to overwrite an existing output. The utility is not a
generic repair for arbitrary GGUF files.

## Device regression

Build/install the synthetic editor as described in [Testing](testing.md). Select
the Gboard Russian ЙЦУКЕН v3 layout for the target app, then run:

```sh
python3 scripts/test-ruto-native.py --serial YOUR_ADB_SERIAL --letters 2
python3 scripts/test-ruto-native.py --serial YOUR_ADB_SERIAL --letters 6 --cold-start
```

The replay is specific to the observed 1080x2400 Pixel keyboard. It selects RUTO,
clears only the synthetic editor, injects the recorded touches, and requires
the expected text, an unchanged PID and no DecodePromptAndMixes tombstone.
Wrong layouts or missed touches fail the text assertion. Use
`--package org.futo.inputmethod.latin.playstore` for the installed FUTO control.

## Limits and upstream prevention

This proves removal of this SIGSEGV on the tested path. It does not certify
Russian suggestion accuracy, autocorrection quality, all gesture paths or every
other model. Unexpected Latin prediction candidates were observed with this model;
the metadata repair is not a model-training fix.

Loading the float embedding cache uses approximately 46.9 MiB for 512 × 24000
floats. Upstream should make its exporter declare the features required by the
runtime, ensure the loader initializes all buffers the prediction path needs,
and reject or safely handle absent/wrong-sized embedding buffers before reading
them. Silently relying on the old Cyrillic filter is not a runtime safety check.
