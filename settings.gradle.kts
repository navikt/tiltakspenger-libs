rootProject.name = "tiltakspenger-libs"

include(
    "person-dtos",
    "arenaytelser-dtos",
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
    "auth-core",
    "auth-test-core",
    "json",
    "ktor-common",
    "ktor-test-common",
    "logging",
    "meldekort-dtos",
    "meldekort",
    "kafka",
    "kafka-test",
    "texas",
    "satser"
)
