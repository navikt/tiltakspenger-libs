rootProject.name = "tiltakspenger-libs"

include(
    "person-dtos",
    "arenatiltak-dtos",
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
    "auth-ktor",
    "json",
    "ktor-common",
    "logging",
)
