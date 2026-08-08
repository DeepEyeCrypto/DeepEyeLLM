# RELEASE_PLAN.md

## Release Rings

### Ring 1: Internal
- Audience: Core team + trusted contributors.
- Criteria: All CI passes, manual smoke test on 2 devices.
- Duration: 3 days.
- Feedback: Internal Slack, crash reports.

### Ring 2: Beta
- Audience: Open beta via Google Play + TestFlight.
- Criteria: No SEV-1/2 bugs from Internal; ≥ 80% crash-free rate.
- Duration: 2 weeks.
- Feedback: Public issue tracker, Discord community.

### Ring 3: Staging
- Audience: Canary production users (5% of active base).
- Criteria: No regressions from Beta; performance SLOs met.
- Duration: 1 week.
- Feedback: Automated monitoring, SLO dashboards.

### Ring 4: Production
- Audience: 100% of users.
- Criteria: Staging canary error rate < 0.1%.
- Rollout: 20% → 50% → 100% over 3 days.

## Canary Strategy

- **Deployment**: Kubernetes rollout with Flagger or Argo Rollouts.
- **Metrics**: Error rate, latency P95, crash rate, custom business metrics.
- **Thresholds**:
  - Error rate > 1% → automatic rollback.
  - Latency P95 > 500 ms → automatic rollback.
  - Crash rate > 0.5% → automatic rollback.
- **Duration**: Minimum 1 hour at each percentage step.

## Rollback Strategy

### Automatic
- Triggered by canary metric breach.
- Time to rollback: < 2 minutes.
- Previous version promoted automatically.

### Manual
- Triggered by human decision (SecurityOfficer, ReleaseManager).
- Time to rollback: < 5 minutes via CLI / dashboard.
- Post-rollback: Root cause analysis within 24 hours.

## Monitoring Plan

| Stage | Tool | Focus |
|-------|------|-------|
| Internal | Firebase Crashlytics + custom logging | Stability |
| Beta | Sentry + Play Console | Crash patterns, ANR |
| Staging | Prometheus + Grafana | SLOs, latency, error budget |
| Production | Full observability stack + PagerDuty | Alerting, incident response |

## DORA Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| Deployment Frequency | ≥ 1 per week | CI/CD pipeline logs |
| Lead Time for Changes | < 3 days | PR merge to production |
| Mean Time to Recovery (MTTR) | < 1 hour | Incident log timestamps |
| Change Failure Rate | < 10% | Failed releases / total releases |

## Release Checklist

- [ ] All P0 tests passing (unit, integration, E2E, security).
- [ ] SBOM generated and signed.
- [ ] Ed25519 signatures on all artifacts.
- [ ] STRIDE threat model reviewed (no new high risks).
- [ ] FAIR risk register updated.
- [ ] ADR ledger current (no orphaned decisions).
- [ ] OPA policies deployed and smoke-tested.
- [ ] Canary environment configured.
- [ ] Rollback procedure rehearsed.
- [ ] Incident response team on standby.
- [ ] User-facing changelog published.

---
[AEOS_STATUS: RELEASE_READY]
