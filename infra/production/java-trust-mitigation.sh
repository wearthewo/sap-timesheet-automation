#!/bin/sh
set -eu
# Vendor mitigation: https://ops.java/emudhra (2026-10-24).
# This is for the pinned Temurin base, not Keycloak's OS-managed trust store.
# Fingerprint checks fail closed if the base/aliases change. G2 and AAA are
# absent in this base; the final image must also pass the vendor CheckCerts probe.
remove_root() {
  alias=$1
  fingerprint=$2
  temporary=$(mktemp)
  keytool -exportcert -cacerts -storepass changeit -alias "$alias" > "$temporary"
  printf '%s  %s\n' "$fingerprint" "$temporary" | sha256sum -c -
  rm -f "$temporary"
  keytool -delete -cacerts -storepass changeit -alias "$alias"
  if keytool -list -cacerts -storepass changeit -alias "$alias" >/dev/null 2>&1; then
    echo 'Certificate removal failed' >&2
    exit 1
  fi
}
remove_root 'cn_emsign_ecc_root_ca__g3,o_emudhra_technologies_limited,ou_emsign_pki,c_in [jdk]' '86a1ecba089c4a8d3bbe2734c612ba341d813e043cf9e8a862cd5c57a36bbe6b'
remove_root 'cn_emsign_root_ca__g1,o_emudhra_technologies_limited,ou_emsign_pki,c_in [jdk]' '40f6af0346a99aa1cd1d555a4e9cce62c7f9634603ee406615833dc8c8d00367'
