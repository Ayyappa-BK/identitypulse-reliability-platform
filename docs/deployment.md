# Kubernetes deployment and promotion

Supply a production OIDC issuer, HA PostgreSQL/Redis, TLS ingress, secret management, collector and Prometheus/Alertmanager. The chart assumes namespace `identitypulse`, a monitoring namespace `observability` and ingress controller namespace `ingress-nginx`. Adjust NetworkPolicy deliberately for your cluster, including DNS and external managed dependencies. Do not apply placeholder secrets or issuer URLs.

```sh
kubectl create namespace identitypulse
# Provision identitypulse-secrets through your secret manager with DB_URL, DB_USER,
# DB_PASSWORD and REDIS_PASSWORD. Run migrations separately with an owner role.
helm lint deploy/helm/identitypulse --strict
helm template identitypulse deploy/helm/identitypulse --namespace identitypulse > /tmp/identitypulse.yaml
helm upgrade --install identitypulse deploy/helm/identitypulse   --namespace identitypulse --atomic --wait --timeout 5m   --set image.tag=YOUR_PUBLISHED_COMMIT_SHA   --set issuer=https://YOUR_ISSUER   --set jwksUri=https://YOUR_ISSUER/JWKS_PATH   --set redisHost=YOUR_REDIS_HOST
kubectl -n identitypulse rollout status deployment/identitypulse
kubectl -n identitypulse get pods,hpa,pdb
```

Raw manifests are the output of `helm template identitypulse deploy/helm/identitypulse --namespace identitypulse`. They carry placeholders and are an inspectable alternative to Helm, not a second independently maintained spec. CI checks drift. Add a TLS ingress to the app Service port 80; port 9090 is for probes/scraping only. Discover pod metrics through annotations or operator-specific PodMonitor configuration. The local Prometheus config uses Compose targets; do not copy its `app:9090` target into a cluster.

The HPA requires metrics-server. Three minimum replicas, 60% CPU target and five-minute scale-down stabilization are starting values requiring load validation. The PDB requires two available replicas but only protects voluntary disruptions. Soft hostname spread does not guarantee distinct zones. Use zone topology, priority, provisioned capacity and regional failover if your risk model requires them. Pool size 10 × maximum 12 replicas = up to 120 database connections, plus migrations/operations. Reserve DB capacity and tune pool/max replicas together.

Create release environment protection rules in GitHub. CI must pass on the reviewed commit; trigger Release image manually to publish a commit-tagged image. Configure image visibility/pull credentials for GHCR. Prefer digest references in production (chart can be extended for digest values). The example manual release workflow publishes; actual cluster promotion is the explicit Helm command above. Configure rollback criteria (valid-token synthetic failure, burn alert, unexplained denials), then `helm rollback identitypulse PREVIOUS_REVISION --namespace identitypulse --wait`.

Before production: exercise key rotation/JWKS outage, external probe credential rotation, ingress timeouts/rate limits, pool exhaustion, app replicas/zone loss, PostgreSQL and Redis failover, backup restore and collector failure. Verify alerts reach the pager; Alertmanager routing is operator-supplied. No cluster-runtime certification is claimed by a successful render or Compose test.
