import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("maven-publish")
    id("java-library")
    id("com.diffplug.spotless")
}

// Shared build service that limits Spotless/ktlint tasks to 1 concurrent execution,
// preventing the flaky InvocationTargetException caused by parallel ktlint initialization.
abstract class SpotlessLimiter : BuildService<BuildServiceParameters.None>

val spotlessLimiter = gradle.sharedServices.registerIfAbsent("spotlessLimiter", SpotlessLimiter::class.java) {
    maxParallelUsages.set(1)
}

tasks.matching { it.name.startsWith("spotless") }.configureEach {
    usesService(spotlessLimiter)
}

val javaVersion = JavaVersion.VERSION_21
val jvmVersion = JvmTarget.JVM_21

group = "com.github.navikt.tiltakspenger-libs"

repositories {
    mavenCentral()
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.platform.launcher)
}
configurations.matching { it.name.endsWith("Classpath") }.configureEach {
    exclude(group = "junit", module = "junit")
    exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
}
// Add stdlib only to Spotless's classpath, not your module's
configurations.matching { it.name.startsWith("spotless") }.configureEach {
    dependencies {
        add(name, kotlin("stdlib"))
    }
}
spotless {
    kotlin {
        ktlint()
            .editorConfigOverride(
                mapOf(
                    "ktlint_standard_max-line-length" to "off",
                    "ktlint_standard_function-signature" to "disabled",
                    "ktlint_standard_function-expression-body" to "disabled",
                ),
            )
    }
}
tasks {
    kotlin {
        compilerOptions {
            jvmTarget.set(jvmVersion)
            freeCompilerArgs.add("-Xconsistent-data-class-copy-visibility")
        }
    }
    test {
        // JUnit 5 support
        useJUnitPlatform()
        // https://phauer.com/2018/best-practices-unit-testing-kotlin/
        systemProperty("junit.jupiter.testinstance.lifecycle.default", "per_class")
        testLogging {
            // We only want to log failed and skipped tests when running Gradle.
            events("skipped", "failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
    withType<Jar> {
        duplicatesStrategy = DuplicatesStrategy.FAIL
    }
}
java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    withSourcesJar()
    withJavadocJar()
}
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.name
            version = project.findProperty("version")?.toString() ?: "0.0.0"
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/navikt/tiltakspenger-libs")
            credentials {
                username = "x-access-token"
                password = providers.environmentVariable("GITHUB_TOKEN").orNull
            }
        }
    }
}
