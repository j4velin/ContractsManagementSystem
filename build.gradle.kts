import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

plugins {
    val kotlinVersion = "2.4.20"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
}

group = "de.j4velin"
/**
 * The version is never set by hand, it comes from the git tag of the release:
 * - CI builds the release image with `-PappVersion=<tag>` (see the Dockerfile and the publish job)
 * - otherwise `git describe`, e.g. "1.2.0" on a tagged commit or "1.2.0-3-gabc1234" three commits later
 * - "dev", if neither is available (like in a Docker build from a checkout, which excludes .git)
 */
version = (providers.gradleProperty("appVersion").orNull?.trim()?.takeIf { it.isNotEmpty() }
    ?: runCatching {
        providers.exec {
            commandLine("git", "describe", "--tags", "--always", "--dirty", "--match", "[0-9]*", "--match", "v[0-9]*")
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim()
    }.getOrNull()?.takeIf { it.isNotEmpty() }
    ?: "dev").removePrefix("v")

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// Spring Boot's dependency management would otherwise pin the Kotlin libraries to its own (older) Kotlin version
extra["kotlin.version"] = getKotlinPluginVersion()

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.webjars:webjars-locator-lite")
    implementation("org.webjars:bootstrap:5.3.8")
    implementation("org.webjars.npm:bootstrap-icons:1.13.1")
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

// META-INF/build-info.properties with the version, read by the app (BuildProperties) to show it in the footer. Without
// the build time, so that the file only changes with the version
springBoot {
    buildInfo {
        excludes.set(setOf("time"))
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
