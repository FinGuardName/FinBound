FROM mcr.microsoft.com/playwright:v1.62.1-noble

ENV CI=1
WORKDIR /app

RUN corepack enable

COPY package.json pnpm-lock.yaml ./
RUN --mount=type=cache,id=pnpm-e2e,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile

COPY playwright.config.js ./playwright.config.js
COPY tests ./tests

CMD ["pnpm", "test"]
