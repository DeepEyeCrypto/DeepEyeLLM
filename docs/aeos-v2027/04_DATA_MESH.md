# 04_DATA_MESH

## UNIFIED MANIFEST SCHEMA
The App Shell consumes updates from Google AI Edge and Hermes via a standardized JSON manifest. This ensures upstreams can evolve without breaking internal domain logic.

```json
{
  "source": "google-ai-edge-gallery | hermes-agent",
  "version": "1.2.0",
  "commit": "abc123def",
  "releaseTag": "v1.2.0",
  "checksum": "sha256-hash-here",
  "updatedAt": "2027-01-01T12:00:00Z",
  "entries": [
    {
      "id": "model_or_skill_id",
      "type": "model | skill | workflow",
      "name": "Display Name",
      "description": "Capability summary",
      "format": "tflite | gguf | json",
      "compatibility": ["android", "local-runtime"],
      "status": "available",
      "downloadUrl": "https://..."
    }
  ]
}
```

## INTERNAL DOMAIN ENTITIES
Mapped into local SQLite/Room storage:

### 1. CatalogEntry
Represents an upstream manifest entry. Pure metadata.
- `id`, `name`, `type`, `format`, `compatibility`, `checksum`

### 2. LocalModel
Extends a `CatalogEntry` with local presence data.
- `localFileName`, `sizeBytes`, `installState` (Installed, Available, Unsupported, Failed), `engineState` (Ready, Loading, Loaded, Failed).

### 3. DownloadJob
Tracks active staging.
- `id`, `targetModelId`, `tmpFilePath`, `bytesDownloaded`, `totalBytes`, `status` (Downloading, Verifying, Renaming, Failed).

### 4. RescanResult
Emitted after directory traversal to reconcile disk state with SQLite state.
- Lists of `foundGhosts`, `missingFiles`, `corruptedHashes`.

## SYNC INTELLIGENCE
Maintains the rollback ledger.
- `snapshotId`, `source`, `previousManifestHash`, `appliedAt`.
