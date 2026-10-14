# Build stage: compiles the app with the Gradle wrapper
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Download Gradle and the dependencies in their own layer, so source changes don't re-download them
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null

COPY src src
# The version shown in the app: CI passes the release tag. Builds from a checkout show "dev" (.git isn't copied)
ARG APP_VERSION=dev
# Tests run in CI; the image build only packages the app.
# Spring Boot also builds a "-plain" jar without dependencies, which is skipped here.
RUN ./gradlew --no-daemon bootJar -x test -PappVersion="$APP_VERSION" \
    && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} app.jar \;

# Runtime stage: only the JRE and the app
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /workspace/app.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
