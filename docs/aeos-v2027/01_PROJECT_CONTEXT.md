# 01_PROJECT_CONTEXT

## OVERVIEW
AEOS v2027.1.0 represents a clean slate premium rewrite of the DeepEyeLLM / DeepEyeAgent local AI shell. The primary outcome is to deliver a robust, offline-first, zero-trust AI operating environment on Android. 

It unifies three distinct open-source upstreams into a single app shell without hard-forking their codebases, preserving independent update cycles.

## UPSTREAM TRIFECTA
The app integrates the following systems through dedicated adapters:

1. **Google AI Edge Gallery**: Provides the on-device local model catalog, model discovery, and native edge inference engine.
2. **Hermes Agent**: Supplies long-lived agentic memory, workflow orchestration, and self-evolving skills.
3. **Roo Code**: Delivers an IDE-native Vibe Coding experience, natural language patch generation, and diff application.

## EXECUTIVE CONSTRAINTS
- **Local-Only & Offline-First**: No silent cloud fallbacks, no remote inference telemetry. All logic executes on-device.
- **App-Specific Storage**: All models, skills, and code diffs reside exclusively in the app's secure internal storage.
- **Architectural Separation**: Strict separation between UI composition, state holders (ViewModels), storage layer, and inference engine.
- **Explicit Visibility**: Every failure, unsupported model, corrupt checksum, or network block must be transparently reported in the UI.
- **Deterministic Validation**: TDD-first approach utilizing ADB-driven instrumentation tests and chaos engineering.

## THE PROBLEM
Previous implementations suffered from opaque model download states, silent fallbacks, and tightly coupled upstream code that made updates brittle. Model lists were inconsistent, and the engine state could lie to the UI about the active model.

## THE SOLUTION
This rewrite introduces a unified Sync Layer and App Shell that imports validated manifests from the upstreams. It enforces atomic downloads, mandatory checksums, and a first-class domain model for every model, skill, and IDE patch. UI acts purely as a consumer of strictly hoisted state flows.
