# 03 — Engineering Rules & Guardrails: DeepEyeLLM

## Zero-Latency Input Rules (Stage W1 Standards)
1. **Keystroke Hot-Path Gating**:
   - `TypingArea` and `ZeroLatencyTypingLayer` must bypass React/Compose synthetic state updates on keydown.
   - Processing time per keystroke must be $\le 1\text{ms}$.
2. **Forbidden on Keystroke Hot-Path**:
   - `setState` or `StateFlow.emit()` calls that trigger full screen recomposition.
   - DOM or Layout queries (`getBoundingClientRect`, `measure`, `layout`).
   - Heavy JSON parsing, regex compilation, or high-volume IPC calls.

---

## State Hoisting & Compose Standards
1. **Stateless Composables**:
   - UI Composables must be purely functional: accept state props and emit event callbacks (`onClick`, `onValueChange`).
   - `remember { mutableStateOf(...) }` is permitted ONLY for local ephemeral UI states (e.g., dropdown expanded state, animation offsets).
2. **ViewModel Ownership**:
   - ViewModels own all business state as `StateFlow<UiState>`.
   - Never duplicate the same state data class across Screen and ViewModel boundaries.

---

## Error Handling & Exception Policy
1. **No Silent Exception Swallowing**:
   - Never catch exceptions with empty blocks or return dummy fallback strings unless explicitly logging diagnostics.
2. **User Feedback Requirement**:
   - All asynchronous failures (download error, out-of-memory, engine failure) must surface explicit UI state or Toast messages (`"Model initialization failed: Insufficient RAM"`).
3. **RAM Guard Verification**:
   - Before initializing any model binary, system memory must be validated: $\text{RequiredRAM} \le \text{TotalRAM} \times 0.75$.

---

## Allowed Dependencies & Forbidden Libraries
- **Allowed**: `AndroidX Jetpack`, `Compose BOM`, `Hilt`, `Room`, `LiteRT-LM`, `OkHttp` (local server only), `JNA/JNI`.
- **Forbidden**: Third-party tracking/analytics libraries (Firebase Analytics, Mixpanel), reflection-heavy frameworks, unverified cloud inference SDKs.
