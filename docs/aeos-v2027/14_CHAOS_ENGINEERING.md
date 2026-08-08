# 14_CHAOS_ENGINEERING

## SIMULATING FAILURES
To ensure the Zero-Trust and Offline-First promises hold, the architecture must survive deliberate sabotage.

### Scenario 1: Partial / Corrupt Download
- **Action**: Inject an invalid `.tmp` file or corrupt a finished `.bin` file via ADB.
- **Expected**: Checksum verification fails. The file is deleted. UI state transitions to `Failed(Checksum Mismatch)`.

### Scenario 2: Ghost Files
- **Action**: Use ADB `run-as` to manually push a valid model file into `files/models/` without updating SQLite.
- **Expected**: The Rescan action detects the file, hashes it, matches it to a CatalogEntry, and updates the SQLite state to `Installed`.

### Scenario 3: Engine OOM
- **Action**: Attempt to load a 7B parameter model on a device with 4GB RAM.
- **Expected**: Edge Adapter intercepts the request, compares `ActivityManager.MemoryInfo` to model size, and rejects the load. UI shows `Failed(Insufficient RAM)`.

### Scenario 4: Process Death
- **Action**: Terminate the app via ADB during a download.
- **Expected**: Upon restart, the app detects an orphaned `.tmp` file, deletes it, and resets the SQLite state to `Available`.
