#!/bin/sh
# One database and one owner per service (least privilege: a service cannot read another service's data).
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v auth_password="${AUTH_DB_PASSWORD:-auth_pass}" \
  -v ledger_password="${LEDGER_DB_PASSWORD:-ledger_pass}" \
  -v banking_password="${BANKING_DB_PASSWORD:-banking_pass}" <<'SQL'
CREATE USER auth_user WITH PASSWORD :'auth_password';
CREATE USER ledger_user WITH PASSWORD :'ledger_password';
CREATE USER banking_user WITH PASSWORD :'banking_password';

CREATE DATABASE auth_db OWNER auth_user;
CREATE DATABASE ledger_db OWNER ledger_user;
CREATE DATABASE banking_db OWNER banking_user;

REVOKE ALL ON DATABASE auth_db FROM PUBLIC;
REVOKE ALL ON DATABASE ledger_db FROM PUBLIC;
REVOKE ALL ON DATABASE banking_db FROM PUBLIC;
SQL
