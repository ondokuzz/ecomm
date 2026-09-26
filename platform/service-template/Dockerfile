# Build from the repo root, since the service needs the whole Gradle build:
#   docker build -f platform/service-template/Dockerfile -t ecomm/service-template .
ARG MODULE=platform/service-template

FROM eclipse-temurin:21-jdk AS build
ARG MODULE
WORKDIR /workspace
SHELL ["/bin/bash", "-o", "pipefail", "-c"]
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon ":${MODULE//\//:}:bootJar" -x test \
 && java -Djarmode=tools -jar "${MODULE}/build/libs/app.jar" extract --layers --launcher --destination /extracted

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60" \
    SPRING_PROFILES_ACTIVE=docker
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
