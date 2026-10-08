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

# Demo mode defaults OFF in the production image (NEXT_PUBLIC_* is inlined at
# build time). Build with --build-arg NEXT_PUBLIC_DEMO_MODE=true for a demo image.
ARG NEXT_PUBLIC_DEMO_MODE=false
ENV NEXT_PUBLIC_DEMO_MODE=$NEXT_PUBLIC_DEMO_MODE

# Sentry. All optional: with no DSN the SDK stays off, and with no auth token the build skips
# source map upload. The DSN/environment/release are inlined into the browser bundle.
ARG NEXT_PUBLIC_SENTRY_DSN=
ARG NEXT_PUBLIC_SENTRY_ENVIRONMENT=production
# "true" shows Sentry's "Report a problem" button to signed-in users.
ARG NEXT_PUBLIC_SENTRY_FEEDBACK=false
ARG SENTRY_RELEASE=
ARG SENTRY_URL=
ARG SENTRY_ORG=
ARG SENTRY_PROJECT=goldys-frontend
ENV NEXT_PUBLIC_SENTRY_DSN=$NEXT_PUBLIC_SENTRY_DSN \
    NEXT_PUBLIC_SENTRY_ENVIRONMENT=$NEXT_PUBLIC_SENTRY_ENVIRONMENT \
    NEXT_PUBLIC_SENTRY_FEEDBACK=$NEXT_PUBLIC_SENTRY_FEEDBACK \
    NEXT_PUBLIC_SENTRY_RELEASE=$SENTRY_RELEASE \
    SENTRY_RELEASE=$SENTRY_RELEASE \
    SENTRY_URL=$SENTRY_URL \
    SENTRY_ORG=$SENTRY_ORG \
    SENTRY_PROJECT=$SENTRY_PROJECT

# The auth token arrives as a BuildKit secret so it never lands in an image layer or history.
RUN --mount=type=secret,id=sentry_auth_token,env=SENTRY_AUTH_TOKEN bun run build

# ---- runtime ----
FROM node:22-alpine AS runtime
WORKDIR /app

ENV NODE_ENV=production
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

# Standalone server + static assets. `public/` is empty today (see .gitkeep) but
# the copy is kept so real public assets work without a Dockerfile change.
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static
COPY --from=build --chown=node:node /app/public ./public

# Run as the unprivileged `node` user (uid 1000) that ships with node:22-alpine.
USER node

EXPOSE 3000
CMD ["node", "server.js"]
