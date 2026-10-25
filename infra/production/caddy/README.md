# Patched Caddy source build

The production proxy uses upstream Caddy 2.11.7 standard modules, compiled with
Go 1.27.3 and x/net 0.60.0. This follows Caddy's supported source-build method;
the resulting binary is maintained by this repository, not an official Caddy
release artifact. Caddy and Go dependencies retain their upstream licenses
(Caddy: Apache-2.0). No third-party Caddy plugin is added.

`go.mod` and `go.sum` are the reviewed complete dependency graph. The build uses
the digest-pinned Go image, `GOTOOLCHAIN=local`, checksum verification and
`-mod=readonly`. It cannot silently upgrade the compiler or resolve a different
module version. Keep the public Go checksum database enabled. `go mod tidy` is a
maintenance operation, never a production build step.

The final image retains the official Caddy root, CA certificates, nonroot UID,
health utility, persistent data paths and standard modules. Brotli is upgraded
to exactly Alpine's signed `1.2.1-r0` package. Package availability is still a
build-time registry dependency: if removed, the build must fail, not silently
fall back. Immutable roots and locked inputs do not promise identical OCI
manifest hashes across different BuildKit metadata/provenance settings.

Build and inspect without publication:

```sh
docker buildx build --platform linux/amd64 --load -f infra/production/caddy.Dockerfile -t workforce-proxy:security-reviewed .
docker image inspect workforce-proxy:security-reviewed --format '{{.Id}}'
node infra/production/runtime-candidate-check.mjs proxy sha256:<inspected-image-id>
```

The component check uses production Compose settings with temporary local TLS,
synthetic HTTP upstreams and tmpfs. It does not test public ACME, real OIDC or
production DNS. Pull the pinned Node root from `images.lock.json` first for its
fixtures. It never opens application databases or existing Docker volumes.

Scan the final assembled image, not only this Go dependency graph. Keep all
findings and the unchanged HIGH/CRITICAL release gate. See
[the remediation evidence](../../../docs/phase13-runtime-remediation.md).
