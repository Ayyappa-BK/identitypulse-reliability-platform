# Identity on-call runbooks

Severity: SEV1 for broad authentication outage or any suspected false acceptance. SEV2 for a regional/partial outage or sustained budget burn. Appoint incident commander, operations lead, security lead and scribe. Record UTC timestamps and evidence links. Never paste bearer tokens, private keys or raw user identifiers into incident artifacts.

## Fast/slow error budget burn, 503 spike

1. Acknowledge and open an incident from the template. Check external valid-token probe, request rate, error ratio, latency and target health. Separate increased legitimate 401/403 from server errors.
2. Inspect deployment changes and `kubectl -n identitypulse get pods,events`; inspect readiness and dependency dashboards. Query `kubectl -n identitypulse logs deployment/identitypulse --since=10m` using trace IDs only. Check database pool usage, statement latency, Redis command latency and IdP key refresh.
3. If correlated with a release, roll back the application revision. If dependencies are unhealthy, fail over/restore using the database or Redis owner. Preserve revocation state. Pause discretionary changes.
4. Verify valid-token synthetic succeeds and revoked/wrong-audience/under-scoped controls still fail. Observe recovery for at least ten minutes and confirm burn rate trends down; a closed circuit alone is insufficient.

## Database unavailable / slow

Check connections, SQL timeouts, locks, replica leader and network path. Account decisions require current state; never bypass checks. Reduce incoming work or scale only within database connection capacity. Escalate to database on-call for failover. After recovery inspect readiness, p99, valid and denied decisions. Lab reproduction: `TOKEN=... python3 scripts/failure-test.py database` or `latency`.

## Redis unavailable

Check connection/TLS/ACL changes, replication/persistence and networking. Restoring an empty cache can re-enable revoked tokens; treat lost revocations as a security incident. Prefer fail-closed operation until revocation correctness is restored. Lab reproduction: scenario `redis`.

## Crash loop / unhealthy replicas

Distinguish startup migration failure from runtime OOM, process crash and external readiness failure. Liveness must not depend on databases. Check resource limits, termination reason, schema compatibility and last rollout. Roll back a bad image; do not repeatedly restart every pod during dependency failure. Lab reproduction: scenario `crash`; Docker restarts the process. Kubernetes replica replacement remains a separate cluster exercise.

## JWT rejection spike / signing key rotation

Check issuer, audience, token expiration, host clocks, key IDs, JWKS reachability and planned key rotation. Use counts/trace IDs rather than copying tokens. Known-key JWTs may validate with cached JWKS; new unknown keys require a reachable issuer. Never disable signature, issuer, audience or expiry checks to recover service. Coordinate overlapping key rotation with identity-provider owner. Validate wrong signature and wrong audience still reject after mitigation.

## Missing telemetry / no traffic

Inspect scrape targets, collector health, ingress routing and probe freshness. Missing telemetry is unknown availability. Check load balancer and external probe before assuming the service is idle. Dashboard no-data is never 100% availability. If Prometheus itself is down, use independent out-of-band paging and client probes.

## Recovery and closure

Record failed and successful probes, fault start/end, time to detect, mitigation and recovery, peak impact and affected failure domains. Preserve CI/load JSON, traces and configuration hashes. Restore every injected toxic and stopped process. Update the incident report and RCA within two business days; assign owners and dates to corrective actions. Security lead approves closure of false-acceptance/revocation incidents.
