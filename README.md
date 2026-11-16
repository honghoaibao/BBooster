# BBooster

Game booster for **non-root** Android 13+ devices. Privileged commands run through
[Shizuku](https://github.com/RikkaApps/Shizuku); every command is whitelisted, time-limited and logged.

Status: **phase 1 (skeleton)**. Connection onboarding and the privileged executor work end to end in
design; the boost features (phase 2) are not implemented yet.

## Build

CI is the build gate: push to GitHub and the `Build` workflow runs
`./gradlew testDebugUnitTest assembleDebug`, then uploads the debug APK and test reports as an artifact.

Locally (JDK 17+, Android SDK with platform 37): `./gradlew testDebugUnitTest assembleDebug`.

## Use

1. Install Shizuku and start it (Developer options → Wireless debugging → pair, then "Start" in Shizuku).
2. Open BBooster → Set up connection → grant permission. Shizuku must be restarted after every reboot.

## Layout

| Package | Role |
|---|---|
| `shell` | `ShellCommand` whitelist, `PrivilegedExecutor`, Shizuku backend, `ShellService` (runs as shell UID) |
| `di` | Hilt bindings |
| `ui` | Compose theme, navigation, Home, Onboarding |

Version pins and their sources are documented at the top of `gradle/libs.versions.toml`.
