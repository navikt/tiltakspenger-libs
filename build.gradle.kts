import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val javaVersion = JavaVersion.VERSION_21
val jvmVersion = JvmTarget.JVM_21

plugins {
    kotlin("jvm") version "2.1.0"
    `maven-publish`
    `java-library`
    id("com.diffplug.spotless") version "6.25.0"
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

    spotless {
        kotlin {
            ktlint("0.48.2")
        }
    }

    tasks {
        compileKotlin {
            compilerOptions {
                jvmTarget.set(jvmVersion)
            }
        }
        compileTestKotlin {
            compilerOptions {
                jvmTarget.set(jvmVersion)
                freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
            }
        }
        test {
            // JUnit 5 support
            useJUnitPlatform()
            // https://phauer.com/2018/best-practices-unit-testing-kotlin/
            systemProperty("junit.jupiter.testinstance.lifecycle.default", "per_class")
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
