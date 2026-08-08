# 07_UI_UX_SYSTEM

## NAVIGATION SHELL
A bottom navigation bar or side rail separating domains:
- **Models** (Google AI Edge Gallery mappings)
- **Skills** (Hermes Agent mappings)
- **IDE** (Roo Code vibe coding)
- **Settings & Updates**
- **Diagnostics**

## STATE HOISTING AND COMPOSE
Following official Android guidelines, Jetpack Compose functions must be pure consumers of state.
- **ViewModels** expose state via `StateFlow`.
- **UI** triggers intents/actions (e.g., `onDownloadClick(modelId)`).

## THE MODEL MANAGER UI
Must explicitly separate models into visual sections or filtered lists:
1. **Installed**: Ready to load. Shows "Load Engine" or "Active".
2. **Available**: Shows "Download".
3. **Unsupported**: Greyed out, explicitly stating the reason (e.g., "Requires >8GB RAM" or "Vision Model - Not Supported").
4. **Failed**: Highlighted in red with a "Retry" and exact error text.

## THE IDE / VIBE CODING UI
- **Workspace Tree**: Left panel showing local project files.
- **Diff Viewer**: Split pane showing original vs. generated code from Roo Code.
- **Action Strip**: "Apply Patch", "Reject", "Run Tests".

## DIAGNOSTICS & EMPTY STATES
- Never show an empty screen without context.
- If the Model list is empty: "No models found in catalog. [Refresh Manifest] [Rescan Local Storage]"
- The Chat header must actively bind to the Engine state: `Loading -> Loaded -> Inference -> Idle`.
