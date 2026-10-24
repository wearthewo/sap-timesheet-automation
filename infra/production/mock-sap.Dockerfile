FROM node:24.22.1-bookworm-slim@sha256:55fb5f5a527ea2c5c4a95f963e48ef90aa38c7b346054fdc8ee9957a2b55d205
# The simulator uses Node built-ins and requires no runtime package manager.
RUN rm -rf /usr/local/lib/node_modules/npm && rm -f /usr/local/bin/npm /usr/local/bin/npx
WORKDIR /app
COPY --chown=node:node mock-sap/server.mjs ./
COPY --chown=node:node infra/production/mock-runner.mjs ./
COPY --chmod=0555 infra/production/with-secrets.sh /usr/local/bin/with-secrets
RUN mkdir /data && chown node:node /data
USER node
ENV NODE_ENV=production PORT=8090 SAP_STORE_PATH=/data/timesheets.json
ENTRYPOINT ["/usr/local/bin/with-secrets","node","mock-runner.mjs"]
