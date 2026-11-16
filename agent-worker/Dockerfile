# syntax=docker/dockerfile:1
# Registry index verified 16 November 2026. Dependency scripts stay disabled in every stage.
FROM oven/bun:1.4.2@sha256:9114c058aeae42162ee16dd5084b95fe9473970bb6bcb5b232ab1630f0546895 AS build
WORKDIR /worker
COPY package.json bun.lock ./
RUN bun install --frozen-lockfile --ignore-scripts
COPY tsconfig.json ./
COPY src ./src
COPY test ./test
RUN bun run typecheck && bun test && bun run build

FROM oven/bun:1.4.2@sha256:9114c058aeae42162ee16dd5084b95fe9473970bb6bcb5b232ab1630f0546895 AS dependencies
WORKDIR /worker
COPY package.json bun.lock ./
RUN bun install --frozen-lockfile --production --ignore-scripts

FROM oven/bun:1.4.2-distroless@sha256:1a0c31c7c5f9d193aedf60fe1cebdeb76ac8f6e29f24be8dd8cbd6df72df26ec AS runtime
WORKDIR /worker
COPY --from=dependencies /worker/node_modules ./node_modules
COPY --from=build /worker/dist ./dist
USER 65532:65532
# A supervisor supplies only the documented configuration; Bun must not load ambient dotenv files.
ENTRYPOINT ["bun", "--no-env-file", "/worker/dist/main.js"]

# Exercise the SDK on the exact runtime base and production dependency tree as well.
FROM runtime AS runtime-check
COPY --from=build /worker/src ./src
COPY --from=build /worker/test ./test
RUN ["bun", "--no-env-file", "test"]

# Force the runtime contract checks into the build graph without shipping their sources.
FROM runtime AS final
COPY --from=runtime-check /worker/dist ./dist
