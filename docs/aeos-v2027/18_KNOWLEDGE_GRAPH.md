# 18_KNOWLEDGE_GRAPH

## CORE DOMAIN ENTITIES & RELATIONSHIPS

- **SyncManifest**: The root JSON from an upstream (Edge/Hermes).
  - *Contains many* -> **CatalogEntry**
- **CatalogEntry**: Metadata for a model, skill, or workflow.
  - *May become* -> **LocalModel** (once downloaded/verified)
- **LocalModel**: Represents physical presence on disk.
  - *Has one* -> **InstallState** (Available, Installed, Unsupported, Failed)
  - *Has one* -> **EngineState** (Ready, Loading, Loaded, Failed)
- **DownloadJob**: A transient action bridging a CatalogEntry to a LocalModel.
  - *Produces* -> `.tmp` file
  - *Validates via* -> SHA-256 Checksum
  - *Resolves to* -> `.bin` or `.gguf` file
- **RescanResult**: A transient action reconciling disk state with SQLite state.
  - *Mutates* -> LocalModel (creates new or updates InstallState)

## OWNERSHIP BOUNDARIES
- **UI Layer**: Owns composition and display. Pure consumer.
- **ViewModel Layer**: Owns StateFlow construction and user intent handling.
- **SyncManager**: Owns manifest fetching and SQLite delta updates.
- **Edge/Hermes Adapters**: Own execution, RAM mapping, and native loads.
