# IdentityPulse

[![CI](https://github.com/Ayyappa-BK/identitypulse-reliability-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/Ayyappa-BK/identitypulse-reliability-platform/actions/workflows/ci.yml)
[![CodeQL](https://github.com/Ayyappa-BK/identitypulse-reliability-platform/actions/workflows/codeql.yml/badge.svg)](https://github.com/Ayyappa-BK/identitypulse-reliability-platform/actions/workflows/codeql.yml)

A Java 21 authentication reliability engineering platform. This is a working OAuth2 resource server and authorization decision service, with real PostgreSQL account policy and Redis revocation checks. It validates externally issued JWTs; password enrollment, MFA and token issuance belong to your identity provider. The local lab includes a disposable RSA issuer fixture, never an application backdoor.

**99.99% is an availability objective, not a measured achievement.** See [SLO definitions](docs/slos.md) and [measured evidence and limitations](docs/results.md).

## Run the local reliability lab

Requirements: Java 21, Maven 3.9+, Docker Engine with Compose v2, Python 3.11+, OpenSSL, about 4 GB free RAM. Helm 3.19+ for chart validation. Linux CI supplies Docker; Docker Desktop also works. All published lab ports bind to loopback. Never expose the lab issuer, Toxiproxy or management ports to untrusted networks.

```sh
git clone https://github.com/Ayyappa-BK/identitypulse-reliability-platform.git
cd identitypulse-reliability-platform
mvn -B test                 # unit + signed JWT / Spring Security tests, no Docker
mvn -B verify               # also REQUIRED Testcontainers PostgreSQL/Redis tests
sh scripts/lab-up.sh        # keys/secrets, proxy setup, build, migrations, fixture account
export TOKEN="$(python3 scripts/lab-token.py)"
curl -i -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8080/v1/auth/verify
python3 scripts/load.py --requests 1000 --concurrency 10
for scenario in crash database redis latency; do
  python3 scripts/failure-test.py "$scenario"
done
python3 scripts/load.py --output results/recovered.json
```

Tokens expire after 15 minutes; refresh `TOKEN` before rerunning. A valid signature alone does not enable an unknown account. `load-user` is inserted only by the explicit lab script. `scripts/lab-token.py --invalid` generates a signed wrong-audience token for rejection tests. The admin scope is separate (`--admin`). Keep tokens out of logs and shell tracing.

Open Grafana at http://localhost:3000 (admin; password in `.env`), Prometheus at http://localhost:9091 and probes at http://localhost:9090/actuator/health/readiness. Grafana provisions the health dashboard and Tempo trace datasource. The local trace sample rate is 100%; the application default is 10%. Trace spans contain no tokens or subject identifiers. `docker compose down -v` removes the lab and its database volume; `.local` and `.env` remain for intentional reuse.

## Behavior and controls

* RS256 allowlist, bounded JWKS HTTP timeouts, issuer/audience/expiry/not-before validation, mandatory subject and token ID, default deny routing, independent verify/admin scopes, stateless bearer authentication.
* Every verification checks account enabled state and token revocation. Missing accounts, disabled accounts and revoked tokens return 403. Dependency faults return 503 with Retry-After; malformed or invalid credentials return 401.
* Per-pod circuit breaker, one bounded retry for transient idempotent reads, Redis command/connect limits, PostgreSQL statement/socket limits, finite thread and connection pools. No retry of signature, scope or policy denials.
* Dependency-aware readiness, process-only liveness, graceful shutdown, rolling updates, HPA, disruption budget, nonroot/read-only containers, no Kubernetes API credentials and default-deny network policy with explicit egress.
* Request counters and duration histograms include rejected requests at the security filter boundary. OpenTelemetry exports request and policy spans. No high-cardinality user/token labels.

## Repository map

| Path | Contents |
|---|---|
| `src/` | Spring service, migration, unit/security and real dependency tests |
| `compose.yml`, `scripts/` | Disposable lab, load generator, controlled failure/recovery assertions |
| `observability/` | Prometheus rules and tests, Grafana dashboard, OTel collector, Tempo |
| `deploy/helm/identitypulse/` | Configurable deployment chart |
| `deploy/kubernetes/identitypulse.yaml` | Checked-in Helm-rendered Kubernetes manifests |
| `.github/workflows/` | Java 21 verification, reliability lab, scanning, CodeQL, manual image release |
| `docs/` | Architecture, SLOs, runbooks, incident/RCA templates, security, deployment and evidence |

## Deploy and operate

Read [deployment](docs/deployment.md), [security boundaries](docs/security.md), [architecture](docs/architecture.md) and [incident runbooks](docs/runbooks.md) first. The chart deploys the application, not managed multi-zone PostgreSQL/Redis/IdP/ingress/telemetry infrastructure. Supply those systems, TLS, credentials and appropriate network policy before deployment. CI validates the chart; it does not certify a production cluster.

CI fails on integration tests, load/failure assertions, Helm/render drift, invalid Prometheus rules, known fixable high/critical dependency vulnerabilities, secret findings or CodeQL analysis errors. CodeQL findings require triage in the repository Security tab; a successful analysis job alone does not mean there are no findings. The manual release workflow reruns Java verification, then publishes a commit-tagged GHCR image with SBOM and provenance. It does not deploy automatically. See [publishing](docs/publishing.md).
