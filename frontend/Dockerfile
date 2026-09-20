# syntax=docker/dockerfile:1
# Bun compiles the app; a minimal Node image runs the standalone server. Bun is a
# build-only dependency — Next.js `output: "standalone"` ships a `server.js` that
# runs under Node, so the runtime image carries no Bun.

# ---- build ----
FROM oven/bun:1.4.2-alpine AS build
WORKDIR /app

# Install dependencies first so this layer caches across source-only changes.
COPY package.json bun.lock ./
RUN bun install --frozen-lockfile

COPY . .

# The /api and /oauth2 rewrites target the backend service. This is baked at
# build time because Next.js resolves rewrites when the config is loaded.
ARG BACKEND_ORIGIN=http://backend:8080
ENV BACKEND_ORIGIN=$BACKEND_ORIGIN
RUN bun run build

# ---- runtime ----
FROM node:22-alpine AS runtime
WORKDIR /app

ENV NODE_ENV=production
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

# Standalone server + static assets. `public/` is empty today (see .gitkeep) but
# the copy is kept so real public assets work without a Dockerfile change.
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public

EXPOSE 3000
CMD ["node", "server.js"]
