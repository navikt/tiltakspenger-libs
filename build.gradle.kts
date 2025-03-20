import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val javaVersion = JavaVersion.VERSION_21
val jvmVersion = JvmTarget.JVM_21

plugins {
    kotlin("jvm") version "2.1.20"
    `maven-publish`
    `java-library`
    id("com.diffplug.spotless") version "7.0.2"
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    if (project.name in listOf("persistering", "personklient")) {
        return@subprojects
    }
    group = "com.github.navikt.tiltakspenger-libs"

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")
    apply(plugin = "java-library")
    apply(plugin = "com.diffplug.spotless")

    dependencies {
        testImplementation(platform("org.junit:junit-bom:5.12.1"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testImplementation("org.junit.jupiter:junit-jupiter-params")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
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
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }

    kotlin.sourceSets["main"].kotlin.srcDirs("main")
    kotlin.sourceSets["test"].kotlin.srcDirs("test")
    sourceSets["main"].resources.srcDirs("main")
    sourceSets["test"].resources.srcDirs("test")
}

configurations.all {
    // exclude JUnit 4
    exclude(group = "junit", module = "junit")
}

tasks {
    register<Copy>("addPreCommitGitHookOnBuild") {
        if (System.getenv()["GITHUB_ACTIONS"] == "true") {
            println("Skipping!")
        } else {
            println("⚈ ⚈ ⚈ Running Add Pre Commit Git Hook Script on Build ⚈ ⚈ ⚈")
            from(file(".scripts/pre-commit"))
            into(file(".git/hooks"))
            println("✅ Added Pre Commit Git Hook Script.")
        }
    }
}
