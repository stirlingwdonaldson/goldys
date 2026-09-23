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

# Headless Chromium OS libraries for the OpenTable browser pull (Playwright's
# canonical set). NOTE: the Chromium binary itself is NOT baked in here —
# Playwright.create() downloads it at runtime to ~/.cache/ms-playwright. A
# deployment with no outbound egress must pre-bake it (playwright install
# chromium, with PLAYWRIGHT_BROWSERS_PATH set for the app user) instead.
RUN apt-get update && apt-get install -y --no-install-recommends \
      libnss3 libnspr4 libatk1.0-0 libatk-bridge2.0-0 libcups2 libdrm2 \
      libxkbcommon0 libxcomposite1 libxdamage1 libxfixes3 libxrandr2 libgbm1 \
      libasound2 libpango-1.0-0 libcairo2 fonts-liberation wget ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
