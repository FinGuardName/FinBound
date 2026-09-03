# syntax=docker/dockerfile:1.7

FROM node:24-alpine AS build

ARG VITE_FINBOUND_API_MODE=real
ARG VITE_FINBOUND_API_BASE_URL=/core-api

WORKDIR /app
RUN corepack enable

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN --mount=type=cache,id=pnpm,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile

COPY index.html vite.config.js ./
COPY src ./src

RUN VITE_FINBOUND_API_MODE="$VITE_FINBOUND_API_MODE" \
    VITE_FINBOUND_API_BASE_URL="$VITE_FINBOUND_API_BASE_URL" \
    pnpm build


FROM nginxinc/nginx-unprivileged:1.29-alpine AS runtime

ENV CORE_API_UPSTREAM=http://core-api:8080

COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=6 \
    CMD ["wget", "-qO-", "http://127.0.0.1:8080/health"]
