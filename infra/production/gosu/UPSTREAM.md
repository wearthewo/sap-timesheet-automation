# gosu source provenance

Unmodified runtime Go sources from gosu **1.20**, commit
`29b939aa5bd4dc588af765b8248f6e39150d2565`:
https://github.com/tianon/gosu/tree/29b939aa5bd4dc588af765b8248f6e39150d2565

Downloaded commit archive:
`https://api.github.com/repos/tianon/gosu/tarball/29b939aa5bd4dc588af765b8248f6e39150d2565`
SHA256 `d296a638f91b49c75344b212f1a24bed4a5b44acf3da9a7f2a41abfea851d4ee`.
Only main.go, setup-user.go, version.go, go.mod, go.sum and LICENSE are vendored.
Apache-2.0 LICENSE is preserved and copied into both runtime images.

Production PostgreSQL and backup Dockerfiles use the same build stage, pinned
Go1.27.3, `GOTOOLCHAIN=local`, module verification, CGO disabled and readonly
module resolution. The published upstream binary still uses Go1.24.6; this is
a locally maintained rebuild, not an upstream signed gosu binary. No upstream
security exclusions or insecure environment overrides are enabled.

Dependency-only maintenance on 2026-10-27: `golang.org/x/sys` is pinned to
`v0.44.0` to eliminate CVE-2026-39824 / GO-2026-5024 from the embedded module
inventory. Its upstream advisory concerns Windows `NewNTUnicodeString`; no
scanner exception is used for the Linux build. `go.mod` now declares the pinned
Go 1.27.3 toolchain version. `go.sum` was regenerated with the checksum database
enabled and `go mod verify` passed. `github.com/moby/sys/user` remains v0.4.1.
The archive hash above describes the original sources, not these maintained
module files; no runtime Go source or upstream license was modified.

Keep both build stages identical. Future source/compiler updates require a
complete assembled-image scan and the focused PostgreSQL initialization,
privilege-drop, dump/restore and shutdown test. Retain the original PostgreSQL
major version, entrypoint, CA/locale support, backup behavior and data ownership.
The release workflow publishes the patched database image under the Workforce
namespace and records its own digest; never relabel it as an official image.

See [the evidence and remaining findings](../../../docs/phase13-runtime-remediation.md).
