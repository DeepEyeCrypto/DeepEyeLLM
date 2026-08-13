# Release Plan: Agent Studio & Skill Store (v1.2.0)

## Overview
This document outlines the deployment and release strategy for the Agent Studio and Skill Store modular update to DeepEyeLLM.

## CI/CD Pipeline Augmentation
The existing `.github/workflows/android-ci.yml` will now include modular test paths for:
- `com.deepeye.agent.core.agent.*`
- `com.deepeye.agent.core.skill.*`

## Skill Store Asset Deployment
Skills in the DeepEye ecosystem are dynamically loaded.
1. **Repository Setup:** A new dedicated repository `deepeye-skills-registry` will be created.
2. **Auto-Deployment:** A GitHub Action in the registry repo will automatically validate `SkillManifest` JSON files and publish them to a CDN (e.g., AWS CloudFront or Vercel Edge Network).
3. **App Sync:** The DeepEye Android app's `SkillService` will ping the CDN endpoint to fetch the latest available skills, eliminating the need to update the core APK for every new skill.

## Rollout Phases
- **Alpha:** Internal dogfooding. Agents restricted to "Read-Only" memory access.
- **Beta:** Opt-in via Google Play Console Internal Track.
- **Production:** Full rollout to main release track with complete Bento Grid UI.
