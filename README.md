# BBooster

Native Android Game Booster built with Kotlin and Jetpack Compose.

## Project status

- Phase 0: repository and build-tool baseline selected.
- Phase 1: Android skeleton and manual GitHub Actions CI.
- Later phases: Shizuku integration, command engine, device telemetry, optimization pipeline, recovery, overlay, and release hardening.

## Technical baseline

| Component | Version / setting |
|---|---|
| Android Gradle Plugin | 8.13.2 |
| Gradle | 8.13 |
| Kotlin | 2.3.21 |
| Compose BOM | 2026.09.00 |
| JDK | 17 |
| compileSdk / targetSdk | 36 |
| minSdk | 26 (Android 8.0) |
| Application ID | `com.honghoaibao.bbooster` |

The project intentionally starts without Shizuku commands or elevated permissions. Those are introduced in later phases with explicit safety checks and recovery.

## Build with GitHub Actions

1. Open the **Actions** tab.
2. Select **Android CI**.
3. Choose **Run workflow** on the `main` branch.
4. Download the `BBooster-debug-apk` artifact after the run succeeds.

The workflow is manual-only during early development to avoid unnecessary runs.

## Local build

The CI workflow provisions Gradle 8.13 and JDK 17. Run the following commands in an environment with Android SDK Platform 36 installed:

```bash
gradle testDebugUnitTest lintDebug assembleDebug
```

## Safety and implementation notes

- No root access is required or assumed.
- Shizuku integration is not present in Phase 1.
- The app does not force-stop, suspend, or modify other apps in this phase.
- Future system changes must be tracked and restored to their original values.
