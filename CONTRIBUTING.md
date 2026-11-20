# Contributing

This is a small, AI-assisted hobby project. Human review and a precise reproduction
are more useful than a large speculative rewrite.

Use the Docker workflow in the README. Run `make test`; when changing injection
points, also run `make integration` against an original APK you are allowed to use.
Report the FUTO flavor/version, Android version, selected patches and exact steps.
Never attach API keys, private editor text or recorded dictation.

Keep patches independently selectable. Match actual optimized DEX and fail clearly
on missing/ambiguous structures. Preserve original package identifiers, input
behavior and user preferences. A runtime option should be reversible where useful.

Code, comments and documentation are English. Log and error messages start with
a capital letter. Avoid credentials or user audio in logs. Document the scope of
validation; a mock server or synthetic version fixture is not an on-device or
real-release compatibility claim.

Do not commit APKs, tool JARs, upstream source snapshots, decompilations, signing
keys or credentials. Build output belongs in `build/`; private inputs and signing
state belong in ignored `.local/`.

Contributions to the original patch code are made under the [WTFPL v2](WTFPL).
Upstream projects retain their own licenses and attribution requirements.
