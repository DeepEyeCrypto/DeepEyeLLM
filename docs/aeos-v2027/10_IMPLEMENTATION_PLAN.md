# 10_IMPLEMENTATION_PLAN

## PHASE 1: FOUNDATION & MANIFEST (Weeks 1-2)
1. Setup Android project with App-specific internal storage directories.
2. Implement SQLite/Room database for `CatalogEntry` and `LocalModel`.
3. Build the `SyncManager` to poll Google AI Edge Gallery and Hermes GitHub releases.
4. Implement atomic manifest parsing and validation.

## PHASE 2: MODEL MANAGER (Weeks 3-4)
1. Implement the Edge Adapter for downloading models to `.tmp`.
2. Implement SHA-256 verification and atomic rename.
3. Build the Jetpack Compose Model Manager UI (4 state buckets).
4. Implement the Rescan logic to detect unmanaged `.bin` files.

## PHASE 3: HERMES & ROO CODE (Weeks 5-6)
1. Integrate the Hermes Memory Adapter (SQLite-backed context).
2. Integrate the Roo Code Patch Adapter (Diff generator and applier).
3. Build the IDE / Vibe Coding UI.

## PHASE 4: ADB & AUTOMATION (Week 7)
1. Implement Instrumentation tests for the full download -> verify -> load flow.
2. Expose internal test hooks for ADB control.

## PHASE 5: AUDIT & RELEASE (Week 8)
1. Execute final Chaos validation.
2. Roll out to Ring 1 (Internal devices).
