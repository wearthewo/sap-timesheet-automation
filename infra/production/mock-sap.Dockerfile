FROM gcr.io/distroless/nodejs24-debian13:nonroot@sha256:cb606325b7ec4af55462cb0b424a53b452a5b4a894ffe841fcf1048d216fb9f8
# Retain UID 1000 for existing mock-state volume and backup compatibility.
WORKDIR /app
COPY --chown=1000:1000 mock-sap/server.mjs ./
COPY --chown=1000:1000 infra/production/mock-runner.mjs infra/production/mock-entrypoint.mjs ./
COPY --chown=1000:1000 infra/production/mock-data/ /data/
USER 1000:1000
ENV NODE_ENV=production PORT=8090 SAP_STORE_PATH=/data/timesheets.json
ENTRYPOINT ["/nodejs/bin/node","/app/mock-entrypoint.mjs"]
