FROM node:24.18.0-bookworm-slim@sha256:6f7b03f7c2c8e2e784dcf9295400527b9b1270fd37b7e9a7285cf83b6951452d
WORKDIR /app
COPY --chown=node:node mock-sap/server.mjs ./
COPY --chown=node:node infra/production/mock-runner.mjs ./
COPY --chmod=0555 infra/production/with-secrets.sh /usr/local/bin/with-secrets
RUN mkdir /data && chown node:node /data
USER node
ENV NODE_ENV=production PORT=8090 SAP_STORE_PATH=/data/timesheets.json
ENTRYPOINT ["/usr/local/bin/with-secrets","node","mock-runner.mjs"]
