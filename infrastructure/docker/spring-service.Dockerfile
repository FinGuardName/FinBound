# syntax=docker/dockerfile:1
# Compose-only Core/Gateway builder. Module owners retain their standalone images.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
ARG SERVICE_MODULE
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY naver-checkstyle.xml naver-checkstyle-suppressions.xml ./
COPY backend backend
RUN case "$SERVICE_MODULE" in core-api|gateway) ;; *) exit 64 ;; esac \
    && chmod +x gradlew
RUN --mount=type=cache,id=finguard-gradle,target=/root/.gradle,sharing=locked \
    ./gradlew ":backend:${SERVICE_MODULE}:bootJar" --no-daemon \
    && cp backend/${SERVICE_MODULE}/build/libs/*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S -g 10001 finguard \
    && adduser -S -D -H -u 10001 -G finguard finguard
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/app.jar app.jar
USER 10001:10001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
