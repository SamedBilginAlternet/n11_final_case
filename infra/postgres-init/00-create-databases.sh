#!/usr/bin/env bash
set -euo pipefail

DATABASES=(authdb productdb cartdb orderdb paymentdb)

for DB in "${DATABASES[@]}"; do
  echo "Creating database $DB"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "postgres" \
    -c "CREATE DATABASE \"$DB\" OWNER \"$POSTGRES_USER\";"
done
