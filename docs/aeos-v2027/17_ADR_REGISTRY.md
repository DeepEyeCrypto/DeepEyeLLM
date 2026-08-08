# 17_ADR_REGISTRY

## ADR-001: App-Specific Internal Storage Only
**Decision**: All models, skills, and code diffs will reside in `Context.getFilesDir()`.
**Reason**: Prevents tampering from external apps, adheres to Zero-Trust policies, and ensures files are wiped on app uninstall.

## ADR-002: Upstream Independence
**Decision**: Google AI Edge Gallery and Hermes Agent codebases will not be hard-forked into the main tree.
**Reason**: Allows both upstreams to evolve independently. The App Shell syncs validated JSON manifests instead of code.

## ADR-003: Mandatory Checksum Verification
**Decision**: No downloaded model or skill can transition to `Installed` without SHA-256 verification against the manifest.
**Reason**: Prevents execution of corrupted binaries which lead to opaque native crashes.

## ADR-004: UI Bound Strictly to Engine State
**Decision**: The Chat UI header must bind to `EngineStateFlow` emitted by the Edge Adapter, not the `LocalModel` metadata.
**Reason**: Ensures the UI never lies about what model is actively loaded in RAM.
