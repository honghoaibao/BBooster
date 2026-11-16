# BBooster Session Continuity

Updated: 2026-11-16

## Repository baseline

- Repository: honghoaibao/BBooster
- Main branch baseline: c795ab03c878682ed41ec9ea4060b3b83f80761a
- Feature branch: feature/phase-9-1-game-library
- Package: com.honghoaibao.bbooster
- Kotlin + Jetpack Compose + Material 3
- minSdk 26, targetSdk / compileSdk 36, Java target 17

## Open pull request stack

These security/test pull requests were still open when this feature branch was created:

1. PR #1 fix/monitoring-controller-test-deterministic based on main
2. PR #2 security/phase-8-7-1-backup-redaction based on PR #1
3. PR #3 security/phase-8-7-2-userservice-hardening based on PR #2

Do not merge or retarget these pull requests without explicit approval. The Game Library work is isolated on its own branch from the recorded main baseline.

## Phase 9.1 — Game Library

Implemented on this branch:

- Local Game Library models and validation rules.
- PackageManager discovery of ACTION_MAIN + CATEGORY_LAUNCHER activities.
- Narrow package visibility declaration in the manifest; no QUERY_ALL_PACKAGES permission.
- Private SharedPreferences storage of selected package identifiers.
- Repository state for installed and missing saved applications.
- Add, remove, refresh, search, and launch UI in Vietnamese and English.
- Android 13+ launch through getLaunchIntentSenderForPackage; earlier versions use getLaunchIntentForPackage.
- Unit tests for add/remove flows, duplicates, filtering, refresh, and missing applications.

## Architecture decisions

- Game discovery, storage, repository rules, and launch handling are isolated under app/src/main/java/com/honghoaibao/bbooster/core/game.
- Game Library UI is under app/src/main/java/com/honghoaibao/bbooster/feature/games.
- Only package identifiers are persisted. App labels and icons are resolved from Android while installed.
- BBooster itself is excluded from the selectable application list.
- No Shizuku, root, shell, force-stop, process-kill, thermal override, or device-setting mutation is used by Game Library.
- No new runtime permission or broad package visibility permission is added.
- Existing CommandPolicy, CommandRegistry, transport, monitoring, and diagnostics behavior must remain unchanged.

## Verification status

- Source and resource changes are prepared for a single feature-branch commit.
- GitHub Actions have not been run.
- No physical-device test has been performed.
- Before release: run testDebugUnitTest, lint, and assembleDebug; then manually verify discovery, add/remove, persistence after process restart, missing-app behavior, app launch, and both locales on Android 13 and a newer Android version.
- Do not report CI or device verification as successful until those checks are actually completed.

## Next step

Review the feature branch diff and compile/test findings. Fix any issues before marking a pull request ready for review. Do not merge without explicit approval.
