# OmniNumeris

OmniNumeris is a private, offline workspace for Android phones and tablets. The internal Android build currently provides the launch screen and project foundation.

## Build and run

Use JDK 21, Android SDK Platform 36 and Build Tools 35.0.0. Set `JAVA_HOME` and `ANDROID_HOME`, or open the root directory in Android Studio and select a JDK 21 Gradle JVM. The first build needs access to the Gradle and Maven repositories.

```powershell
.\gradlew.bat :check :app:assembleDebug
```

On macOS or Linux, run `bash ./gradlew :check :app:assembleDebug`. The debug APK lives at `app/build/outputs/apk/debug/app-debug.apk`. Run the `app` configuration in Android Studio, or install the APK with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

The minimum Android version is API 29 (Android 10). Release builds package ARM64 native libraries. Debug builds also package x86-64 libraries for Android emulators.

Open **Canvas prototype** from the debug workspace to test pen or finger ink, two-finger pan and zoom, and stroke recovery.

`:check` verifies the module graph and runs the JVM checks, Android debug unit tests, and lint. The [Android CI workflow](.github/workflows/android-ci.yml) runs the same checks and assembles the debug APK on pull requests.

## Module ownership

- `:app` owns the Compose UI, ViewModels, and Hilt wiring.
- `:application` owns Kotlin/JVM use cases and the local transaction contract.
- `:domain` owns pure Kotlin workspace contracts.
- `:infrastructure` owns Android, private storage, Room, and future native adapters.

See the [module diagram and dependency rules](docs/adr/0002-android-layer-boundaries.md).

## Project documents

- [Product requirements](01-SRS.md)
- [Architecture](02-Architecture.md) and [Android bootstrap decision](docs/adr/0001-android-bootstrap.md)
- [Data, security, and UX](03-Data-Security-UX.md)
- [Testing and roadmap](04-Testing-Roadmap.md)
- [Implementation backlog](05-Tickets.md)
