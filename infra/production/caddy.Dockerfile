FROM golang:1.27.3-alpine3.23@sha256:754d5200fc3edae76d3b6598a9a3fac7e74981ee6a5413ab955025f185753ef2 AS caddy-build
WORKDIR /src
ENV GOTOOLCHAIN=local GOMAXPROCS=2 CGO_ENABLED=0
COPY infra/production/caddy/go.mod infra/production/caddy/go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod go mod download && go mod verify
COPY infra/production/caddy/main.go ./
COPY infra/production/caddy/guard/ ./guard/
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build go test -mod=readonly ./guard
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build go build -mod=readonly -trimpath -buildvcs=false -ldflags='-s -w' -o /out/caddy .
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build go list -mod=readonly -deps . > /out/dependencies.txt && ! grep -E '^golang.org/x/crypto/openpgp(/|$)' /out/dependencies.txt

FROM caddy:2.11.7-alpine@sha256:d8542f48d34a9cf4e4c11a478865229840e87e4c96ea3f439101f31a5d35f75f
RUN setcap -r /usr/bin/caddy && apk add --no-cache brotli-libs=1.2.1-r0 curl=8.23.0-r0 libcurl=8.23.0-r0 musl=1.2.5-r24 musl-utils=1.2.5-r24 && addgroup -g 1000 app && adduser -D -u 1000 -G app app && chown -R app:app /data /config
COPY --from=caddy-build /out/caddy /usr/bin/caddy
COPY --from=caddy-build /out/dependencies.txt /usr/share/workforce/caddy-dependencies.txt
COPY infra/production/Caddyfile /etc/caddy/Caddyfile
USER 1000:1000
