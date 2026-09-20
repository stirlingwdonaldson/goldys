# syntax=docker/dockerfile:1
# Gradle (on JDK 25) compiles the Spring Boot fat jar; a minimal JRE 25 image runs
# it. Tests are intentionally NOT run here — they need Testcontainers/Postgres and
# CI runs them separately.

# ---- build ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Wrapper + build files first so the Gradle distribution and dependency downloads
# sit in an early, cacheable layer.
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon

COPY src ./src
RUN ./gradlew bootJar --no-daemon

# ---- runtime ----
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
