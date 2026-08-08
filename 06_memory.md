# DeepEyeLLM Architectural Memory (State: Post-Ultimate Refactor)

## Module 1: Download Pipeline (WorkManager)
- **Engine**: Replaced legacy HTTP code with `ModelDownloadWorker` utilizing `OkHttp` + `WorkManager`.
- **Resilience**: Supports chunked downloading with `Range` header resume. Automatically handles transient network errors (e.g. `IOException`, `SocketTimeoutException`) with an exponential backoff (`Result.retry()`).
- **Telemetry**: Tracks and broadcasts download progress and `bytesPerSec` speed up to `ModelCatalogViewModel`.
- **Data Structures**: Created `DownloadState.kt` representing type-safe states: `Idle`, `Downloading`, `Paused`, `Error`, `Verifying`, and `Completed`.

## Module 2: Model Catalog (Remote JSON)
- **Source**: Removed hardcoded `OpenSourceModels.kt` fallback URLs. Fully relying on remote JSON catalog fetched by `ModelRepository`.
- **Resilience**: Added robust `try/catch` wrapper around JSON parsing to prevent silent failures on malformed JSON payload.
- **Security**: Hardened GGUF filename sanitization against path traversal attacks.
- **Caching**: Introduced 24-hour cache expiry in `loadCachedCatalog()` to automatically refresh local catalog records.

## Module 3: Advanced Settings (Engine Wiring)
- **DataStore**: Maintains hardware acceleration, threads, context limit, and decoding configuration (`SettingsDataStore.kt`).
- **Engine Controller (`EngineController.kt`)**: Now properly injected with `SettingsDataStore`.
- **Initialization**: Dynamically retrieves the latest DataStore config via `engineSettingsFlow.first()` and applies settings (`useGpu`, `gpuLayers`, `customThreads`, `customContextSize`) directly to `LlamaCppEngine` upon instantiation (`reinitializeWithModel()`).

## Module 4: UI Performance (Jetpack Compose)
- **State Management**: Stable keys (`key = { it.id }`) have been bound to all `LazyColumn` instances within `ChatScreen.kt` and `ModelManagerScreen.kt`, significantly optimizing recomposition and preventing jitter/stutters on state mutations.
- **Glassmorphism**: Retained alpha-based composited surfaces in `GlassCard.kt` since hardware-accelerated RenderEffect/blur was determined unnecessary and potentially performance degrading on lower-end devices.

## DI/Hilt Configuration
- **EngineModule**: Cleaned up obsolete `ModelDownloader` provides. Injects `SettingsDataStore` into `EngineController`.
