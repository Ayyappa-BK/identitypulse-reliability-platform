# Measured results and limitations

## Local verification — 2026-10-15 (America/Chicago)

Executed on macOS arm64 using a workspace-local Eclipse Temurin JDK 21.0.12.1 and Maven. `mvn test` completed successfully: **15 tests, 0 failures, 0 errors, 0 skipped**. Suite wall time reported by Maven: **5.685 seconds**. Tests include a local JWKS HTTP server, real RSA signatures and Spring Security filter processing; PostgreSQL/Redis are mocked in this local suite. This is functional/security evidence, not production latency or availability evidence.

Helm 3.19.0 strict lint: **1 chart, 0 failures**. Prometheus 3.5.0 promtool: **7 rules valid**, alert unit test passed. Python scripts compile and security regression guards pass.

Docker, kubectl and Helm were not preinstalled. Helm/promtool were downloaded to the workspace for validation; no Docker daemon or Kubernetes cluster was available locally. Therefore local Testcontainers, Docker load/chaos, container startup and Kubernetes runtime behavior are **not locally measured**. GitHub Actions is configured to execute required real PostgreSQL/Redis tests and the complete Compose reliability experiment. GitHub run results and measured JSON will be recorded here once observed.

## Evidence interpretation

* `results/measured-local-tests.json`: captured local test summary.
* CI `java-evidence` artifact: JUnit XML, real dependency integration results and coverage.
* CI `reliability-evidence` artifact: baseline/recovery load JSON, negative-token JSON, four failure reports and Compose logs.
* Load uses a bounded closed-loop Python client, no warmup, HTTP loopback to the app, finite request count and concurrency. It is a repeatable smoke experiment, not a capacity benchmark. Token generation is outside timed requests. Client request timeout is 5 seconds. Status 0 means transport failure.
* Fault tests assert fail-closed responses during dependency loss, observe a process interruption and poll valid authentication plus readiness for recovery within 120 seconds. Recovery time starts after fault removal; total time also includes the fault experiment. It is not mean time to recover across independent incidents.
* Testcontainers pauses real Redis and PostgreSQL containers, asserts exceptions then restores them. Unit tests explicitly transition half-open state to verify breaker behavior; that unit case does not measure the five-second wait.
* No external traffic, TLS ingress, multi-zone infrastructure, real IdP key-rotation exercise, long-duration soak or production pager delivery has been measured.

**No observed 99.99% availability is claimed.** A short test interval, even with zero failures, cannot establish the rolling 30-day objective. The separate time budget is 259.2 seconds per 30 days; request budget is eligible requests × 0.0001.
