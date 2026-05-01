#!/usr/bin/env bash
# Pulls the production secret bundle from Infisical and writes it to
# /opt/n11/.env atomically.  Called by the GitHub Actions deploy workflow
# before `docker compose up`, and can be run manually on the droplet to
# refresh secrets out-of-band.
#
# Auth lives in /opt/n11/.infisical-auth (chmod 600) with three vars:
#   INFISICAL_CLIENT_ID, INFISICAL_CLIENT_SECRET, INFISICAL_PROJECT_ID
# Rotate the client secret in the Infisical UI and update that file when
# it leaks or on a routine cadence — the rest of the flow stays the same.

set -euo pipefail

source /opt/n11/.infisical-auth

TOKEN=$(infisical login --method=universal-auth \
  --client-id="$INFISICAL_CLIENT_ID" \
  --client-secret="$INFISICAL_CLIENT_SECRET" \
  --plain --silent | tr -d '[:space:]')

infisical export \
  --token="$TOKEN" \
  --projectId="$INFISICAL_PROJECT_ID" \
  --env=prod \
  --format=dotenv > /opt/n11/.env.new

# Guard against a successful API call that returns an empty bundle (e.g.
# wrong env slug, identity demoted) — overwriting .env with nothing would
# silently break every service on the next `compose up`.
if [ "$(wc -l < /opt/n11/.env.new)" -lt 5 ]; then
  echo "Infisical export looks empty, aborting." >&2
  rm -f /opt/n11/.env.new
  exit 1
fi

mv /opt/n11/.env.new /opt/n11/.env
chmod 600 /opt/n11/.env
echo "Synced $(grep -cE '^[A-Z_]' /opt/n11/.env) secrets from Infisical."
