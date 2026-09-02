# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Dockerfile.dockerignore keeps local Gradle settings and credentials out of the context.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY naver-checkstyle.xml naver-checkstyle-suppressions.xml ./
RUN --mount=type=cache,id=finguard-gradle,target=/root/.gradle \
    ./gradlew --version --no-daemon >/dev/null

COPY backend/agent/build.gradle.kts backend/agent/
RUN --mount=type=cache,id=finguard-gradle,target=/root/.gradle \
    ./gradlew :backend:agent:dependencies \
        --configuration runtimeClasspath --no-daemon --quiet >/dev/null

COPY backend/agent/src backend/agent/src
RUN --mount=type=cache,id=finguard-gradle,target=/root/.gradle \
    ./gradlew :backend:agent:bootJar --no-daemon \
    && cp backend/agent/build/libs/*.jar /workspace/agent.jar

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S -g 10001 finguard \
    && adduser -S -D -H -u 10001 -G finguard finguard

WORKDIR /app
COPY --from=build --chown=finguard:finguard /workspace/agent.jar app.jar

USER 10001:10001
EXPOSE 8082

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=12 \
    CMD wget -qO- http://localhost:8082/actuator/health | grep -q '"status":"UP"'

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
