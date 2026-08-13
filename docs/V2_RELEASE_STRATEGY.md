# DeepEyeLLM v2.0 Release Strategy
> RAG + Multi-Modal + P2P Sharing Expansion

## Version Scheme
- **v2.0.0-alpha.1** → Internal dogfooding (Week 3)
- **v2.0.0-beta.1** → Google Play Internal Track (Week 4)
- **v2.0.0-rc.1** → Open Beta via Play Console (Week 5)
- **v2.0.0** → Production release (Week 6)

---

## Feature Flags (Gradual Rollout)
Each module ships behind a feature flag to allow independent rollout.

| Flag | Module | Default (Alpha) | Default (Beta) | Default (Prod) |
|------|--------|-----------------|----------------|-----------------|
| `FF_RAG_ENGINE` | On-Device RAG | ✅ ON | ✅ ON | ✅ ON |
| `FF_VOICE_INPUT` | Voice-to-Text | ✅ ON | ✅ ON | ✅ ON |
| `FF_VISION_CAMERA` | LLaVA Vision | ✅ ON | ⚠️ 50% | ✅ ON |
| `FF_P2P_SHARING` | P2P Model Transfer | ✅ ON | ⚠️ 25% | ⚠️ 50% |

> P2P ships slower due to higher security surface area. Full rollout after 2 weeks of beta monitoring.

---

## CI/CD Pipeline Architecture

```
feature/* branches
    │
    ▼
┌─────────────────────────────────────────────┐
│  Per-Agent Test Tracks (Parallel)           │
│                                             │
│  ⚙️ FORGE: RAG Engine Tests                 │
│  🔊 ECHO: Multi-Modal Tests                │
│  📡 MESH: P2P Transfer Tests               │
│  🎨 GLASS: Compose UI Tests                │
└─────────────┬───────────────────────────────┘
              │ All Pass
              ▼
┌─────────────────────────────────────────────┐
│  🔒 Security Gate                           │
│  CodeQL + Model Signature Verification      │
└─────────────┬───────────────────────────────┘
              │ Pass
              ▼
┌─────────────────────────────────────────────┐
│  🚀 Build Release APK + AAB                 │
│  Upload to GitHub Artifacts                 │
└─────────────┬───────────────────────────────┘
              │ main branch only
              ▼
┌─────────────────────────────────────────────┐
│  📦 Distribution                            │
│  Firebase App Distribution (Alpha)          │
│  Google Play Internal Track (Beta)          │
│  Google Play Production (Release)           │
└─────────────────────────────────────────────┘
```

---

## Device Compatibility Matrix

| Feature | Min RAM | Min Android | GPU Required |
|---------|---------|-------------|--------------|
| RAG (MiniLM Q4) | 2 GB | API 26 (8.0) | No |
| Voice (Native SR) | 1 GB | API 26 (8.0) | No |
| Voice (Whisper.cpp) | 3 GB | API 26 (8.0) | Recommended |
| Vision (LLaVA) | 4 GB | API 28 (9.0) | Yes |
| P2P Sharing | 1 GB | API 28 (9.0) | No |

---

## Monitoring & Rollback Plan

### Crash Monitoring
- Firebase Crashlytics for real-time ANR and crash tracking.
- Custom event: `rag_indexing_oom`, `whisper_load_fail`, `p2p_transfer_corrupt`.

### Rollback Triggers
| Trigger | Action |
|---------|--------|
| Crash rate > 1% on any module | Disable feature flag remotely |
| P2P signature verification bypass detected | Kill `FF_P2P_SHARING` globally, push hotfix |
| RAG indexing causes ANR on >5% of devices | Throttle indexing batch size via remote config |

### Success Metrics
| Metric | Target |
|--------|--------|
| RAG query latency (on-device) | < 200ms for top-5 retrieval |
| Voice transcription accuracy | ≥ 92% (English) |
| P2P transfer success rate | ≥ 95% completion |
| Vision model load time | < 3 seconds on flagship devices |
| Overall crash-free rate | ≥ 99.5% |
