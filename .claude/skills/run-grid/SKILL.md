---
name: run-grid
description: Build, test, lint, and sign the Grid Android app (com.defnf.grid) on a headless Linux machine. Use when asked to build Grid, run a Grid build, produce a debug/release APK or AAB, sign the app, run lint/tests, or verify the Android build works. Enforces zero-tolerance for compiler/KSP/lint warnings.
---

# Run Grid (build · test · sign)

Grid is an Android (Kotlin + Jetpack Compose) app. There is **no GUI/emulator path on
this machine** — `/dev/kvm` is absent, so the Android emulator cannot boot (x86 images
require KVM; software emulation hangs). What you "run" here is the **build / test / sign
pipeline**, and that is what this skill drives.

All paths below are relative to the repo root (`/primary/dev/grid`). The driver lives at
`.claude/skills/run-grid/driver.sh`.

## Primary path — the driver

```bash
.claude/skills/run-grid/driver.sh all       # clean + debug + tests + lint + signed release APK & AAB
.claude/skills/run-grid/driver.sh debug      # fast inner loop: assembleDebug only
.claude/skills/run-grid/driver.sh lint       # the zero-tolerance gate (lintDebug)
.claude/skills/run-grid/driver.sh release    # signed release .apk + .aab for Play
```

The driver sets `ANDROID_HOME`, runs Gradle, **fails on any compiler/KSP `w:` warning**,
and verifies the release signature with `apksigner`. Artifacts land under
`app/build/outputs/` (`apk/debug/`, `apk/release/`, `bundle/release/`).

## Zero tolerance for warnings (enforced)

Per project policy, **any warning fails the build** — they must be fixed, never ignored:
- `app/build.gradle.kts` → `kotlinOptions { allWarningsAsErrors = true }` (Kotlin compiler).
- `app/build.gradle.kts` → `lint { warningsAsErrors = true; abortOnError = true; checkReleaseBuilds = true }`.
- The driver also greps build output for stray `w:` lines as a backstop.
- **Excluded by design** (documented in the `lint {}` block): the informational
  "newer version available" advisories (`NewerVersionAvailable`, `GradleDependency`,
  `AndroidGradlePluginVersion`, `OldTargetApi`) and the third-party-jar
  `TrustAllX509TrustManager`. These are not defects and re-fire on every dependency
  release; dependencies are bumped out-of-band, not via the gate. Do **not** add other
  `disable` entries to silence a real warning — fix the code instead.

## Prerequisites (one-time, already done on this box)

JDK 17 and the Gradle 8.13 wrapper are present. The Android SDK lives at `~/Android/Sdk`.
If recreating it on a clean machine:

```bash
# command-line tools → ~/Android/Sdk/cmdline-tools/latest/
export ANDROID_HOME="$HOME/Android/Sdk"
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

Then point Gradle at it (gitignored, machine-specific):

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
```

## Signing

Release signing is wired in `app/build.gradle.kts` and reads `keystore.properties` at the
repo root (gitignored). It is the **upload key** for Play App Signing. If
`keystore.properties` is absent, release builds come out **unsigned** (gated on
`hasReleaseKeystore`) — the build still succeeds. `keystore.properties` format:

```
storeFile=/primary/dev/android-keystores/upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

Verify a built release APK directly if needed:

```bash
~/Android/Sdk/build-tools/35.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

## Tests

`driver.sh test` runs `:app:testDebugUnitTest`. Note: the project currently has **no unit
tests** (`app/src/test` is empty), so this passes trivially. Test deps (JUnit, MockK,
Turbine, coroutines-test) are configured for when tests are added.

## Gotchas (things that actually bit, in this container)

- **No emulator.** `emulator -accel-check` reports `/dev/kvm is not found`. Don't try to
  boot an AVD — it won't. Verify via build + lint + (optionally) a real device over `adb`.
- **`java.nio.file` needs the NIO desugar variant.** `SftpClient` uses `Paths.get` /
  `FileTime.toMillis` (API 26+) while `minSdk = 24`. The fix is
  `coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio")` (the plain
  `desugar_jdk_libs` does NOT cover `java.nio.file` — lint still flags NewApi). It is
  enabled via `compileOptions { isCoreLibraryDesugaringEnabled = true }`.
- **Media3 opt-in uses `androidx.annotation.OptIn`, positionally.** Annotate with
  `@OptIn(UnstableApi::class)` — NOT `@OptIn(markerClass = UnstableApi::class)`; the
  named single-element vararg form is a Kotlin compile error.
- **Hilt/Dagger ≥ 2.59 requires AGP 9.** Stay on Hilt 2.54–2.5x while on AGP 8.13. Hilt
  2.53 emits 16 spurious KSP "No dependencies reported" warnings (Dagger #4526); 2.54 fixes
  them — do not downgrade below 2.54 or the zero-tolerance gate breaks.
- **`commons-compress` ≥ 1.26 deprecates `SevenZFile(File)`.** Use
  `SevenZFile.builder().setFile(f).get()` — the old constructor trips `allWarningsAsErrors`.
- **First build is slow (~7 min), incremental ~1 min.** The Gradle daemon, build cache,
  configuration cache, and KSP incremental are enabled in `gradle.properties` (earlier
  Windows-only workarounds that disabled them were removed).

## Human path

There isn't a meaningful one here — Grid is a phone app and this box is headless. To see it
actually run, install a built APK on a physical device:
`adb install app/build/outputs/apk/debug/app-debug.apk`.
