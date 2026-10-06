FROM node:24-bookworm-slim
WORKDIR /app
COPY --chown=node:node server.mjs ./
RUN mkdir /data && chown node:node /data
USER node
ENV PORT=8090
EXPOSE 8090
CMD ["node","server.mjs"]
