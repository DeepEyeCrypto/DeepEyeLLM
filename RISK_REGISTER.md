# RISK_REGISTER.md

## Risk Methodology
- **Threat Modeling**: STRIDE
- **Risk Quantification**: FAIR (Factor Analysis of Information Risk)
- **Thresholds**: Low ≤ 0.3, Medium ≤ 0.6, High > 0.6

## Risk Inventory

| ID | Description | Likelihood | Impact | FAIR Score | Severity | Mitigation | Residual Risk | Owner |
|----|-------------|-----------|--------|-----------|----------|-----------|---------------|-------|
| R-001 | **Prompt Injection** — Malicious user input hijacks agent to execute unintended actions. | 0.7 | 0.8 | 0.56 | High | Input validation, OPA policy gating, sandboxed tool execution, red-team benchmarks. | 0.18 | SecurityArchitect |
| R-002 | **Model Hallucination** — On-device LLM generates incorrect code/patches causing system damage. | 0.6 | 0.7 | 0.42 | Medium | Human approval gates for destructive actions, diff review UI, rollback capability. | 0.12 | QA |
| R-003 | **Data Exfiltration** — Sensitive local data leaked to cloud during fallback. | 0.4 | 0.9 | 0.36 | Medium | OPA routing policies, encrypted transit, data classification labels, explicit user opt-in per task. | 0.09 | SecurityArchitect |
| R-004 | **Supply Chain Poisoning** — Compromised upstream model or dependency introduces backdoor. | 0.3 | 0.9 | 0.27 | Medium | Ed25519 signing, SBOM validation, dependency signature checks, reproducible builds. | 0.05 | DevOpsArchitect |
| R-005 | **Skill Auto-Sync Breakage** — Hermes upstream breaking change crashes local agent. | 0.5 | 0.5 | 0.25 | Low | Version pinning, semantic versioning, rollback mechanism, staged rollout. | 0.06 | BackendArchitect |
| R-006 | **On-Device Memory Exhaustion** — Large model + RAG causes OOM on mid-range phones. | 0.6 | 0.5 | 0.30 | Low | Model size limits per device tier, memory monitoring, graceful degradation to smaller model. | 0.08 | PlatformArchitect |
| R-007 | **Cloud Cost Overrun** — Heavy cloud usage exceeds budget due to misconfigured router. | 0.4 | 0.6 | 0.24 | Low | Resource credits, budget marketplace, automatic circuit breaker at 80% spend. | 0.05 | FinOps |
| R-008 | **Offline Functionality Loss** — Critical bug prevents offline operation, breaking core value prop. | 0.3 | 0.8 | 0.24 | Low | Offline-first test suite, airplane mode CI tests, staged rollout with offline smoke tests. | 0.06 | SRE |
| R-009 | **RBAC Privilege Escalation** — Agent or user exploits role override to gain unauthorized access. | 0.3 | 0.9 | 0.27 | Medium | Time-bound overrides, multi-party approval for production, continuous attestation. | 0.07 | SecurityReviewer |
| R-010 | **Multi-Region Data Residency Violation** — Failover routes data to non-compliant region. | 0.2 | 0.8 | 0.16 | Low | Region-aware routing, data residency labels, policy enforcement at ingress. | 0.03 | ComplianceOfficer |
| R-011 | **Adversarial Model Extraction** — Attacker queries agent to extract training data or model weights. | 0.2 | 0.6 | 0.12 | Low | Rate limiting, query deduplication, output filtering, no raw embedding exposure. | 0.03 | SecurityArchitect |
| R-012 | **Policy Drift** — Production policies diverge from governed baseline without detection. | 0.4 | 0.5 | 0.20 | Low | Continuous verification, drift detection alerts, policy version pinning in OPA. | 0.05 | SecurityReviewer |

## Aggregated Risk Summary
- **High Risks**: 1 (Prompt Injection)
- **Medium Risks**: 4 (Hallucination, Exfiltration, Supply Chain, Privilege Escalation)
- **Low Risks**: 7
- **Total Open Risks**: 12
- **Mitigated (Residual < 0.1)**: 6

---
[AEOS_STATUS: RISKS_ANALYZED]
