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

# The backend no longer launches a browser itself — it connects to the Fortress
# sidecar over CDP — so the Chromium OS libraries are no longer needed here.
COPY --from=build /workspace/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
