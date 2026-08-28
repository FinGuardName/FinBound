FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY . .
RUN ./gradlew :backend:gateway:bootJar -x test

FROM eclipse-temurin:21-jre-alpine
COPY --from=builder /workspace/backend/gateway/build/libs/gateway-*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]