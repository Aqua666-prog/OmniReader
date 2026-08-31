# Building OmniReader 0.1.0

## Requirements

- JDK 17.
- Android SDK Platform 37.
- Internet access on the first build for Gradle/AndroidX/decoder dependencies.
- Use the repository Gradle Wrapper; do not substitute a random system Gradle version.

AGP is pinned to 9.3.2 and the project wrapper is pinned to Gradle 9.5.0. AGP 9 built-in Kotlin is enabled; KGP/Compose Compiler are pinned to Kotlin 2.3.21 and Room code generation uses KSP 2.3.11.

## 1. Ensure the official Wrapper JAR exists

Normally a Gradle repository commits `gradle/wrapper/gradle-wrapper.jar`. The source-generation environment could not persist that binary, so this archive contains verified bootstrap scripts.

Linux / macOS / Termux:

```bash
chmod +x ./bootstrap-wrapper.sh ./gradlew
./bootstrap-wrapper.sh
```

Windows PowerShell:

```powershell
.\bootstrap-wrapper.ps1
```

The script downloads only:

`https://raw.githubusercontent.com/gradle/gradle/v9.5.0/gradle/wrapper/gradle-wrapper.jar`

and refuses to install it unless SHA-256 equals:

`497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`

If the JAR already exists with that checksum, the bootstrap script does nothing.

## 2. Point Gradle at Android SDK

Either export `ANDROID_HOME`/`ANDROID_SDK_ROOT`, or create uncommitted `local.properties`:

```properties
sdk.dir=/absolute/path/to/android-sdk
```

Install Android 17 / API 37 in SDK Manager. Depending on the installed command-line tools the platform package may appear as `platforms;android-37` or `platforms;android-37.0`; the included GitHub Actions workflow handles both forms. Install Build-Tools 37.0.0 (or a compatible newer 37.x.x) as well.

## 3. Run the build gate

From repository root:

```bash
./gradlew clean
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Debug APK:

`app/build/outputs/apk/debug/app-debug.apk`

If a native optional decoder is the only build blocker, isolate/disable that provider and dependency rather than rewriting the library/scanner architecture. In particular DJVU and TIFF live behind independent ReaderProviders.

## GitHub Actions

Push the repository to GitHub and use **Actions → Build OmniReader APK → Run workflow**. The workflow:

1. checks out the repository;
2. bootstraps/verifies the official Gradle 9.5.0 wrapper JAR if necessary;
3. installs JDK 17;
4. configures Gradle caching;
5. runs clean;
6. runs unit tests;
7. runs Android lint;
8. assembles debug APK;
9. uploads `OmniReader-0.1.0-debug-apk`.

## Dependency repositories

The project uses `google()` and Maven Central. JitPack is also present only because the TIFF wrapper currently has a transitive native TIFF artifact there. Reading remains completely offline at runtime.
