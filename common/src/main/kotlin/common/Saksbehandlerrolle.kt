package no.nav.tiltakspenger.libs.common

/**
 * Dette er en rolle en person som bruker tiltakspenger-systemet kan ha.
 * Dette vil sannsynligvis gjelde på tvers av applikasjoner.
 * Det er ikke tenkt på veiledere i denne omgangen.
 */
enum class Saksbehandlerrolle : Rolle {
    SAKSBEHANDLER,
    BESLUTTER,

    FORTROLIG_ADRESSE,
    STRENGT_FORTROLIG_ADRESSE,
    SKJERMING,

    DRIFT,
}
