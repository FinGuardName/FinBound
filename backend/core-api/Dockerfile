# syntax=docker/dockerfile:1
#
# core-api를 이미지 안에서 통째로 빌드한다. 로컬에 JDK가 없어도, JAVA_HOME이 잘못 잡혀 있어도
# `docker compose up --build` 하나로 뜬다. docs/05-development-guide.md §1.1 참조.

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# 의존성 해석에 필요한 파일만 먼저 복사한다. 소스만 바뀌면 이 레이어는 캐시된다.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY naver-checkstyle.xml naver-checkstyle-suppressions.xml ./
COPY backend/core-api/build.gradle.kts backend/core-api/
COPY backend/gateway/build.gradle.kts backend/gateway/
COPY backend/agent/build.gradle.kts backend/agent/
COPY backend/mock-finance/build.gradle.kts backend/mock-finance/
RUN ./gradlew :backend:core-api:dependencies --no-daemon --quiet

COPY backend/core-api/src backend/core-api/src
# bootJar는 test에 의존하지 않는다. 테스트는 CI와 로컬에서 돌리며, Testcontainers를 쓰므로
# 이미지 빌드 중에는 어차피 돌릴 수 없다(Docker in Docker가 된다).
RUN ./gradlew :backend:core-api:bootJar --no-daemon \
    && cp backend/core-api/build/libs/*.jar /workspace/core-api.jar

FROM eclipse-temurin:21-jre-alpine AS runtime

# root로 돌리지 않는다.
RUN addgroup -S finguard && adduser -S finguard -G finguard
USER finguard

WORKDIR /app
COPY --from=build --chown=finguard:finguard /workspace/core-api.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
