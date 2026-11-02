#!/bin/sh
set -eu
umask 077
for name in $SECRET_NAMES; do
  case "$name" in *[!A-Z_]*|'') echo 'Invalid secret name' >&2; exit 1;; esac
  path="/run/secrets/$name"
  [ -s "$path" ] || { echo "Required secret file missing: $name" >&2; exit 1; }
  value=$(cat "$path")
  [ ${#value} -ge 16 ] || { echo "Secret too short: $name" >&2; exit 1; }
  case "$value" in *REPLACE*|*CHANGEME*) echo "Placeholder secret rejected: $name" >&2; exit 1;; esac
  export "$name=$value"
done
case "${SERVICE_ROLE:-}" in
 keycloak)
  export KC_DB_PASSWORD="$KEYCLOAK_DB_PASSWORD" KC_BOOTSTRAP_ADMIN_PASSWORD="$KEYCLOAK_ADMIN_PASSWORD"
  export KEYCLOAK_SMTP_PASSWORD="$SMTP_PASSWORD"
  ;;
 backend) export MAIL_PASSWORD="$SMTP_PASSWORD";;
esac
unset value path name
exec "$@"
