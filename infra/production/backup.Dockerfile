FROM postgres:17-alpine@sha256:b0f9560a2de083e2cc7382e75f808c7381a32852a7ec49117deedb300e552b24
RUN apk add --no-cache age bash
COPY --chmod=0555 infra/production/backup.sh /usr/local/bin/workforce-backup
COPY infra/production/manifest.sql /opt/manifest.sql
ENTRYPOINT ["/usr/local/bin/workforce-backup"]
