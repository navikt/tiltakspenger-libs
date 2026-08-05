pluginManagement {
    // Konvensjonspluginene bor i et eget inkludert bygg, ikke i buildSrc: buildSrc invaliderer hele bygget ved
    // hver endring, og kan ikke publiseres videre til app-repoene.
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    // Repositories deklareres her, ikke i den enkelte modulen; en modul som legger til sitt eget feiler bygget.
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "tiltakspenger-libs"

include(
    "person-dtos",
    "arenatiltak-dtos",
    "tiltak-dtos",
    "soknad-dtos",
    "periodisering",
    "personklient:personklient-infrastruktur",
    "personklient:personklient-domene",
    "jobber",
    "common",
    "test-common",
    "persistering:persistering-infrastruktur",
    "persistering:persistering-domene",
    "persistering:persistering-suspending",
    "persistering:persistering-test-common",
    "tiltaksdeltakelse:tiltaksdeltakelse-infrastruktur",
    "tiltaksdeltakelse:tiltaksdeltakelse-domene",
    "auth-test-core",
    "httpklient:httpklient-domene",
    "httpklient:httpklient-infrastruktur",
    "json",
    "ktor-common",
    "ktor-test-common",
    "logging",
    "lokal-oppstart",
    "meldekort-dtos",
    "meldekort",
    "kafka",
    "kafka-avro",
    "kafka-test",
    "konsist-regler",
    "texas",
    "satser"
)
