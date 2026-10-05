FROM node:24-bookworm-slim
RUN npm install -g pnpm@12.8.1
WORKDIR /work
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile && pnpm exec playwright install --with-deps chromium
COPY playwright.config.ts ./
COPY tests ./tests
CMD ["pnpm", "test:browser"]
