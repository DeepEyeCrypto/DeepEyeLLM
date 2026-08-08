# 11_TEST_STRATEGY

## INSTRUMENTATION TESTS (ADB-Driven)
The core source of truth for app reliability is Android Instrumentation Tests running on physical hardware.

**Key Scenarios:**
1. `testCatalogSyncValidatesSchema()`
2. `testModelDownloadAppliesAtomicRename()`
3. `testModelDownloadRejectsBadChecksum()`
4. `testRescanDetectsGhostFiles()`
5. `testEngineLoadUpdatesActiveState()`

**Execution:**
Tests must be runnable via ADB in a CI pipeline:
```bash
adb shell am instrument -w -e class com.deepeye.agent.ModelManagerTest com.deepeye.agent.test/androidx.test.runner.AndroidJUnitRunner
```

## UNIT TESTS
- Run strictly on JVM (no device required).
- Target: ViewModels, Manifest Parsers, SHA-256 calculators, State reducers.

## UI / COMPOSABLE TESTS
- Isolate Composables to verify they correctly display the 4 states (Installed, Available, Unsupported, Failed) when provided dummy `StateFlow` data.
- Ensure "Unsupported" entries render the reason string.
