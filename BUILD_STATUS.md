# Build status — OmniReader 0.1.0 source release

## Android build status

**No APK is claimed from the generation container.**

The source tree is prepared as a conventional Android Gradle project, but the generation environment does not contain an Android SDK/AAPT2 installation and outbound DNS is unavailable to the local shell. Therefore the Android release gate could not be executed here. The user requested the completed source release and will assemble the APK on an Android-capable machine.

The required release gate remains:

```bash
./gradlew clean
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

A successful result of those commands is the authoritative Android build validation.

## Source validation performed in the generation environment

The following checks were executed successfully after the final source edits:

- POSIX syntax: `bash -n gradlew` and `sh -n bootstrap-wrapper.sh`;
- all Android XML resources/manifests parsed successfully (3 XML files);
- `.github/workflows/build-apk.yml` parsed successfully as YAML;
- required Android/Gradle project files are present;
- source/config scan found no `runBlocking`, `MANAGE_EXTERNAL_STORAGE`, `READ_EXTERNAL_STORAGE` or `WRITE_EXTERNAL_STORAGE`;
- AGP 9 migration scan found no legacy `org.jetbrains.kotlin.android`, `android.builtInKotlin=false`, `android.newDsl=false` or `kotlinOptions { ... }` configuration;
- pure-Kotlin smoke test passed for natural sorting, series/volume parsing and text encoding;
- exact-source smoke extraction passed for the RTF parser and PalmDOC decompression primitive;
- a bare `kotlinc` syntax pass over all production Kotlin sources reported no parser-level `expecting`, `unexpected tokens` or unclosed-source diagnostics. It necessarily reports unresolved Android/AndroidX references because no Android classpath is installed, so this is a syntax heuristic, not an Android compilation.

## Gradle / Android toolchain

- Android Gradle Plugin: 9.3.2
- Gradle distribution: 9.5.0
- JDK for builds/CI: 17
- compileSdk / targetSdk: 37
- built-in Kotlin (AGP 9), KGP/Compose Compiler: 2.3.21
- KSP: 2.3.11

The root `buildscript` declares its own Google/Maven Central/Plugin Portal repositories so the pinned KGP classpath is resolvable independently from `pluginManagement`.

## Gradle Wrapper

`gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.properties` are present and pinned to Gradle 9.5.0. The binary `gradle-wrapper.jar` could not be persisted directly by this generation environment, so the release contains verified bootstrap scripts for Linux/macOS/Termux and PowerShell. They fetch the official wrapper JAR from the Gradle v9.5.0 upstream tree and refuse to install it if SHA-256 does not match.

Published checksums used by the project:

- Gradle 9.5.0 binary distribution: `553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746`
- Gradle 9.5.0 wrapper JAR: `497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`

GitHub Actions runs the wrapper bootstrap automatically before invoking `./gradlew`.

## Test-runtime note

The project uses stable Robolectric 4.16.1. Robolectric tests are explicitly pinned to SDK 36 because that stable release does not provide an API 37 runtime; production remains `compileSdk = 37` / `targetSdk = 37`. This avoids pulling a beta Robolectric solely to execute local unit tests.

## Meaning of “release” in this archive

This ZIP is a **source release**, not a prevalidated APK release. It contains the complete project, reader providers, tests, CI workflow, build documentation and integrity manifest. After a successful Android release gate and device smoke test, the resulting APK can be treated as the validated 0.1.0 build.
