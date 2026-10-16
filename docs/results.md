# Measured results and limitations

## Verified GitHub experiment

**CI and CodeQL passed** on commit `a5229cd7bd368073b43ca6e093cfb964f656e6e2`:
[CI run](https://github.com/Ayyappa-BK/identitypulse-reliability-platform/actions/runs/38830929031),
[CodeQL run](https://github.com/Ayyappa-BK/identitypulse-reliability-platform/actions/runs/38830928954).
CI includes Java verification, real dependency integration, packaged-library vulnerability scanning, source secret scanning, Helm/render validation, Prometheus rule tests and the full Docker load/fault experiment. The current Actions badges show subsequent main-branch verification; the numbers below are permanently tied to this captured run, rather than silently updated with newer runs.

Experiment date: **October 15, 2026, America/Chicago** (artifact timestamps cross into October 16 UTC). Environment: GitHub hosted ubuntu-latest x64 runner, Java 21, Docker Compose, one app instance, PostgreSQL 17, Redis 7.4 and Toxiproxy. HTTP loopback, no TLS ingress. Closed-loop Python client, no warmup, eight concurrent clients for the two 500-request runs. This is a small repeatable smoke experiment, not a capacity benchmark.

| Phase | Requests / expected status | Successful expected responses | Throughput | Client p50 | Client p95 | Client p99 |
|---|---|---|---|---|---|---|
| Initial baseline | 500 / 200 | 500 | 255.3 req/s | 25.1ms | 43.5ms | **356.5ms** |
| Signed wrong audience | 20 / 401 | 20 | 117.6 req/s | 20.4ms | 151.0ms | 157.8ms |
| After all faults | 500 / 200 | 500 | 380.6 req/s | 18.4ms | 32.5ms | 110.1ms |

**The initial p99 exceeded the candidate 300ms latency threshold.** The load script gates response correctness, not latency compliance; the CI success therefore does not certify the latency objective. The later faster sample may reflect warmup and resource variation. It is not evidence of a causal performance improvement.

| Deliberate fault | Fault observations | Maximum fault-request duration | Recovery after restoration |
|---|---|---|---|
| Process SIGKILL | 12/12 transport failures | 0.5ms | 8.112s |
| PostgreSQL proxy disabled | 12/12 HTTP 503 | 515.4ms | 3.115s |
| Redis proxy disabled | 12/12 HTTP 503 | 440.1ms | 4.121s |
| PostgreSQL downstream +1500ms | 12/12 HTTP 503 | 1016.8ms | 4.115s |

Every dependency fault failed closed; every scenario recovered valid authentication and readiness within the 120-second assertion. Recovery starts after fault removal and includes polling granularity/readiness checks, not time to detect. Process recovery uses explicit Compose startup; it does not establish Kubernetes replica failover behavior.

The run passed **17 JUnit tests**: 16 unit/Spring Security cases and one real Testcontainers integration case that exercises revocation plus Redis/PostgreSQL pause and recovery. Real RSA JWT signature verification and a real JWKS HTTP server cover issuer, audience, expiry, required claims, scopes, issuer-fetch outage classification and policy failures. A subsequent administrator-policy regression test brings the current source suite to **17 local tests plus one Docker integration test**; consult the current CI run for that commit's results.

Raw load/fault JSON, generated structured exercise incident reports and the verification summary are versioned under [`results/measured-ci/38830929031`](../results/measured-ci/38830929031). GitHub's `java-evidence` artifact contains JUnit XML and coverage; `reliability-evidence` contains the original JSON and Compose logs. Raw artifacts may expire under GitHub retention; the committed JSON remains available.

## Local verification

macOS arm64, Eclipse Temurin 21.0.12.1, Maven, Spring Boot 4.1.1: **17 tests, zero failures, zero errors, zero skipped**. Last local Maven test wall time: **8.290s**. PostgreSQL/Redis are mocked in this local suite; the GitHub Testcontainers run supplies the real dependency evidence. The administrator regression verifies that even a correctly scoped administrative JWT must pass current revocation/account policy.

Helm 3.19.0 strict lint: one chart, zero failures. Prometheus 3.5.0: seven main rules valid and alert rule unit test passed. Python script compilation and security regression guards pass. A local synthetic-probe harness with a fixture HTTP server verified success/failure observations exported to Prometheus; this harness is tooling evidence, not identity availability measurement.

Trivy 0.76.0 scanned **132 packaged runtime Java dependencies**, with **zero fixable HIGH/CRITICAL findings** after upgrading Boot/Tomcat/Jackson. CI independently passed its runtime scan and source secret scan. See `results/measured-local-security.json`. Source/BOM-only scanning was replaced with scanning the actual packaged dependencies, avoiding ambiguous Maven BOM override resolution. No vulnerability exclusions were added to bypass the initial dependency findings.

CodeQL security-extended analysis passed. Its CSRF finding was reviewed and dismissed with the bearer-only/stateless-authentication rationale recorded in GitHub and `docs/security.md`; no open finding remained at review time. Scanner success is not proof of security.

Docker and kubectl were not available locally. Helm/promtool/JDK were downloaded into the workspace. Kubernetes runtime behavior remains **unmeasured**: scheduling, HPA, PDB, multi-replica and multi-zone behavior need a cluster exercise.

## Limits of these measurements

* No production users, external DNS/TLS/ingress, real IdP key-rotation exercise, production pager routing, regional failover or long-duration soak was tested.
* All requests use one local fixture account and a finite-lived JWT. Status 0 in load/fault JSON denotes a transport failure; credentials are omitted from evidence. Token generation is outside the timed load requests.
* Unit breaker tests explicitly transition half-open state; fault experiments include the actual configured breaker wait and dependency recovery.
* Faults are intentional and short; they are not independent production incidents and do not establish mean time to recovery or observed availability.
* Telemetry and external-probe coverage must be validated across a full SLO window. Missing measurements must not be counted as success.

**No observed 99.99% availability is claimed.** The rolling 30-day time budget at that target is **259.2 seconds (4m 19.2s)**. The separate request budget is eligible requests × 0.0001. Short baseline success samples do not establish either SLO.
