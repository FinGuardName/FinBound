# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY naver-checkstyle.xml naver-checkstyle-suppressions.xml ./
COPY backend/mock-finance/build.gradle.kts backend/mock-finance/
RUN ./gradlew :backend:mock-finance:dependencies --no-daemon --quiet

COPY backend/mock-finance/src backend/mock-finance/src
RUN ./gradlew :backend:mock-finance:bootJar --no-daemon \
    && cp backend/mock-finance/build/libs/*.jar /workspace/mock-finance.jar

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S finguard && adduser -S finguard -G finguard
USER finguard

WORKDIR /app
COPY --from=build --chown=finguard:finguard /workspace/mock-finance.jar app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
