# Build context must be storage-service/ root:
#   docker build -f rustfs-server/Dockerfile .

FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .
COPY rustfs-client rustfs-client
COPY rustfs-server rustfs-server
RUN chmod +x gradlew && ./gradlew :rustfs-server:quarkusBuild --no-daemon

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/rustfs-server/build/quarkus-app /app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
