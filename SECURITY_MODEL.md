# SECURITY_MODEL.md

## 1. Zero Trust Architecture

### Principle
"Never trust, always verify." Every component, agent, tool, and user must authenticate and authorize every request, regardless of network location.

### Implementation
- Device attestation on startup (SafetyNet / DeviceCheck + custom checks).
- Agent identity revalidation before privileged actions.
- Runtime attestation checks (tamper detection, debugger detection).
- All inter-service calls carry signed JWTs with short expiry.

## 2. RBAC & Least Privilege

### Roles
| Role | Permissions | Approval Override |
|------|-------------|-------------------|
| User | Chat, file analysis, local memory | No |
| PowerUser | Custom model import, skill editing | No |
| SecurityOfficer | Policy changes, RBAC modifications, audit access | Yes |
| ReleaseManager | Production deployment, canary promotion | Yes |
| ArchitectAgent | Read architecture spec, propose changes | No |
| DeveloperAgent | Read spec, write implementation | No |
| QAAgent | Read implementation, write tests | No |

### Enforcement
- OPA evaluates every action against role + resource + context.
- Cached policy snapshot for offline enforcement.
- Elevation requires multi-party approval + time-bound token.

## 3. Secret Management

- Local: Android Keystore / iOS Keychain for device keys.
- Cloud: HashiCorp Vault or AWS Secrets Manager with rotation.
- No secrets in code or logs.
- Model download URLs signed with time-limited tokens.

## 4. Encryption

### At Rest
- Local memory (CozoDB): SQLCipher or filesystem encryption.
- Model weights: encrypted on disk, decrypted to protected memory during inference.
- Key material: hardware-backed keystore.

### In Transit
- TLS 1.3 for all cloud communication.
- mTLS between internal services.
- Certificate pinning for mobile app → API.

## 5. Audit Logging

- Every control-plane decision logged with:
  - Timestamp, actor, action, resource, policy version, decision, justification.
- Logs are tamper-evident (signed hash chain).
- Retention: 90 days local, 1 year cloud (if enabled).

## 6. Supply Chain Security

### SBOM Strategy
- Generate CycloneDX SBOM at build time for every release.
- Validate all dependencies against known vulnerability database (OSV, Snyk).
- Block builds with critical CVEs.

### Artifact Signing
- Ed25519 signatures for all release artifacts.
- Signature published alongside artifact.
- Client verifies signature before installation/execution.

### Dependency Validation
- All dependencies pinned to exact versions.
- Checksums validated at build and runtime.
- No unverified third-party repositories.

## 7. Trust Boundaries

```
[User] --(auth)--> [Mobile App] --(policy-gated)--> [Local Runtime]
                                    |
                                    +--(encrypted)--> [Cloud Gateway] --(mTLS)--> [Hermes Agent]
                                    |
                                    +--(policy-gated)--> [Device APIs]
```

### Boundary 1: User ↔ Mobile App
- Biometric or PIN auth for app launch.
- Separate auth for sensitive actions (device settings, cloud sync).

### Boundary 2: Mobile App ↔ Local Runtime
- IPC isolation (Android Binder with custom permission checks).
- No direct file system access from WebView layer.

### Boundary 3: Mobile App ↔ Cloud Gateway
- TLS 1.3 + certificate pinning.
- OPA policy must explicitly allow cloud escalation per task.
- Data classification labels determine what can leave device.

### Boundary 4: Cloud Gateway ↔ Hermes Agent
- mTLS with service identity.
- Network segmentation (VPC / private subnet).

## 8. Attack Surface

| Surface | Controls |
|---------|----------|
| Mobile app input | Input validation, prompt injection filters, sandboxed parsing |
| Model loading | Signature verification, format validation, memory limits |
| Network APIs | Rate limiting, authN/authZ, DDoS protection |
| File analysis | MIME validation, sandboxed extraction, size limits |
| Skill execution | Sandboxed tool execution, resource limits, timeout |
| Cloud sync | E2E encryption, user opt-in, residency controls |

## 9. Threat Surface (STRIDE Mapping)

| Threat | STRIDE Category | Mitigation |
|--------|----------------|----------|
| Spoofing identity | S | Device attestation, JWTs, mTLS |
| Tampering with data | T | Ed25519 signing, integrity checks |
| Repudiation | R | Tamper-evident audit logs |
| Information disclosure | I | Encryption at rest + in transit, least privilege |
| Denial of service | D | Rate limiting, resource quotas, circuit breakers |
| Elevation of privilege | E | RBAC, OPA, time-bound overrides, multi-party approval |

---
[AEOS_STATUS: SECURITY_REVIEWED]
