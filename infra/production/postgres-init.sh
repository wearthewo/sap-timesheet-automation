#!/bin/sh
set -eu
# psql reads secrets from its process environment, never command arguments or SQL logs.
DATABASE_PASSWORD=$(cat /run/secrets/DATABASE_PASSWORD)
KEYCLOAK_DB_PASSWORD=$(cat /run/secrets/KEYCLOAK_DB_PASSWORD)
export DATABASE_PASSWORD KEYCLOAK_DB_PASSWORD
psql -X -v ON_ERROR_STOP=1 --username postgres --dbname postgres <<'SQL'
\getenv workforce_password DATABASE_PASSWORD
\getenv keycloak_password KEYCLOAK_DB_PASSWORD
SELECT format('CREATE ROLE workforce LOGIN PASSWORD %L', :'workforce_password') \gexec
SELECT format('CREATE ROLE keycloak LOGIN PASSWORD %L', :'keycloak_password') \gexec
CREATE DATABASE workforce OWNER workforce;
CREATE DATABASE keycloak OWNER keycloak;
REVOKE ALL ON DATABASE workforce FROM PUBLIC;
REVOKE ALL ON DATABASE keycloak FROM PUBLIC;
SQL
