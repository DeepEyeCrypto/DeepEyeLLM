# 16_RELEASE_RING_PLAN

## RING 1: INTERNAL (Automated + ADB)
- **Target**: Local developer devices.
- **Gates**:
  - Code compiles cleanly.
  - Unit tests pass.
  - ADB Instrumentation tests pass (download -> verify -> load).
  - Rescan flow tested manually via ADB injection.

## RING 2: BETA (Opt-In Testing)
- **Target**: Trusted testers running physical devices with varying RAM constraints.
- **Gates**:
  - App successfully syncs Hermes and Edge manifests.
  - Models load without native crashes.
  - Rollback UI successfully reverts a bad manifest sync.

## RING 3: PRODUCTION
- **Target**: General offline-first users.
- **Gates**:
  - Zero cloud dependencies verified via network profiler.
  - No silent failures reported in telemetry (telemetry is disabled, validation relies on explicit user error reports).

## ROLLBACK POLICY
- If a Production release breaks the manifest schema or local inference, the user must be able to tap "Revert to Previous Manifest" in the Updates tab to restore the prior SQLite state and resume offline inference immediately.
