# 06_SECURITY_MODEL

## ISOLATION & STORAGE
To achieve a Zero-Trust local architecture, the app relies strictly on the Android OS sandbox.

- **App-Specific Internal Storage**: All models, skills, and patch histories are stored in `Context.getFilesDir()`. 
  - `files/models/`
  - `files/skills/`
  - `files/projects/`
- No use of external storage (`getExternalFilesDir()`) to prevent tampering from other apps or ADB access without `run-as`.

## ATOMIC FILE OPERATIONS
To prevent silent corruption during model downloads or code patch applications:
1. Data is streamed to `[target-filename].tmp`.
2. After the stream closes, the system computes the SHA-256 hash of the `.tmp` file.
3. If the hash matches the manifest, `renameTo()` is called to atomically swap the `.tmp` file to the final `[target-filename].bin`/`.gguf`.
4. If the hash fails, the `.tmp` file is deleted, and a `DownloadError(ChecksumMismatch)` is emitted.

## INFERENCE ENGINE ZERO-TRUST
- The local inference engine must be built without telemetry compilation flags.
- It operates entirely in-memory once loaded.
- Memory constraints are validated *before* allocation to prevent hard OOM crashes.

## ROLLBACK CACHE
If a Sync Update breaks functionality or violates schema validations, the system retains the last known-good manifest and SQLite snapshot.
- The Rollback UI exposes a "Revert to Last Stable" button that deletes current unverified binaries and restores the previous SQL manifest.
