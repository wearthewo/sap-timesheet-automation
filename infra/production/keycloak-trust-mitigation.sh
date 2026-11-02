#!/bin/sh
set -eu
# Use Java's supported keytool on the effective default Java store. The stripped
# Keycloak image lacks libp11-kit, so update-ca-trust cannot run in this image.
# This changes Java trust only; it does not claim to regenerate every OS bundle.
# Exact pinned Keycloak base: G3/G2/AAA absent; G1 remains. Fingerprint mismatch
# is a hard failure requiring a fresh vendor/store review when changing the base.
directory=/etc/pki/ca-trust/source/blocklist
mkdir -p "$directory"
certificate="$directory/workforce-emsign-g1.der"
keytool -exportcert -cacerts -storepass changeit -alias emsignrootca-g1 > "$certificate"
printf '%s  %s\n' '40f6af0346a99aa1cd1d555a4e9cce62c7f9634603ee406615833dc8c8d00367' "$certificate" | sha256sum -c -
keytool -delete -cacerts -storepass changeit -alias emsignrootca-g1
if keytool -list -cacerts -storepass changeit -alias emsignrootca-g1 >/dev/null 2>&1; then
 echo 'Blocked certificate remains trusted' >&2
 exit 1
fi
