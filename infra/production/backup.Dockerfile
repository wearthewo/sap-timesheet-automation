FROM golang:1.27.3-alpine3.23@sha256:754d5200fc3edae76d3b6598a9a3fac7e74981ee6a5413ab955025f185753ef2 AS gosu-build
WORKDIR /src
ENV GOTOOLCHAIN=local GOMAXPROCS=2 CGO_ENABLED=0
COPY infra/production/gosu/ ./
RUN --mount=type=cache,target=/go/pkg/mod go mod download && go mod verify
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build go build -mod=readonly -trimpath -buildvcs=false -ldflags='-s -w' -o /out/gosu .

FROM postgres:17-alpine@sha256:b0f9560a2de083e2cc7382e75f808c7381a32852a7ec49117deedb300e552b24
COPY --from=gosu-build /out/gosu /usr/local/bin/gosu
COPY infra/production/gosu/LICENSE /usr/local/share/licenses/gosu/LICENSE
RUN apk add --no-cache age bash musl=1.2.6-r3 musl-utils=1.2.6-r3
COPY --chmod=0555 infra/production/backup.sh /usr/local/bin/workforce-backup
COPY infra/production/manifest.sql /opt/manifest.sql
ENTRYPOINT ["/usr/local/bin/workforce-backup"]
