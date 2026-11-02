#!/bin/bash
# Explicit operator action on a NEW production host. Never overwrite or rotate existing credentials.
set -euo pipefail
umask 077
directory=/etc/workforce/secrets
[ "$(id -u)" = 0 ] || exit 1
[ ! -e "$directory" ] || { echo 'Secret directory already exists; refusing to overwrite' >&2; exit 1; }
install -d -m 0700 "$directory"
for name in PG_ADMIN_PASSWORD DATABASE_PASSWORD KEYCLOAK_DB_PASSWORD KEYCLOAK_ADMIN_PASSWORD KEYCLOAK_CLIENT_SECRET NEXTAUTH_SECRET SAP_API_TOKEN INVITATION_SIGNING_KEY; do
 (set -o noclobber; openssl rand -hex 32 > "$directory/$name")
 chmod 0444 "$directory/$name"
done
echo 'Eight independent secrets created without displaying values. Provision SMTP_PASSWORD from the provider separately.'
echo 'Store an encrypted recovery copy outside this VPS before starting the databases.'
