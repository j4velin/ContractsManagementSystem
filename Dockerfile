# Build stage: compiles the app with the Gradle wrapper
FROM eclipse-temurin:11-jdk AS build
WORKDIR /workspace

# Download Gradle and the dependencies in their own layer, so source changes don't re-download them
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null

COPY src src
# Tests run in CI; the image build only packages the app.
# Spring Boot also builds a "-plain" jar without dependencies, which is skipped here.
RUN ./gradlew --no-daemon bootJar -x test \
    && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} app.jar \;

# Runtime stage: only the JRE and the app
FROM eclipse-temurin:11-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /workspace/app.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
