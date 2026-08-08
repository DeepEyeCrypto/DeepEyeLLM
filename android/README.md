# DeepEyeAgent Android Skeleton

This skeleton wires LiteRT-LM into an Android-friendly architecture for:
- local chat
- streaming responses
- image/audio analysis
- file analysis
- Hermes deep-debug fallback

## Files
- `DeepEyeAgentEngine.kt` — LiteRT-LM wrapper
- `FileAnalysisService.kt` — file import + analysis
- `ToolRegistry.kt` — project tools and Hermes routing
- `HermesClient.kt` — cloud debug contract

## Next Steps
1. Add Gradle dependency for LiteRT-LM.
2. Hook `HermesClient` to your FastAPI bridge.
3. Add a ViewModel + UI for chat and file upload.
4. Add OPA policy checks before cloud routing.
