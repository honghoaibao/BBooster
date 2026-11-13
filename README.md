# BBooster

Native Android Game Booster built with Kotlin and Jetpack Compose.

## Project status

- Phase 0: repository and build-tool baseline selected.
- Phase 1: Android foundation and manual GitHub Actions CI.
- Phase 2: Shizuku Binder detection, permission flow, and Dashboard status.
- Later phases: Shizuku UserService command bridge, device telemetry, optimization pipeline, recovery, overlay, and release hardening.

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

## Build with GitHub Actions

1. Open the **Actions** tab.
2. Select **Android CI**.
3. Choose **Run workflow** on the `main` branch.
4. Download the `BBooster-debug-apk` artifact after the run succeeds.

The workflow is manual-only during early development to avoid unnecessary runs.

## Shizuku setup

1. Install Shizuku and start its service using the setup method supported by the device.
2. Open BBooster and refresh the connection status.
3. Grant BBooster permission from the Shizuku prompt.
4. Confirm the Dashboard reports **Shizuku Ready** and displays the active privilege identity.

On non-rooted devices, Shizuku uses the ADB/shell identity. The service may need to be started again after a device reboot.

## Safety boundary for Phase 2

- This phase only detects the Shizuku Binder and handles the app permission request.
- It does not execute shell commands.
- It does not force-stop, suspend, restrict, or modify other applications.
- It does not change device settings.
- Command execution will be added separately through a Shizuku UserService rather than the deprecated `newProcess()` API.
