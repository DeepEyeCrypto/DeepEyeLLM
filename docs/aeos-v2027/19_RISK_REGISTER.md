# 19_RISK_REGISTER

### Risk 1: Manifest Schema Drift
- **Impact**: High. App fails to parse upstream updates.
- **Mitigation**: Strict JSON validation. If parsing fails, reject update, retain old snapshot, log error to Diagnostics UI.

### Risk 2: Native Engine Crash on Unsupported Model
- **Impact**: High. App process dies without error handling.
- **Mitigation**: Edge Adapter must pre-validate model format, RAM requirements, and device compatibility flags *before* passing the pointer to the C++ runtime.

### Risk 3: Ghost Files Accumulating
- **Impact**: Medium. Storage fills up without UI visibility.
- **Mitigation**: App launch triggers an asynchronous lightweight `DirectoryScanner`. Any unmapped files are surfaced in the Diagnostics tab under "Unmanaged Files" with a "Delete" or "Rescan" option.

### Risk 4: Agent Patch Loop
- **Impact**: Medium. Roo Code adapter repeatedly generates invalid diffs.
- **Mitigation**: Hard limit of 3 retry loops on diff application failures. User must manually intervene in the IDE tab.
