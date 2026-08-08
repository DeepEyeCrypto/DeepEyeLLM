# 02_PRD

## PRODUCT VISION
Build a unified local AI shell that integrates Google AI Edge Gallery models, Hermes Agent skills, and Roo Code IDE capabilities while maintaining a zero-trust, offline-first operating environment.

## CORE FEATURES

### 1. Model Manager (Powered by Google AI Edge)
- **Unified Catalog**: Display models fetched via manifest sync.
- **State Buckets**: Every model must fall into one of four mutually exclusive states:
  - `Installed`: Verified checksum, ready for load.
  - `Available`: Valid catalog entry, not downloaded.
  - `Unsupported`: Hardware or runtime incompatible (never hide these, show reason).
  - `Failed`: Corrupt download, checksum mismatch, or failed engine load.
- **Download Lifecycle**: Download to `.tmp` -> Checksum verification -> Atomic rename -> Mark Installed.
- **Rescan**: Action to detect "ghost files" in internal storage and map them to catalog entries without app restart.

### 2. Skill & Agent Manager (Powered by Hermes)
- **Skill Sync**: Import skills and workflows via upstream Hermes manifest updates.
- **Memory Management**: Local persistent storage for agent context across sessions.
- **Visibility**: Show Active, Deprecated, and Failed skills.

### 3. Vibe Coding IDE (Powered by Roo Code)
- **Workspace Explorer**: File explorer mapped to local app-specific project directories.
- **Generative Edits**: Prompt-to-code workflow utilizing local inference.
- **Diff & Patching**: Visual diff review before atomic patch application.
- **Local Execution**: Ability to trigger local tests (e.g., ADB shell commands) to validate code changes.

### 4. Sync & Updates Manager
- **Independent Tracking**: Track versions of Google AI Edge and Hermes separately.
- **Manifest Validation**: Reject updates that violate offline-only constraints or fail schema checks.
- **Rollback**: One-tap reversion to previous known-good snapshots.

## USER EXPERIENCE (UX)
- **Honest UI**: The chat header must show the *actual* active engine state (e.g., `Active: <ModelName> | State: Loading/Loaded/Failed`). Never claim a model is active unless the inference engine confirms it.
- **No Cloud Language**: Remove all terms suggesting cloud connectivity (e.g., "Downloading from Cloud").
- **Empty States**: If a list is empty, state exactly why and provide diagnostic buttons (Refresh, Rescan, Logs).

## PERFORMANCE GOALS
- **UI Responsiveness**: UI must not block during file scanning, checksum verification, or engine loading.
- **State Hoisting**: Avoid extra recompositions; Compose consumes state from ViewModels outliving the screen.
