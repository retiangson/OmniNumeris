# ADR 0001 · Android bootstrap

**Status** Accepted, 2026-11-24  
**Ticket** OW-001  
**Traceability** NFR-08, with baseline coverage for FR-CUS-01 and NFR-06

The initial two-module layout below was extended by [ADR 0002](0002-android-layer-boundaries.md) when implementing the detailed KAN-27 criteria.

## Decision

Use a single Android application module (`:app`) and a Kotlin/JVM domain module (`:domain`). `:app` owns Compose and Android integration and can depend on `:domain`. `:domain` has no project dependencies, uses only the Kotlin standard library in production, and may use JUnit in tests. `:verifyModuleDependencies` checks the declared project dependencies and domain classpaths during `:check`. Revisit the allowlist when adding modules or domain libraries.

Set `minSdk` to 29 and use API 36 for compilation and targeting. Keep the Gradle runtime and toolchains on JDK 21 and emit JVM 17 bytecode. Compose brings a prebuilt AndroidX graphics library. Package its `arm64-v8a` binary for release and include `x86_64` in debug builds for emulator testing.

Choose the launch layout from the available window width after safe drawing insets. Use a stacked layout below 840 dp and a two-column layout at and above 840 dp. This lets the layout adapt to resizing, orientation, and Android split screen. Preserve scrolling for short windows and large text.

The bootstrap requests no internet permission and stores no workspace content. It disables automatic backup with `android:allowBackup` and excludes app files from cloud backup and device transfer in the Android 12+ extraction rules. Encrypted storage, app-managed backup, and protected indexes belong to separate tickets.

Use the pinned Gradle wrapper and GitHub Actions PR workflow for module rules, unit checks, Android lint, and debug APK assembly. The application ID `com.omninumeris.app` is a development identifier pending the product naming decision already recorded in the architecture.

## References

- [Android Gradle Plugin 8.13 compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [Android window size classes](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes)
- [Android backup and transfer extraction rules](https://developer.android.com/identity/data/autobackup)
- [Gradle distribution and wrapper checksums](https://gradle.org/release-checksums/)
