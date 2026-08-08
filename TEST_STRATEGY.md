# TEST_STRATEGY.md

## Philosophy
Test-Driven Development (TDD): Write tests first, implement to satisfy tests, refactor.

## Test Pyramid

```
        /\
       /  \
      / E2E \      ~5%  (Critical user journeys)
     /--------\
    /  Integration \  ~15% (Router, memory, API contracts)
   /----------------\
  /     Unit Tests     \ ~80% (Pure functions, model logic, policies)
 /------------------------\
```

## Unit Tests

### Coverage Targets
- Core Rust library: ≥ 90%
- Kotlin Android layer: ≥ 80%
- Swift iOS layer: ≥ 75%
- Python cloud bridge: ≥ 85%

### Key Unit Test Areas
1. **Task Router** — Decision matrix for local vs cloud routing.
2. **Model Registry** — Loading, unloading, quantization detection.
3. **Memory Engine** — CRUD, embedding similarity, BM25 retrieval.
4. **Skill Parser** — SKILL.md → JSON conversion, version validation.
5. **Policy Engine** — OPA decision caching, offline fallback.
6. **File Preprocessor** — MIME detection, chunking, extraction.

### Tools
- Rust: `cargo test` + `tarpaulin` (coverage)
- Kotlin: JUnit 5 + MockK + JaCoCo
- Swift: XCTest + `swift-coverage`
- Python: pytest + `coverage.py` + `hypothesis` (property-based)

## Integration Tests

### Scope
- End-to-end inference pipeline (load model → tokenize → generate → decode).
- RAG pipeline (file import → chunk → embed → index → query → retrieve).
- Skill sync pipeline (mock Hermes upstream → sync → validate → store).
- Routing pipeline (task input → classify → execute local or cloud mock).
- Policy enforcement (mock OPA → decision → action allow/deny).

### Tools
- Android: Espresso + UI Automator
- iOS: XCUITest
- Cloud bridge: pytest + `responses` / `httpx` mock
- Hermes integration: Docker Compose test stack

## E2E Tests

### Critical User Journeys
1. **Offline Chat** — Airplane mode, launch app, chat with local model, verify no network calls.
2. **File Analysis** — Import APK, analyze, receive structured report.
3. **Skill Auto-Sync** — Trigger sync, verify new skill appears, execute skill.
4. **Cloud Debug** — Upload code, receive deep debug results from Hermes bridge.
5. **Privacy Compliance** — Verify sensitive data never leaves device without explicit opt-in.

### Tools
- Android: Maestro
- iOS: Maestro / XCUITest
- Desktop: Playwright (Tauri)

## Security Tests

### Static Analysis
- Rust: `cargo audit` + `cargo geiger` (unsafe code detection)
- Kotlin: Detekt + Android Lint
- Swift: SwiftLint + Xcode static analysis
- Python: Bandit + Safety

### Dynamic Analysis
- OWASP ZAP for API penetration testing.
- Frida for mobile runtime manipulation testing.
- Prompt injection benchmark suite (custom + public datasets).

### Fuzzing
- Model input fuzzing (malformed GGUF, adversarial prompts).
- File parser fuzzing (corrupted APKs, oversized logs).
- Policy engine fuzzing (invalid Rego policies).

## Performance Tests

| Scenario | Target | Tool |
|----------|--------|------|
| Local inference latency | ≤ 20 tokens/sec mid-range | Custom benchmark |
| RAG retrieval latency | ≤ 100 ms (1,000 memories) | Custom benchmark |
| App cold start | ≤ 3 seconds | Android Profiler |
| Memory footprint | ≤ 100 MB during inference | Memory Profiler |
| Cloud fallback latency | ≤ 200 ms (network + model) | k6 / Locust |

## AI Safety Tests

- **Prompt Injection**: 500+ adversarial prompts across categories (jailbreak, indirect injection, tool poisoning).
- **Hallucination**: Benchmark on coding tasks, math, factual QA with ground-truth datasets.
- **Red Team**: Quarterly external red team engagement.
- **Model Governance**: Validate no PII leakage in model outputs.

## Regression Testing

- Full suite runs on every PR.
- Nightly extended suite (E2E + security + performance).
- Pre-release smoke test on physical devices (Pixel, Samsung, Xiaomi).

---
[AEOS_STATUS: TESTS_PLANNED]
