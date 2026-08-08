# 08_PERFORMANCE_PLAN

## THREAD ISOLATION
The app must aggressively isolate heavy workloads from the Main (UI) thread to maintain 60/120fps responsiveness.
- **Main Thread**: Pure UI rendering and Flow collection. No disk I/O, no JSON parsing, no hash computation.
- **I/O Dispatcher**: SQLite queries, manifest parsing, `.tmp` file streaming.
- **Default Dispatcher**: SHA-256 hash computation, diff generation.

## MANIFEST POLLING
Manifest updates from Google AI Edge and Hermes should be fast:
- Use `ETag` or `If-Modified-Since` headers when fetching manifest releases to prevent redundant JSON parsing.
- Apply manifest diffs to SQLite in bulk transactions.

## ENGINE LOAD LATENCY
- Do not block the UI while the local model loads into RAM.
- Emit a `Loading` state immediately.
- Pre-validate RAM requirements against `ActivityManager.MemoryInfo` before attempting to load a model to avoid native `std::bad_alloc` crashes.

## ZERO-LATENCY INPUT
For the Vibe Coding / IDE interface:
- Keystrokes in the code editor must bypass heavy Compose state updates where possible, utilizing standard `EditText` or highly optimized Canvas text layouts to maintain sub-millisecond response times.
