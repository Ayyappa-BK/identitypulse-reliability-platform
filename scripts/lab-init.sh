#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
umask 077
mkdir -p .local/public
if [ ! -f .env ]; then
 printf 'LAB_DB_PASSWORD=%s\nLAB_GRAFANA_PASSWORD=%s\n' "$(openssl rand -hex 24)" "$(openssl rand -hex 24)" > .env
fi
if [ ! -f .local/signing.pem ]; then openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out .local/signing.pem 2>/dev/null; fi
python3 scripts/token.py --jwks > .local/public/jwks.json
printf 'Local secrets generated in .env and .local; never commit these.\n'
