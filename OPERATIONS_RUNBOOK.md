# OPERATIONS_RUNBOOK.md

## 1. Service Level Indicators (SLIs)

| SLI | Definition | Measurement |
|-----|-----------|-------------|
| Local inference latency | Time from prompt to first token | Mobile profiler, histogram |
| Local inference throughput | Tokens per second | Mobile profiler, histogram |
| RAG retrieval latency | Time from query to top-k results | Custom metric, histogram |
| Cloud fallback latency | Time from routing decision to response | Gateway logs, histogram |
| App crash rate | Crashes per session | Firebase Crashlytics / custom |
| Skill sync success rate | Successful syncs / total attempts | Sync service logs |
| Policy decision latency | OPA evaluation time | OPA metrics endpoint |
| Memory usage | Peak RAM during inference | Android Profiler |

## 2. Service Level Objectives (SLOs)

| SLO | Target | Measurement Window | Error Budget |
|-----|--------|-------------------|--------------|
| Local inference P95 latency | < 500 ms | 28 days | 5% |
| RAG retrieval P95 latency | < 100 ms | 28 days | 5% |
| Cloud fallback P95 latency | < 200 ms | 28 days | 5% |
| App crash-free rate | > 99.5% | 28 days | 0.5% |
| Skill sync success rate | > 99% | 7 days | 1% |
| Policy decision P99 latency | < 50 ms | 28 days | 1% |

## 3. Error Budgets

- **Monthly error budget**: Derived from SLO (e.g., 0.5% crash budget).
- **Alert**: At 50% budget consumed → warn team.
- **Alert**: At 80% budget consumed → freeze non-critical releases.
- **Alert**: At 100% budget consumed → halt releases, focus on reliability.

## 4. Autoscaling

### On-Device
- Not applicable; resource-constrained.
- Instead: dynamic model selection based on available RAM/CPU.

### Cloud Gateway
- **Metric**: Request queue depth + CPU utilization.
- **Scale out**: > 70% CPU for 2 minutes → +1 instance.
- **Scale in**: < 30% CPU for 5 minutes → -1 instance (min 2).
- **Max instances**: 10 (configurable).

### Hermes Bridge
- **Metric**: Active task count + memory usage.
- **Scale out**: > 5 queued tasks per instance → +1 instance.
- **Scale in**: 0 queued tasks for 10 minutes → -1 instance (min 1).

## 5. Capacity Planning

### Model Storage
- Gemma 4 2B: ~1.5 GB
- Gemma 4 4B: ~2.5 GB
- FunctionGemma 270m: ~200 MB
- User custom models: up to 10 GB reserved.
- Total device storage budget: 20 GB.

### Cloud Compute
- Baseline: 2 vCPU, 4 GB RAM per gateway instance.
- Baseline: 4 vCPU, 16 GB RAM per Hermes instance (for heavy analysis).
- Growth factor: 2x per year projected.

### Memory (On-Device)
- Target: ≤ 100 MB during inference.
- Budget: 50 MB model weights in protected memory + 30 MB runtime overhead + 20 MB app UI.

## 6. Cost Allocation

### Showback
- Per-feature cost tracking: local inference (zero), cloud analysis ($), sync ($), storage ($).
- Dashboard available to users with cloud features enabled.

### Chargeback
- Enterprise tenants: monthly invoice by workspace.
- Resource credits consumed per task type visible in real-time.

### Cost Governance
- Daily budget alerts at 50%, 80%, 100%.
- Hard stop at 120% (emergency fund reserved for SecurityOfficer approval).
- Reserved instances for baseline load; spot/preemptible for burst.

## 7. Incident Response

### Severity Levels
| Level | Criteria | Response Time |
|-------|----------|--------------|
| SEV-1 | Complete outage, data loss, security breach | < 15 min |
| SEV-2 | Major feature degradation, > 10% users affected | < 1 hour |
| SEV-3 | Minor feature issue, workaround available | < 4 hours |
| SEV-4 | Cosmetic, enhancement request | < 1 day |

### Runbook: Local Runtime Crash Loop
1. Detect: Crashlytics alert + user reports.
2. Isolate: Disable auto-restart; capture last 3 crash dumps.
3. Diagnose: Check model signature, memory pressure, recent OS update.
4. Mitigate: Push config to use smaller model (Gemma 2B instead of 4B).
5. Fix: Patch in next release; hotfix if SEV-1/2.

### Runbook: Cloud Cost Spike
1. Detect: Budget alert > 100%.
2. Isolate: Circuit breaker on cloud routing; force local-only.
3. Diagnose: Analyze router logs for misclassification.
4. Mitigate: Fix policy; refund credits if user-facing.
5. Fix: Deploy corrected router + regression test.

---
[AEOS_STATUS: OPERATIONS_DEFINED]
