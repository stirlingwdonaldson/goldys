# syntax=docker/dockerfile:1
# Gradle (on JDK 25) compiles the Spring Boot fat jar; a minimal JRE 25 image runs
# it. Tests are intentionally NOT run here — they need Testcontainers/Postgres and
# CI runs them separately.

# ---- build ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Wrapper + build files first so the Gradle distribution and dependency downloads
# sit in an early, cacheable layer. gradle.properties carries the project JVM flags.
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY build.gradle settings.gradle gradle.properties ./
RUN ./gradlew dependencies --no-daemon

COPY src ./src
RUN ./gradlew bootJar --no-daemon

# ---- runtime ----
FROM eclipse-temurin:25-jre AS runtime
RUN useradd --system --uid 1001 app
WORKDIR /app

# Headless Chromium OS libraries for the OpenTable browser pull — Playwright's
# dependency list for Ubuntu 24.04+/26.04 (the "t64" package names; older
# `libasound2`-style names no longer exist here). NOTE: the Chromium binary
# itself is NOT baked in — Playwright.create() downloads it at runtime to
# ~/.cache/ms-playwright. A deployment with no outbound egress must pre-bake it
# (playwright install chromium, with PLAYWRIGHT_BROWSERS_PATH set for app) instead.
RUN apt-get update && apt-get install -y --no-install-recommends \
      libasound2t64 libatk-bridge2.0-0t64 libatk1.0-0t64 libatspi2.0-0t64 \
      libcairo2 libcups2t64 libdbus-1-3 libdrm2 libgbm1 libglib2.0-0t64 \
      libnspr4 libnss3 libpango-1.0-0 libx11-6 libxcb1 libxcomposite1 \
      libxdamage1 libxext6 libxfixes3 libxkbcommon0 libxrandr2 \
      fonts-liberation ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
