import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.0"
    kotlin("plugin.serialization") version "1.8.0" apply false
    `maven-publish`
    `java-library`
    id("com.diffplug.spotless") version "6.13.0"
}

allprojects {
    repositories {
        mavenCentral()
    }
}

spotless {
    kotlin {
        ktlint("0.45.2")
    }
}

subprojects {
    group = "com.github.navikt.tiltakspenger-libs"

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")
    apply(plugin = "java-library")
    apply(plugin = "com.diffplug.spotless")

    spotless {
        kotlin {
            ktlint("0.45.2")
        }
    }

    tasks {
        withType<KotlinCompile> {
            kotlinOptions.jvmTarget = "17"
        }
        withType<Jar> {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
        withType<Test> {
            useJUnitPlatform()
        }
    }

    java {
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
