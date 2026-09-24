rootProject.name = "build-logic"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    // Byggelogikken henter versjonene fra samme katalog som modulene, slik at pluginversjoner ikke lever et eget liv her inne.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
