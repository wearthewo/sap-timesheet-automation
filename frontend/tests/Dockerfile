FROM node:26.13.0-bookworm-slim@sha256:bc0a2afd3b74ce12222fc8bab99271e66257f021078f5f4a5d687a77d280c1d9
RUN npm install -g pnpm@12.8.1
WORKDIR /work
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile && pnpm exec playwright install --with-deps chromium
COPY playwright.config.ts ./
COPY tests ./tests
CMD ["pnpm", "test:browser"]
