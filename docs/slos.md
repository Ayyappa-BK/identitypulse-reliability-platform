# SLIs, objective and error budget

Window: rolling 30 days. Proposed request availability SLO: **99.99%**. Owners: identity on-call and identity security. This is a target, not an observed availability claim.

| SLI | Definition | Interpretation |
|---|---|---|
| Authentication success rate | successful decisions / all verify requests | `outcome="success" / all`; changes with invalid traffic and policy denials; not the availability SLO |
| Request availability | (all decisions - service errors) / all decisions | 401/403 are correctly completed security decisions; 5xx are bad events; timeouts/connection failures outside the app require the external probe |
| Latency | fraction of verify requests <=300ms; also p50/p95/p99 | histogram bucket `le="0.3"`; candidate latency objective 99% <=300ms; evaluate accepted and denied traffic separately |
| Authentication error rate | service errors / all verify requests | `outcome="error"`; 401 invalid credentials and 403 policy denials are separately charted |
| External valid-credential availability | successful scheduled valid-token probes / scheduled probes | includes DNS, TLS, ingress, issuer/key churn and dependency outages; count missed scheduled probes as unknown/bad, never silently drop |
| Scrape availability | successful target scrapes / scheduled scrapes | `up`; diagnoses telemetry/process health, not a replacement for client availability |

`identity_auth_requests_total{outcome=...}` and `identity_auth_duration_seconds_bucket` are bounded-cardinality metrics. The app initializes all outcome counters so a no-error period produces a zero numerator. No traffic yields an undefined ratio; the dashboard should show no data, not fabricate 100%. Alerts cover missing targets and traffic independently. 404/405 and unusual responses belong to `other` and should be investigated rather than interpreted as valid authentication. Count standard verify POST traffic for the SLO.

The request ratio cannot see requests that never reach the process. Use `scripts/synthetic.py` from an independent host with a dedicated short-lived scoped token refreshed by a secret manager. It records each scheduled observation, including network errors; the metric server exposes the time SLI. Place multiple independent probes outside the cluster, validate that the probe credential itself remains valid, and alert on stalled probes. Local CI's finite load sample is not a production synthetic fleet. The public lab issuer is never a production credential source.

## Exact budget

Error fraction = 1 - 0.9999 = 0.0001.

* 30 days: 2,592,000 seconds × 0.0001 = **259.2 seconds = 4 minutes 19.2 seconds**.
* 7 days: **60.48 seconds**.
* 365 days: **3,153.6 seconds = 52 minutes 33.6 seconds**.
* Request budget = total eligible requests × 0.0001. For 1,000,000 requests, **100 bad events**.

Time and request budgets are different measures. The 259.2-second figure applies to the time SLI; the request SLO uses request counts. Report both rather than converting between them under uneven traffic. Security denials consume no service-error budget. A false acceptance is a security incident even when the HTTP availability SLI looks healthy.

Fast burn: >14.4× allowed error fraction in both 5m and 1h windows, sustained 2m. Slow burn: >6× in both 30m and 6h windows, sustained 15m. These are operational choices for a 30-day window, not proof of reliability. Add an absolute error-count or traffic floor appropriate to your volume if low-traffic noise causes paging. Exhausted budget freezes discretionary releases; security fixes continue with incident-owner review.

A single short load run cannot establish four-nines. Even zero failures in n independent trials yields a rough 95% upper error bound of 3/n; >=30,000 independent trials would be needed just to put that bound near 0.0001, and real failures are correlated. Use full-window scheduled evidence, coverage of dependencies and failure domains, and explicit missing-data accounting.
