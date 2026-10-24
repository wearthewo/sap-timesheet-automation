FROM node:24.22.1-bookworm-slim@sha256:55fb5f5a527ea2c5c4a95f963e48ef90aa38c7b346054fdc8ee9957a2b55d205
WORKDIR /app
COPY --chown=node:node server.mjs ./
RUN mkdir /data && chown node:node /data
USER node
ENV PORT=8090
EXPOSE 8090
CMD ["node","server.mjs"]
