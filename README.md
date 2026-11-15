# BBooster

Native Android Game Booster built with Kotlin and Jetpack Compose.

## Project status

- Phase 0: repository and build-tool baseline selected.
- Phase 1: Android foundation and manual-only GitHub Actions CI.
- Phase 2: Shizuku Binder detection and permission flow.
- Phase 3: Shizuku UserService bridge, read-only diagnostic command engine, execution timeouts, and Vietnamese/English localization.
- Phase 4.1: process CPU telemetry and Android thermal status.
- Phase 4.2: foreground-only device monitoring at 5-second intervals, with up to 60 in-memory samples.
- Phase 4.3: Dashboard telemetry, CPU trend display, and TXT/JSON report history.
- Phase 5: Dashboard information hierarchy, roadmap presentation, and compact developer tools.
- Phase 6: read-only Diagnostics Hub for app runtime, device snapshot, Shizuku/transport state, monitoring history, and log health.
- Phase 7.1: read-only Optimization Engine core with deterministic thermal, memory, storage, and battery findings plus unit-test coverage.
- Phase 7.2: bilingual Optimization Engine screen with priority labels, explanations, and read-only suggested steps.
- Upcoming: further optimization analysis and release hardening; any future device-changing action requires separate explicit approval and recovery design.

## Technical baseline

| Component | Version / setting |
|---|---|
| Android Gradle Plugin | 8.13.2 |
| Gradle | 8.13 |
| Kotlin | 2.3.21 |
| Compose BOM | 2026.06.01 |
| JDK | 17 |
| compileSdk / targetSdk | 36 |
| minSdk | 26 (Android 8.0) |
| Application ID | `com.honghoaibao.bbooster` |
| Shizuku API / Provider | 13.1.5 |
| AppCompat | 1.7.1 |

## Build with GitHub Actions

1. Open the **Actions** tab.
2. Select **Android CI**.
3. Choose **Run workflow** on the `main` branch.
4. Download the `BBooster-debug-apk` artifact after the run succeeds.

The workflow is manual-only during early development to avoid unnecessary runs.

## Languages

BBooster includes English (default resources) and Vietnamese resources. Users can switch language inside the app. On Android 13 and later, Android's per-app language setting is generated from the supported resource locales. AppCompat stores the app language choice on older Android versions.

## Shizuku setup

1. Install Shizuku and start its service using the setup method supported by the device.
2. Open BBooster and refresh the connection status.
3. Grant BBooster permission from the Shizuku prompt.
4. Confirm the Dashboard reports **Shizuku Ready**.

On non-rooted devices, Shizuku uses the ADB/shell identity. The service may need to be started again after a device reboot.

## Phase 3 command safety

- The UserService runs in a separate Shizuku process.
- The command bridge accepts fixed action IDs only; arbitrary command strings and shell fragments are rejected.
- Current actions are read-only: identify the service UID and read Android release/API properties.
- Every process has a bounded timeout and returns exit code, stdout, stderr, and timeout status.
- This phase does not force-stop, suspend, restrict, or modify other applications.
- This phase does not change device settings.
- Future write operations require their own explicit action definitions, validation, and recovery design.
