# Changelog

## Unreleased

- Flatten the project into one source root.
- Remove downloaded APKs, upstream checkouts, decompilations and old build output.
- Download toolchain dependencies in Docker with verified checksums.
- Generate synthetic speech fixtures during the build.
- Add a portable Make/Compose workflow, offline CI and source-only release packaging.

## 0.3.0

- Add optional character backspace after swipe, with word deletion on hold.
- Add reversible stock/keyboard/custom spacebar timing and a native millisecond slider.
- Verify swipe suffix editing and timing-mode changes on Pixel.
- Expand the real Morphe matrix to fourteen cases.

## 0.2.0

- Move enhancement controls into native FUTO / Material 3 settings.
- Keep swipe replacement candidates visible in the shared suggestion row.
- Remove the version whitelist and whole-method hash gate.
- Add the initial spacebar-duration patch.

## 0.1.2

- Package the four original enhancements for standard APK installation.
- Add INTERNET and validate requested patch results before publishing an APK.
- Verify data-preserving installation and network permission registration.
