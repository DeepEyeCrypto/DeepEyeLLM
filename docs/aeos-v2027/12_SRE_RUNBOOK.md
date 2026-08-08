# 12_SRE_RUNBOOK

## INCIDENT RESPONSE FLOW

If the app exhibits unexpected behavior (e.g., models failing to load, IDE patches failing), SRE / QA should use standard ADB commands to diagnose.

### 1. Check Live Logs
Filter logs for explicit domain tags:
```bash
adb logcat -s DeepEyeSync DeepEyeEngine DeepEyePatch
```

### 2. Verify Storage Integrity
Check if the models exist in internal storage and inspect sizes:
```bash
adb shell run-as com.deepeye.agent ls -la files/models/
```

### 3. Force Rescan
If SQLite state and disk state desync:
```bash
adb shell am start -n com.deepeye.agent/.MainActivity --ez "FORCE_RESCAN" true
```

### 4. Trigger Rollback
If a new upstream manifest corrupts the catalog:
```bash
adb shell am broadcast -a com.deepeye.agent.ACTION_ROLLBACK
```

## MONITORING
Since the app is offline-first and zero-trust, there is no remote telemetry.
- The app must maintain a local rolling log file (`files/logs/system.log`).
- The Diagnostics UI tab must parse and display this file with syntax highlighting for error levels.
