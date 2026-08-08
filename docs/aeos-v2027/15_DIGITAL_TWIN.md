# 15_DIGITAL_TWIN

## SIMULATED ENVIRONMENTS FOR TDD
To guarantee local reliability, the app must be validated against these specific digital twin scenarios on physical or virtual devices before any release.

### Twin A: The Clean Slate
- **State**: App installed, 0 bytes in `files/models/`, SQLite empty.
- **Validation**: Ensure empty states render correctly, "Available" models show Download buttons.

### Twin B: The Ghost Environment
- **State**: Valid `.bin` and `.gguf` files exist in `files/models/` but SQLite is empty (simulating an ADB push or corrupted DB).
- **Validation**: Triggering Rescan maps the files to the manifest, verifies hashes, and updates SQLite to `Installed` without re-downloading.

### Twin C: The Corrupt Download
- **State**: `.tmp` file exists but hash matches nothing in the manifest.
- **Validation**: System detects corruption, deletes `.tmp`, logs error, UI shows `Failed`.

### Twin D: Low Memory Constraint
- **State**: Device has 3GB RAM available. User attempts to load an 8GB model.
- **Validation**: Edge Adapter proactively blocks the load. Engine state becomes `Failed(OOM Prevented)`.
