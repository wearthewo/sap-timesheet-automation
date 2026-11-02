FROM node:24.22.1-bookworm-slim@sha256:55fb5f5a527ea2c5c4a95f963e48ef90aa38c7b346054fdc8ee9957a2b55d205
RUN npm install -g pnpm@12.8.1
WORKDIR /work
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile && pnpm exec playwright install --with-deps chromium
COPY playwright.config.ts ./
COPY tests ./tests
CMD ["pnpm", "test:browser"]
