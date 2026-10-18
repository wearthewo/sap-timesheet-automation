FROM caddy:2.11.7-alpine@sha256:d8542f48d34a9cf4e4c11a478865229840e87e4c96ea3f439101f31a5d35f75f
RUN setcap -r /usr/bin/caddy && addgroup -g 1000 app && adduser -D -u 1000 -G app app && chown -R app:app /data /config
COPY infra/production/Caddyfile /etc/caddy/Caddyfile
USER 1000:1000
