FROM node:26.13.0-bookworm-slim@sha256:bc0a2afd3b74ce12222fc8bab99271e66257f021078f5f4a5d687a77d280c1d9
WORKDIR /app
COPY --chown=node:node server.mjs ./
RUN mkdir /data && chown node:node /data
USER node
ENV PORT=8090
EXPOSE 8090
CMD ["node","server.mjs"]
