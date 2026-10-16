#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
sh scripts/lab-init.sh
docker compose up -d postgres redis toxiproxy issuer otel tempo prometheus grafana
python3 scripts/proxies.py
# Flyway owns the schema; insert the fixture only after readiness.
docker compose up -d --build app
python3 scripts/wait-ready.py
docker compose exec -T postgres psql -U identitypulse -d identitypulse -c "INSERT INTO principals VALUES ('load-user',true) ON CONFLICT(subject) DO UPDATE SET enabled=true;"
